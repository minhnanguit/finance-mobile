package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.local.rejectWith
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.ledger.data.local.toPayload
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.domain.validation.LedgerRules
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.outbox.OutboxWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AccountRepositoryImpl(
    private val store: LedgerStore,
    private val outbox: OutboxWriter,
    private val codec: SyncPayloadCodec,
    private val uuidGenerator: UuidGenerator,
    private val dispatchers: DispatcherProvider,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<Account>> = store.observe(emptyList()) { database ->
        database.accountQueries.selectActive().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun create(draft: AccountDraft): AppResult<Account> {
        val errors = LedgerRules.name(draft.name) + LedgerRules.currency(draft.currency) +
            LedgerRules.openingBalance(draft.openingBalanceMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            if (database.accountQueries.countActive().executeAsOne() >= LedgerLimits.MAX_ACCOUNTS) {
                rejectWith("account", LedgerErrorCode.LIMIT_EXCEEDED)
            }
            Account(
                id = uuidGenerator.generate(),
                name = draft.name.trim(),
                type = draft.type,
                openingBalance = Money(draft.openingBalanceMinor, draft.currency),
                sortOrder = draft.sortOrder,
                archived = false,
            ).also { persist(database, it) }
        }
    }

    override suspend fun update(account: Account): AppResult<Account> {
        val errors = LedgerRules.name(account.name) + LedgerRules.currency(account.currency) +
            LedgerRules.openingBalance(account.openingBalance.amountMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val existing = requireActive(database, account.id)
            if (existing.currency != account.currency && isReferenced(database, account.id)) {
                rejectWith("currency", LedgerErrorCode.CURRENCY_LOCKED)
            }
            account.copy(name = account.name.trim()).also { persist(database, it) }
        }
    }

    override suspend fun setArchived(accountId: String, archived: Boolean): AppResult<Unit> = store.write { database ->
        val existing = requireActive(database, accountId)
        if (existing.archived != archived) persist(database, existing.copy(archived = archived))
    }

    override suspend fun delete(accountId: String): AppResult<Unit> = store.write { database ->
        requireActive(database, accountId)
        if (isReferenced(database, accountId)) rejectWith("account", LedgerErrorCode.IN_USE)
        database.accountQueries.markDeleted(accountId)
        outbox.delete(database, SyncEntity.ACCOUNT, accountId)
    }

    private fun persist(database: FinanceDatabase, account: Account) {
        database.save(account)
        outbox.upsert(database, SyncEntity.ACCOUNT, account.id, codec.encode(account.toPayload()))
    }

    private fun requireActive(database: FinanceDatabase, accountId: String): Account =
        database.accountQueries.selectById(accountId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("account", LedgerErrorCode.NOT_FOUND)

    private fun isReferenced(database: FinanceDatabase, accountId: String): Boolean =
        database.accountQueries.isReferenced(accountId).executeAsOne() > 0
}
