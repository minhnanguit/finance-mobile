@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.data

import co.touchlab.kermit.Logger
import com.uit.finance.core.capture.data.settings.CaptureSettingsRepositoryImpl
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.BankMessage
import com.uit.finance.core.capture.domain.model.CaptureIds
import com.uit.finance.core.capture.domain.model.CaptureSettings
import com.uit.finance.core.capture.domain.model.CaptureStatus
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.model.IgnoreReason
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.model.ParseResult
import com.uit.finance.core.capture.domain.parser.BankMessageParser
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.text.foldForSearch
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.occurrence.LedgerOccurrenceWriter
import com.uit.finance.core.ledger.data.occurrence.OccurrenceDraft
import com.uit.finance.core.ledger.data.occurrence.OccurrenceWrite
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Luồng xử lý một tin (plan mục 3.5), dùng chung cho listener Android và nháp từ liên kết ví:
 *
 * chờ sổ mở (≤ 10 s, L7) → kiểm user đã bật + đã chọn ngân hàng (L8) → dọn hạn (S4) → đọc → tính id → đã xử lý
 * thì dừng → tìm ví + danh mục → **ghi nháp vào ledger và dòng nhật ký trong cùng một transaction** (writer chạy
 * trong transaction của người gọi) → hẹn sync → cập nhật thông báo "Có N giao dịch cần xác nhận".
 *
 * Log chỉ có ngân hàng + kết quả (S1), không số tiền, không nội dung.
 */
internal class CaptureIngestor(
    private val databases: UserDatabaseProvider,
    private val session: UserSession,
    private val settings: CaptureSettingsRepositoryImpl,
    private val writer: LedgerOccurrenceWriter,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val syncScheduler: SyncScheduler,
    private val notifier: CaptureInboxNotifier,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) {

    suspend fun ingestNotification(packageName: String, title: String?, text: String, postedAt: Instant): IngestOutcome {
        // Khi app đang tắt, hệ thống dựng process để giao thông báo trước khi sổ kịp mở: chờ một chút (L7).
        // Một hạn chờ chung cho cả phiên lẫn sổ. Không bỏ ngay khi SignedOut: lúc process vừa dựng, phiên có thể
        // đang SignedOut tạm thời trước khi khôi phục token.
        val (userId, database) = withTimeoutOrNull(SESSION_WAIT) {
            val ready = session.state.filterIsInstance<LocalDataState.Ready>().first().userId
            ready to databases.database.filterNotNull().first()
        } ?: return log(null, IngestOutcome.NO_SESSION)
        val current = settings.read(userId)
        val bank = resolveBank(packageName, title, current)
        if (!current.enabled || bank == null) return log(bank, IngestOutcome.DISABLED)

        val outcome = withContext(dispatchers.io) {
            purge(database)
            process(database, userId, current, BankMessage(bank, title, text, postedAt))
        }
        if (outcome in NOTIFY_ON) notifier.refresh()
        return log(bank, outcome)
    }

    private fun resolveBank(packageName: String, title: String?, current: CaptureSettings): BankId? {
        current.banks.firstOrNull { packageName in it.packages }?.let { return it }
        if (packageName !in BankId.SMS_APP_PACKAGES) return null
        val sender = title?.foldForSearch()?.uppercase() ?: return null
        return current.banks.firstOrNull { bank -> bank.smsSenders.any { sender == it || sender.startsWith("$it ") } }
    }

    private suspend fun process(database: FinanceDatabase, userId: String, current: CaptureSettings, message: BankMessage): IngestOutcome {
        val zone = TimeZone.currentSystemDefault()
        return when (val result = BankMessageParser.parse(message)) {
            is ParseResult.Ignored -> if (result.reason == IgnoreReason.OTP) IngestOutcome.OTP else IngestOutcome.IGNORED
            ParseResult.Unparsed -> {
                val id = CaptureIds.unparsedId(userId, message.bank, message.text, message.receivedAt, zone)
                if (database.captureQueries.selectEvent(id).executeAsOneOrNull() != null) return IngestOutcome.ALREADY_EXISTS
                database.captureQueries.upsertEvent(
                    id = id, bank = message.bank?.name, status = CaptureStatus.UNPARSED.name, direction = null,
                    amount_minor = null, account_hint = null, payee = null, note = null, occurred_on = null,
                    confidence = null, raw_text = message.text, received_at = message.receivedAt.toEpochMilliseconds(),
                )
                IngestOutcome.UNPARSED
            }
            is ParseResult.Parsed -> {
                val event = result.event
                val occurredOn = event.postedAt?.date ?: message.receivedAt.toLocalDateTime(zone).date
                val minute = event.postedAt?.let { "${it.date}T${it.hour.pad()}:${it.minute.pad()}" }
                    ?: CaptureIds.minuteOf(message.receivedAt, zone)
                val id = CaptureIds.transactionId(userId, CaptureIds.fingerprint(event, occurredOn, minute))
                if (database.captureQueries.selectEvent(id).executeAsOneOrNull() != null) return IngestOutcome.ALREADY_EXISTS
                val pending = PendingDraft(id, message.bank, event.direction, event.amountMinor, event.accountHint, event.payee,
                    event.description, occurredOn, event.confidence.name, message.receivedAt,
                    occurredAt = event.postedAt?.toInstant(zone) ?: message.receivedAt)
                write(database, current, pending, rawText = message.text)
            }
        }
    }

    /** Dữ liệu đủ để ghi nháp: từ tin vừa đọc, hoặc từ dòng "Cần chọn ví" khi user vừa gắn ví. */
    data class PendingDraft(
        val id: String,
        val bank: BankId?,
        val direction: Direction,
        val amountMinor: Long,
        val accountHint: String?,
        val payee: String?,
        val note: String?,
        val occurredOn: LocalDate,
        val confidence: String?,
        val receivedAt: Instant,
        /** Giờ phát sinh (G5): giờ ghi trong tin, không có thì giờ thông báo đến. */
        val occurredAt: Instant? = receivedAt,
    ) {
        override fun toString(): String = "PendingDraft(id=$id, bank=$bank)"
    }

    /** Bước 6–7: tìm ví + danh mục, ghi nháp + nhật ký trong một transaction. */
    suspend fun write(database: FinanceDatabase, current: CaptureSettings, pending: PendingDraft, rawText: String?): IngestOutcome {
        val accountId = pending.bank?.let { resolveAccount(database, it, pending.accountHint) }
        val categoryId = resolveCategory(current, pending.direction)
        if (accountId == null || categoryId == null) {
            saveEvent(database, pending, CaptureStatus.NEEDS_ACCOUNT, rawText)
            return IngestOutcome.NEEDS_ACCOUNT
        }
        val draft = OccurrenceDraft(
            id = pending.id,
            type = if (pending.direction == Direction.IN) TransactionType.INCOME else TransactionType.EXPENSE,
            status = TransactionStatus.DRAFT,
            accountId = accountId,
            categoryId = categoryId,
            amountMinor = pending.amountMinor,
            currency = VND,
            occurredOn = pending.occurredOn,
            payee = pending.payee,
            note = pending.note,
            occurredAt = pending.occurredAt,
        )
        val outcome = database.transactionWithResult {
            when (writer.recordIfAbsent(database, draft)) {
                OccurrenceWrite.WRITTEN -> IngestOutcome.DRAFTED.also { saveEvent(database, pending, CaptureStatus.DRAFTED, null) }
                OccurrenceWrite.ALREADY_EXISTS -> IngestOutcome.ALREADY_EXISTS.also { saveEvent(database, pending, CaptureStatus.DRAFTED, null) }
                // Ví/danh mục vừa bị lưu trữ hay khác tiền tệ giữa lúc kiểm và lúc ghi: để user chọn lại (G3, G4).
                OccurrenceWrite.BLOCKED -> IngestOutcome.NEEDS_ACCOUNT.also { saveEvent(database, pending, CaptureStatus.NEEDS_ACCOUNT, rawText) }
            }
        }
        if (outcome == IngestOutcome.DRAFTED) syncScheduler.requestImmediate()
        return outcome
    }

    /** Ví đã gắn, còn dùng được: chưa xoá, chưa lưu trữ, tiền VND (G3). */
    private suspend fun resolveAccount(database: FinanceDatabase, bank: BankId, hint: String?): String? {
        val linked = database.captureQueries.selectLink(bank.name, hint.orEmpty()).executeAsOneOrNull() ?: return null
        val account = accounts.observeAccounts().first().firstOrNull { it.id == linked } ?: return null
        return linked.takeIf { !account.archived && account.currency == VND }
    }

    /** Danh mục mặc định theo chiều; đã lưu trữ/xoá thì lấy danh mục gốc đầu tiên cùng loại (G4). */
    suspend fun resolveCategory(current: CaptureSettings, direction: Direction): String? {
        val kind = if (direction == Direction.IN) CategoryKind.INCOME else CategoryKind.EXPENSE
        val preferred = if (direction == Direction.IN) current.defaultIncomeCategoryId else current.defaultExpenseCategoryId
        val usable = categories.observeCategories().first().filter { it.kind == kind && !it.archived }
        return usable.firstOrNull { it.id == preferred }?.id ?: usable.firstOrNull { it.parentId == null }?.id
    }

    private fun saveEvent(database: FinanceDatabase, pending: PendingDraft, status: CaptureStatus, rawText: String?) {
        database.captureQueries.upsertEvent(
            id = pending.id,
            bank = pending.bank?.name,
            status = status.name,
            direction = pending.direction.name,
            amount_minor = pending.amountMinor,
            account_hint = pending.accountHint,
            payee = pending.payee,
            note = pending.note,
            occurred_on = pending.occurredOn.toString(),
            confidence = pending.confidence,
            raw_text = rawText.takeIf { status == CaptureStatus.NEEDS_ACCOUNT || status == CaptureStatus.UNPARSED },
            received_at = pending.receivedAt.toEpochMilliseconds(),
        )
    }

    /** S2 + S4: chạy đầu mỗi lần ingest và khi mở màn Chờ xác nhận. */
    fun purge(database: FinanceDatabase) {
        val now = clock.now()
        // Tin không đọc được mà nguyên văn đã hết hạn thì không còn gì để user nhập: tự bỏ, hộp chờ không tồn đọng.
        database.captureQueries.expireUnparsed((now - RAW_TEXT_TTL).toEpochMilliseconds())
        database.captureQueries.purgeRawText((now - RAW_TEXT_TTL).toEpochMilliseconds())
        database.captureQueries.purgeFinished((now - FINISHED_TTL).toEpochMilliseconds())
    }

    private fun log(bank: BankId?, outcome: IngestOutcome): IngestOutcome {
        logger.i { "capture bank=${bank?.name} outcome=$outcome" }
        return outcome
    }

    private fun Int.pad(): String = toString().padStart(2, '0')

    private companion object {
        const val VND = "VND"
        val SESSION_WAIT = 10.seconds
        val RAW_TEXT_TTL = 14.days
        val FINISHED_TTL = 30.days
        val NOTIFY_ON = setOf(IngestOutcome.DRAFTED, IngestOutcome.NEEDS_ACCOUNT, IngestOutcome.UNPARSED)
    }
}
