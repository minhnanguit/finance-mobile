package com.uit.finance.core.budget.data.local

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.BudgetPayload
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.outbox.SyncRef
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import com.uit.finance.core.database.Budget as BudgetRow

// ---- Dòng DB → domain ----

internal fun BudgetRow.toDomain() = Budget(
    id = id,
    categoryId = category_id,
    amount = Money(amount_minor, currency),
    startMonth = LocalDate.parse(start_month).yearMonth,
)

// ---- Domain → dòng DB (luôn là bản chưa xoá) ----

internal fun FinanceDatabase.save(budget: Budget) = budgetQueries.upsert(
    budget.id,
    budget.categoryId,
    budget.currency,
    budget.amount.amountMinor,
    budget.startMonth.firstDay.toString(),
    0L,
)

// ---- Domain ↔ payload của contract (startMonth là ngày 1 của tháng) ----

internal fun Budget.toPayload() = BudgetPayload(
    categoryId = categoryId,
    currency = currency,
    amountMinor = amount.amountMinor,
    startMonth = startMonth.firstDay,
)

internal fun BudgetPayload.toDomain(id: String) = Budget(
    id = id,
    categoryId = categoryId,
    amount = Money(amountMinor, currency),
    startMonth = startMonth.yearMonth,
)

/** Op ngân sách trỏ tới danh mục: op archive/xoá danh mục phải đợi op này xong (ADR-007 GS5). */
internal fun Budget.syncRefs(): List<SyncRef> = listOf(SyncRef(SyncEntity.CATEGORY, categoryId))
