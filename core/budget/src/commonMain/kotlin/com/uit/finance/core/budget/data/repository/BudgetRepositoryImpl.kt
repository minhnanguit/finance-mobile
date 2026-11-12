package com.uit.finance.core.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.budget.data.local.BudgetStore
import com.uit.finance.core.budget.data.local.rejectWith
import com.uit.finance.core.budget.data.local.save
import com.uit.finance.core.budget.data.local.syncRefs
import com.uit.finance.core.budget.data.local.toDomain
import com.uit.finance.core.budget.data.local.toPayload
import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.model.BudgetErrorCode
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.budget.domain.validation.BudgetLimits
import com.uit.finance.core.budget.domain.validation.BudgetRules
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.BudgetPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.outbox.OutboxWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Module **duy nhất** đụng bảng `budget` (Konsist `BudgetDataRulesTest`). Bản ghi + op outbox + `outbox_ref`
 * tới danh mục luôn đi chung một transaction (ADR-007 GS5).
 */
internal class BudgetRepositoryImpl(
    private val store: BudgetStore,
    private val outbox: OutboxWriter,
    private val codec: BudgetPayloadCodec,
    private val uuidGenerator: UuidGenerator,
    private val dispatchers: DispatcherProvider,
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> = store.observe(emptyList()) { database ->
        database.budgetQueries.selectActive().asFlow().mapToList(dispatchers.io).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun create(draft: BudgetDraft, countsTowardsLimit: (Budget) -> Boolean): AppResult<Budget> {
        val errors = BudgetRules.amount(draft.amountMinor) + BudgetRules.currency(draft.currency) +
            BudgetRules.startMonth(draft.startMonth)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            if (database.budgetQueries.selectActiveFor(draft.categoryId, draft.currency).executeAsOneOrNull() != null) {
                rejectWith("categoryId", BudgetErrorCode.DUPLICATE)
            }
            val counted = database.budgetQueries.selectActive().executeAsList().count { countsTowardsLimit(it.toDomain()) }
            if (counted >= BudgetLimits.MAX_BUDGETS) {
                rejectWith("budget", BudgetErrorCode.LIMIT_REACHED)
            }
            val budget = Budget(
                id = uuidGenerator.generate(),
                categoryId = draft.categoryId,
                amount = Money(draft.amountMinor, draft.currency),
                startMonth = draft.startMonth,
            )
            budget.also { persist(database, it) }
        }
    }

    override suspend fun updateAmount(budgetId: String, amountMinor: Long): AppResult<Budget> {
        val errors = BudgetRules.amount(amountMinor)
        if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))

        return store.write { database ->
            val existing = requireActive(database, budgetId)
            if (existing.amount.amountMinor == amountMinor) return@write existing
            existing.copy(amount = Money(amountMinor, existing.currency)).also { persist(database, it) }
        }
    }

    override suspend fun delete(budgetId: String): AppResult<Unit> = store.write { database ->
        requireActive(database, budgetId)
        database.budgetQueries.markDeleted(budgetId)
        outbox.delete(database, SyncEntity.BUDGET, budgetId)
    }

    private fun persist(database: FinanceDatabase, budget: Budget) {
        database.save(budget)
        outbox.upsert(database, SyncEntity.BUDGET, budget.id, codec.encode(budget.toPayload()), budget.syncRefs())
    }

    private fun requireActive(database: FinanceDatabase, budgetId: String): Budget =
        database.budgetQueries.selectById(budgetId).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.toDomain()
            ?: rejectWith("budget", BudgetErrorCode.NOT_FOUND)
}
