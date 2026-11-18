package com.uit.finance.core.ledger.data.occurrence

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.syncRefs
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.ledger.data.local.toPayload
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.domain.validation.normalizedOrNull
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.outbox.SyncRef
import kotlinx.datetime.LocalDate

/**
 * Một giao dịch sinh từ khoản định kỳ (ADR-009 §2): [id] đã tính sẵn bằng UUIDv5, [refs] thêm tham chiếu ngoài ví và
 * danh mục (khoản định kỳ, để op `CREATE` không lên server trước khoản của nó, RS7).
 */
data class OccurrenceDraft(
    val id: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val currency: String,
    val occurredOn: LocalDate,
    val payee: String?,
    val note: String?,
    val refs: List<SyncRef> = emptyList(),
) {
    /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
    override fun toString(): String = "OccurrenceDraft(id=$id, type=$type, status=$status, occurredOn=$occurredOn)"
}

enum class OccurrenceWrite {
    /** Đã ghi dòng ledger + op `CREATE` trong transaction của người gọi. */
    WRITTEN,

    /** Id đã có trên máy, kể cả đã xoá (đã bỏ qua kỳ này): không làm gì (ADR-009 RS5, RS13). */
    ALREADY_EXISTS,

    /** Ví/danh mục đã lưu trữ, đã xoá, chưa có, khác tiền tệ hoặc khác loại: không sinh kỳ này (R13). */
    BLOCKED,
}

/**
 * Cửa duy nhất để module khác ghi một giao dịch theo kỳ, **bên trong transaction DB của người gọi**: core/recurring
 * kiểm revision của khoản và ghi kỳ trong cùng một transaction (RS13) mà không đụng bảng ledger (Konsist).
 */
interface LedgerOccurrenceWriter {
    fun recordIfAbsent(database: FinanceDatabase, draft: OccurrenceDraft): OccurrenceWrite
}

internal class DefaultLedgerOccurrenceWriter(
    private val outbox: OutboxWriter,
    private val codec: SyncPayloadCodec,
) : LedgerOccurrenceWriter {

    override fun recordIfAbsent(database: FinanceDatabase, draft: OccurrenceDraft): OccurrenceWrite {
        if (database.ledgerTransactionQueries.selectById(draft.id).executeAsOneOrNull() != null) {
            return OccurrenceWrite.ALREADY_EXISTS
        }
        val accountRow = database.accountQueries.selectById(draft.accountId).executeAsOneOrNull()
        val categoryRow = database.categoryQueries.selectById(draft.categoryId).executeAsOneOrNull()
        val account = accountRow?.takeIf { it.deleted == 0L }?.toDomain()
        val category = categoryRow?.takeIf { it.deleted == 0L }?.toDomain()
        val expectedKind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val usable = account != null && category != null && !account.archived && !category.archived &&
            account.currency == draft.currency && category.kind == expectedKind &&
            draft.type != TransactionType.TRANSFER && draft.amountMinor in 1..LedgerLimits.MAX_AMOUNT_MINOR
        if (!usable) return OccurrenceWrite.BLOCKED

        val transaction = Transaction(
            id = draft.id,
            type = draft.type,
            status = draft.status,
            accountId = draft.accountId,
            counterAccountId = null,
            amount = Money(draft.amountMinor, draft.currency),
            categoryId = draft.categoryId,
            occurredOn = draft.occurredOn,
            occurredAt = null,
            payee = draft.payee.normalizedOrNull()?.take(LedgerLimits.MAX_PAYEE_LENGTH),
            note = draft.note.normalizedOrNull()?.take(LedgerLimits.MAX_NOTE_LENGTH),
        )
        database.save(transaction)
        outbox.create(
            database,
            SyncEntity.TRANSACTION,
            transaction.id,
            codec.encode(transaction.toPayload()),
            transaction.syncRefs() + draft.refs,
        )
        return OccurrenceWrite.WRITTEN
    }
}

/** Dựng writer thật ngoài Koin (test của module khác trên schema thật). */
fun ledgerOccurrenceWriter(outbox: OutboxWriter, codec: SyncPayloadCodec): LedgerOccurrenceWriter =
    DefaultLedgerOccurrenceWriter(outbox, codec)
