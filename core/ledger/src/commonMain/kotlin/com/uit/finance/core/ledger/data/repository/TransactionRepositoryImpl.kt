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
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.local.rejectWith
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.ledger.data.local.toPayload
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.domain.validation.LedgerRules
import com.uit.finance.core.ledger.domain.validation.normalizedOrNull
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.outbox.OutboxWriter
import kotlinx.coroutines.flow.Flow
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

    override suspend fun record(draft: TransactionDraft): AppResult<Transaction> {
        val candidate = Transaction(
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
        val errors = fieldErrors(candidate, draft.amountMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val account = requireAttachable(database, draft.accountId, previous = null)
            candidate.copy(amount = Money(draft.amountMinor, account.currency))
                .also { checkReferences(database, it, account, previous = null) }
                .also { persist(database, it) }
        }
    }

    override suspend fun update(transaction: Transaction): AppResult<Transaction> {
        val normalized = transaction.copy(payee = transaction.payee.normalizedOrNull(), note = transaction.note.normalizedOrNull())
        val errors = fieldErrors(normalized, normalized.amount.amountMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val previous = requireActive(database, transaction.id)
            val account = requireAttachable(database, normalized.accountId, previous = previous.accountId)
            normalized.copy(amount = Money(normalized.amount.amountMinor, account.currency))
                .also { checkReferences(database, it, account, previous) }
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
     * Cùng luật với backend (`TransactionRules`): ví đến cùng tiền tệ, danh mục đúng loại. Không gắn mới
     * vào ví/danh mục đã archive; giao dịch cũ đã nằm sẵn trong đó vẫn sửa được.
     */
    private fun checkReferences(database: FinanceDatabase, transaction: Transaction, account: Account, previous: Transaction?) {
        if (transaction.type == TransactionType.TRANSFER) {
            val counterId = requireNotNull(transaction.counterAccountId)
            val counter = requireAttachable(database, counterId, previous?.counterAccountId, field = "counterAccountId")
            if (counter.currency != account.currency) rejectWith("counterAccountId", LedgerErrorCode.INVALID_TRANSFER)
        } else {
            val categoryId = requireNotNull(transaction.categoryId)
            val category = requireCategory(database, categoryId, previous?.categoryId)
            val expected = if (transaction.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            if (category.kind != expected) rejectWith("categoryId", LedgerErrorCode.CATEGORY_KIND_MISMATCH)
        }
    }

    private fun requireAttachable(
        database: FinanceDatabase,
        accountId: String,
        previous: String?,
        field: String = "accountId",
    ): Account {
        val account = database.accountQueries.selectById(accountId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith(field, LedgerErrorCode.NOT_FOUND)
        if (account.archived && accountId != previous) rejectWith(field, LedgerErrorCode.ARCHIVED)
        return account
    }

    private fun requireCategory(database: FinanceDatabase, categoryId: String, previous: String?): Category {
        val category = database.categoryQueries.selectById(categoryId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("categoryId", LedgerErrorCode.NOT_FOUND)
        if (category.archived && categoryId != previous) rejectWith("categoryId", LedgerErrorCode.ARCHIVED)
        return category
    }

    private fun requireActive(database: FinanceDatabase, transactionId: String): Transaction =
        database.ledgerTransactionQueries.selectById(transactionId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("transaction", LedgerErrorCode.NOT_FOUND)

    private fun persist(database: FinanceDatabase, transaction: Transaction) {
        database.save(transaction)
        outbox.upsert(database, SyncEntity.TRANSACTION, transaction.id, codec.encode(transaction.toPayload()))
    }

    private companion object {
        const val PLACEHOLDER_CURRENCY = "XXX" // ISO-4217 "không có tiền tệ"; bị thay bằng tiền tệ của ví trước khi lưu
    }
}
