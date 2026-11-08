package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.result.map
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.local.TransactionSearch
import com.uit.finance.core.ledger.data.local.filtered
import com.uit.finance.core.ledger.data.local.filteredTotals
import com.uit.finance.core.ledger.data.local.rejectWith
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.syncRefs
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.ledger.data.local.toPayload
import com.uit.finance.core.ledger.data.local.toTotals
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.domain.validation.LedgerRules
import com.uit.finance.core.ledger.domain.validation.normalizedOrNull
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.outbox.OutboxWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class TransactionRepositoryImpl(
    private val store: LedgerStore,
    private val outbox: OutboxWriter,
    private val codec: SyncPayloadCodec,
    private val uuidGenerator: UuidGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : TransactionRepository {

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = store.observe(emptyList()) { database ->
        database.ledgerTransactionQueries.selectRecent(limit.toLong()).asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observe(transactionId: String): Flow<Transaction?> = store.observe(null) { database ->
        database.ledgerTransactionQueries.selectById(transactionId).asFlow().mapToOneOrNull(dispatchers.io)
            .map { row -> row?.takeIf { it.deleted == 0L }?.toDomain() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFiltered(filter: TransactionFilter, limit: Int): Flow<List<Transaction>> =
        store.observe(emptyList()) { database ->
            database.observeSearch(filter)
                .flatMapLatest { search ->
                    database.ledgerSearchQueries.filtered(search, limit.toLong()).asFlow().mapToList(dispatchers.io)
                }
                .map { rows -> rows.map { it.toDomain() } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFilteredTotals(filter: TransactionFilter): Flow<TransactionTotals> =
        store.observe(TransactionTotals.EMPTY) { database ->
            database.observeSearch(filter)
                .flatMapLatest { search -> database.ledgerSearchQueries.filteredTotals(search).asFlow().mapToList(dispatchers.io) }
                .map { rows -> rows.toTotals() }
        }

    /**
     * Tham số SQL của [filter], tính lại khi danh mục đổi: đổi tên hay đổi cha làm đổi kết quả tìm theo tên danh mục
     * và lọc cha gồm con (F6). Danh mục đổi mà tham số không đổi thì không chạy lại câu lọc.
     */
    private fun FinanceDatabase.observeSearch(filter: TransactionFilter): Flow<TransactionSearch> =
        categoryQueries.selectActive().asFlow().mapToList(dispatchers.io)
            .map { rows -> TransactionSearch.of(filter, rows.map { it.toDomain() }) }
            .distinctUntilChanged()

    override suspend fun record(draft: TransactionDraft): AppResult<Transaction> = recordAll(listOf(draft)).map { it.single() }

    override suspend fun recordAll(drafts: List<TransactionDraft>): AppResult<List<Transaction>> {
        val candidates = drafts.map { draft ->
            Transaction(
                id = uuidGenerator.generate(),
                type = draft.type,
                status = draft.status,
                accountId = draft.accountId,
                counterAccountId = draft.counterAccountId,
                amount = Money(draft.amountMinor.coerceAtLeast(0), PLACEHOLDER_CURRENCY), // tiền tệ lấy theo ví bên dưới
                categoryId = draft.categoryId,
                occurredOn = draft.occurredOn,
                occurredAt = draft.occurredAt,
                payee = draft.payee.normalizedOrNull(),
                note = draft.note.normalizedOrNull(),
            )
        }
        val errors = candidates.zip(drafts).flatMap { (candidate, draft) -> fieldErrors(candidate, draft.amountMinor) }
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        // Một transaction DB cho cả nhóm: dòng nào vi phạm luật thì không dòng nào được lưu (D3: chuyển + phí).
        return store.write { database ->
            candidates.zip(drafts).map { (candidate, draft) ->
                val account = requireAccount(database, draft.accountId)
                candidate.copy(amount = Money(draft.amountMinor, account.currency))
                    .also { checkReferences(database, it, account) }
                    .also { persist(database, it) }
            }
        }
    }

    override suspend fun update(transaction: Transaction): AppResult<Transaction> {
        val normalized = transaction.copy(payee = transaction.payee.normalizedOrNull(), note = transaction.note.normalizedOrNull())
        val errors = fieldErrors(normalized, normalized.amount.amountMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            requireActive(database, transaction.id)
            val account = requireAccount(database, normalized.accountId)
            normalized.copy(amount = Money(normalized.amount.amountMinor, account.currency))
                .also { checkReferences(database, it, account) }
                .also { persist(database, it) }
        }
    }

    override suspend fun delete(transactionId: String): AppResult<Unit> = store.write { database ->
        requireActive(database, transactionId)
        database.ledgerTransactionQueries.markDeleted(transactionId)
        outbox.delete(database, SyncEntity.TRANSACTION, transactionId)
    }

    private fun fieldErrors(transaction: Transaction, amountMinor: Long): List<FieldError> =
        LedgerRules.amount(amountMinor) +
            LedgerRules.optionalText(transaction.payee, LedgerLimits.MAX_PAYEE_LENGTH, "payee") +
            LedgerRules.optionalText(transaction.note, LedgerLimits.MAX_NOTE_LENGTH, "note") +
            LedgerRules.occurredOn(transaction.occurredOn, clock.today()) +
            LedgerRules.shape(transaction.type, transaction.accountId, transaction.counterAccountId, transaction.categoryId)

    /**
     * Cùng luật với backend (`TransactionRules`): ví đến cùng tiền tệ, danh mục đúng loại. Ví/danh mục đã archive
     * vẫn trỏ tới được — archive chỉ ẩn khỏi danh sách chọn ở UI (ADR-005 §3).
     */
    private fun checkReferences(database: FinanceDatabase, transaction: Transaction, account: Account) {
        if (transaction.type == TransactionType.TRANSFER) {
            val counterId = requireNotNull(transaction.counterAccountId)
            val counter = requireAccount(database, counterId, field = "counterAccountId")
            if (counter.currency != account.currency) rejectWith("counterAccountId", LedgerErrorCode.INVALID_TRANSFER)
        } else {
            val categoryId = requireNotNull(transaction.categoryId)
            val category = requireCategory(database, categoryId)
            val expected = if (transaction.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            if (category.kind != expected) rejectWith("categoryId", LedgerErrorCode.CATEGORY_KIND_MISMATCH)
        }
    }

    private fun requireAccount(database: FinanceDatabase, accountId: String, field: String = "accountId"): Account =
        database.accountQueries.selectById(accountId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith(field, LedgerErrorCode.NOT_FOUND)

    private fun requireCategory(database: FinanceDatabase, categoryId: String): Category =
        database.categoryQueries.selectById(categoryId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("categoryId", LedgerErrorCode.NOT_FOUND)

    private fun requireActive(database: FinanceDatabase, transactionId: String): Transaction =
        database.ledgerTransactionQueries.selectById(transactionId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("transaction", LedgerErrorCode.NOT_FOUND)

    private fun persist(database: FinanceDatabase, transaction: Transaction) {
        database.save(transaction)
        outbox.upsert(database, SyncEntity.TRANSACTION, transaction.id, codec.encode(transaction.toPayload()), transaction.syncRefs())
    }

    private companion object {
        const val PLACEHOLDER_CURRENCY = "XXX" // ISO-4217 "không có tiền tệ"; bị thay bằng tiền tệ của ví trước khi lưu
    }
}
