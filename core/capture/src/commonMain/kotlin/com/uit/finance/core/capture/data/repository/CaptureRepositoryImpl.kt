@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.core.capture.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.capture.data.CaptureIngestor
import com.uit.finance.core.capture.data.CaptureInboxNotifier
import com.uit.finance.core.capture.data.settings.CaptureSettingsRepositoryImpl
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.BankMessage
import com.uit.finance.core.capture.domain.model.CaptureEvent
import com.uit.finance.core.capture.domain.model.CaptureIds
import com.uit.finance.core.capture.domain.model.CaptureStatus
import com.uit.finance.core.capture.domain.model.Confidence
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.model.IgnoreReason
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.model.ParseResult
import com.uit.finance.core.capture.domain.parser.BankMessageParser
import com.uit.finance.core.capture.domain.repository.CaptureInbox
import com.uit.finance.core.capture.domain.repository.CaptureRepository
import com.uit.finance.core.capture.domain.repository.DraftItem
import com.uit.finance.core.capture.domain.repository.PasteParseResult
import com.uit.finance.core.capture.domain.repository.PastePreview
import com.uit.finance.core.capture.domain.repository.PasteSubmission
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.Capture_event
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.occurrence.LedgerOccurrenceWriter
import com.uit.finance.core.ledger.data.occurrence.OccurrenceDraft
import com.uit.finance.core.ledger.data.occurrence.OccurrenceWrite
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserSession
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal class CaptureRepositoryImpl(
    private val databases: UserDatabaseProvider,
    private val session: UserSession,
    private val settings: CaptureSettingsRepositoryImpl,
    private val ingestor: CaptureIngestor,
    private val writer: LedgerOccurrenceWriter,
    private val transactions: TransactionRepository,
    private val notifier: CaptureInboxNotifier,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : CaptureRepository {

    private fun observeEvents(): Flow<List<CaptureEvent>> = databases.database.flatMapLatest { database ->
        database?.captureQueries?.selectAll()?.asFlow()?.mapToList(dispatchers.io)?.map { rows -> rows.map { it.toDomain() } }
            ?: flowOf(emptyList())
    }

    private fun observeDrafts(): Flow<List<Transaction>> =
        transactions.observeFiltered(TransactionFilter(status = TransactionStatus.DRAFT), MAX_DRAFTS)

    override fun observeInbox(): Flow<CaptureInbox> = combine(observeDrafts(), observeEvents()) { drafts, events -> drafts to events }
        .flatMapLatest { (drafts, events) ->
            neighbours(drafts).map { others ->
                val byId = events.associateBy { it.id }
                CaptureInbox(
                    drafts = drafts.map { draft ->
                        val event = byId[draft.id]
                        DraftItem(
                            transaction = draft,
                            bank = event?.bank,
                            needsCheck = event?.confidence == Confidence.LOW,
                            possibleDuplicate = others.any { it.looksLike(draft) },
                        )
                    },
                    needsAccount = events.filter { it.status == CaptureStatus.NEEDS_ACCOUNT }.sortedByDescending { it.receivedAt },
                    unparsed = events.filter { it.status == CaptureStatus.UNPARSED }.sortedByDescending { it.receivedAt },
                )
            }
        }

    /** Giao dịch quanh ngày của các nháp (±1 ngày, cùng ví, cùng loại) để gắn nhãn "Có thể trùng" (D8). */
    private fun neighbours(drafts: List<Transaction>): Flow<List<Transaction>> {
        if (drafts.isEmpty()) return flowOf(emptyList())
        val from = drafts.minOf { it.occurredOn }.minus(DatePeriod(days = 1))
        val to = drafts.maxOf { it.occurredOn }.plus(DatePeriod(days = 1))
        val filter = TransactionFilter(
            from = from,
            to = to,
            types = drafts.mapTo(mutableSetOf()) { it.type },
            accountIds = drafts.mapTo(mutableSetOf()) { it.accountId },
        )
        return transactions.observeFiltered(filter, MAX_NEIGHBOURS)
    }

    private fun Transaction.looksLike(draft: Transaction): Boolean =
        id != draft.id && accountId == draft.accountId && type == draft.type && amount == draft.amount &&
            occurredOn >= draft.occurredOn.minus(DatePeriod(days = 1)) && occurredOn <= draft.occurredOn.plus(DatePeriod(days = 1))

    override fun observePendingCount(): Flow<Int> = combine(observeDrafts(), observeEvents()) { drafts, events ->
        drafts.size + events.count { it.status == CaptureStatus.NEEDS_ACCOUNT || it.status == CaptureStatus.UNPARSED }
    }

    override suspend fun confirm(transactionId: String): AppResult<Unit> {
        val draft = transactions.observe(transactionId).first()
            ?: return AppResult.Failure(AppError.Validation(listOf(FieldError("transaction", "capture.not_found"))))
        return when (val result = transactions.update(draft.copy(status = TransactionStatus.CONFIRMED))) {
            is AppResult.Success -> AppResult.Success(Unit).also { notifier.refresh() }
            is AppResult.Failure -> result
        }
    }

    override suspend fun dismissDraft(transactionId: String): AppResult<Unit> {
        val result = transactions.delete(transactionId)
        if (result is AppResult.Success) {
            writeLocal { database ->
                if (database.captureQueries.selectEvent(transactionId).executeAsOneOrNull() != null) {
                    database.captureQueries.setStatus(CaptureStatus.DISMISSED.name, transactionId)
                }
            }
            notifier.refresh()
        }
        return result
    }

    override suspend fun dismissEvent(eventId: String): AppResult<Unit> =
        writeLocal { database -> database.captureQueries.setStatus(CaptureStatus.DISMISSED.name, eventId) }
            .also { notifier.refresh() }

    override suspend fun linkAccount(bank: BankId, accountHint: String?, accountId: String): AppResult<Int> {
        val userId = currentUserId() ?: return noUser()
        val database = databases.database.value ?: return noUser()
        return guarded {
            withContext(dispatchers.io) {
                database.captureQueries.upsertLink(bank.name, accountHint.orEmpty(), accountId)
                val current = settings.read(userId)
                val waiting = database.captureQueries.selectNeedsAccount(bank.name, accountHint.orEmpty()).executeAsList()
                waiting.count { row ->
                    val pending = row.toPending() ?: return@count false
                    ingestor.write(database, current, pending, rawText = row.raw_text) == IngestOutcome.DRAFTED
                }
            }
        }.also { notifier.refresh() }
    }

    override suspend fun parsePasted(text: String, bank: BankId?): PasteParseResult {
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        return when (val result = BankMessageParser.parse(BankMessage(bank, null, text, now))) {
            is ParseResult.Ignored -> if (result.reason == IgnoreReason.OTP) PasteParseResult.Otp else PasteParseResult.NotReadable
            ParseResult.Unparsed -> PasteParseResult.NotReadable
            is ParseResult.Parsed -> {
                val event = result.event
                val userId = currentUserId()
                val current = userId?.let(settings::read)
                val accountId = bank?.let { b ->
                    withContext(dispatchers.io) {
                        databases.database.value?.captureQueries?.selectLink(b.name, event.accountHint.orEmpty())?.executeAsOneOrNull()
                    }
                }
                PasteParseResult.Ready(
                    PastePreview(
                        event = event,
                        occurredOn = event.postedAt?.date ?: now.toLocalDateTime(zone).date,
                        suggestedAccountId = accountId,
                        suggestedCategoryId = current?.let { ingestor.resolveCategory(it, event.direction) },
                    ),
                )
            }
        }
    }

    override suspend fun savePasted(submission: PasteSubmission): AppResult<IngestOutcome> {
        val userId = currentUserId() ?: return noUser()
        val database = databases.database.value ?: return noUser()
        if (submission.amountMinor <= 0) {
            return AppResult.Failure(AppError.Validation(listOf(FieldError("amount", "capture.amount_invalid"))))
        }
        val event = submission.preview.event
        val zone = TimeZone.currentSystemDefault()
        val minute = event.postedAt?.let { "${it.date}T${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
            ?: CaptureIds.minuteOf(clock.now(), zone)
        // Id theo tin gốc (không theo phần user sửa): dán lại đúng tin đó thì báo "đã ghi".
        val id = CaptureIds.transactionId(userId, CaptureIds.fingerprint(event, submission.preview.occurredOn, minute))
        val draft = OccurrenceDraft(
            id = id,
            type = if (event.direction == Direction.IN) TransactionType.INCOME else TransactionType.EXPENSE,
            status = TransactionStatus.CONFIRMED,
            accountId = submission.accountId,
            categoryId = submission.categoryId,
            amountMinor = submission.amountMinor,
            currency = "VND",
            occurredOn = submission.occurredOn,
            payee = event.payee,
            note = submission.note?.trim()?.ifBlank { null },
        )
        return guarded {
            withContext(dispatchers.io) {
                database.transactionWithResult {
                    if (submission.rememberAccount && submission.bank != null) {
                        database.captureQueries.upsertLink(submission.bank.name, event.accountHint.orEmpty(), submission.accountId)
                    }
                    when (writer.recordIfAbsent(database, draft)) {
                        OccurrenceWrite.WRITTEN -> IngestOutcome.SAVED
                        OccurrenceWrite.ALREADY_EXISTS -> IngestOutcome.ALREADY_EXISTS
                        OccurrenceWrite.BLOCKED -> IngestOutcome.REJECTED
                    }
                }
            }
        }.also { if ((it as? AppResult.Success)?.value == IngestOutcome.SAVED) syncScheduler.requestImmediate() }
    }

    override suspend fun purgeExpired() {
        writeLocal { database -> ingestor.purge(database) }
        notifier.clear()
    }

    private fun currentUserId(): String? = (session.state.value as? LocalDataState.Ready)?.userId

    private suspend fun writeLocal(block: (FinanceDatabase) -> Unit): AppResult<Unit> {
        val database = databases.database.value ?: return noUser()
        return guarded { withContext(dispatchers.io) { database.transaction { block(database) } } }
    }

    private suspend fun <T> guarded(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Storage(e::class.simpleName))
    }

    private fun noUser(): AppResult.Failure = AppResult.Failure(AppError.Storage("no active user database"))

    private fun Capture_event.toPending(): CaptureIngestor.PendingDraft? {
        val direction = direction?.let { value -> Direction.entries.firstOrNull { it.name == value } } ?: return null
        return CaptureIngestor.PendingDraft(
            id = id,
            bank = BankId.fromName(bank),
            direction = direction,
            amountMinor = amount_minor ?: return null,
            accountHint = account_hint,
            payee = payee,
            note = note,
            occurredOn = occurred_on?.let(LocalDate::parse) ?: return null,
            confidence = confidence,
            receivedAt = Instant.fromEpochMilliseconds(received_at),
        )
    }

    private companion object {
        const val MAX_DRAFTS = 500
        const val MAX_NEIGHBOURS = 2_000
    }
}

internal fun Capture_event.toDomain(): CaptureEvent = CaptureEvent(
    id = id,
    bank = BankId.fromName(bank),
    status = CaptureStatus.entries.firstOrNull { it.name == status } ?: CaptureStatus.UNPARSED,
    direction = direction?.let { value -> Direction.entries.firstOrNull { it.name == value } },
    amountMinor = amount_minor,
    accountHint = account_hint,
    payee = payee,
    note = note,
    occurredOn = occurred_on?.let(LocalDate::parse),
    confidence = confidence?.let { value -> Confidence.entries.firstOrNull { it.name == value } },
    rawText = raw_text,
    receivedAt = Instant.fromEpochMilliseconds(received_at),
)
