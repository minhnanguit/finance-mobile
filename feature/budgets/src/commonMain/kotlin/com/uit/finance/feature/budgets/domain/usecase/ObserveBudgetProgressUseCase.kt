package com.uit.finance.feature.budgets.domain.usecase

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.SpendingRepository
import com.uit.finance.feature.budgets.domain.model.BudgetMath
import com.uit.finance.feature.budgets.domain.model.BudgetOverview
import com.uit.finance.feature.budgets.domain.model.BudgetProgress
import com.uit.finance.feature.budgets.domain.model.BudgetTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Ngân sách + "đã chi" + danh mục của một tháng → tiến độ từng dòng và thẻ tổng theo tiền tệ (ADR-007 §1).
 * "Đã chi" luôn tính từ giao dịch (G6); tự cập nhật khi ghi giao dịch, khi sync kéo ngân sách về, và theo [today]
 * (số "mỗi ngày còn được chi", G11-G12).
 */
class ObserveBudgetProgressUseCase(
    private val budgets: BudgetRepository,
    private val spending: SpendingRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(month: YearMonth, today: LocalDate): Flow<BudgetOverview> = combine(
        budgets.observeBudgets(),
        spending.observeMonthlyExpense(month),
        categories.observeCategories(),
    ) { allBudgets, spent, allCategories ->
        val spentByKey = spent.associateBy({ it.categoryId to it.spent.currency }, CategorySpend::spent)
        val categoriesById = allCategories.associateBy(Category::id)
        val items = allBudgets
            .filter { it.appliesTo(month) } // tháng trước start_month không hiện (G4)
            .map { budget -> progress(budget, categoriesById[budget.categoryId], spentByKey, month, today) }
            .sortedWith(compareByDescending<BudgetProgress> { it.ratio }.thenBy { it.category?.name?.lowercase() })
        BudgetOverview(month = month, items = items, totals = totals(items))
    }

    private fun progress(
        budget: Budget,
        category: Category?,
        spentByKey: Map<Pair<String, String>, Money>,
        month: YearMonth,
        today: LocalDate,
    ): BudgetProgress {
        val amount = budget.amount.amountMinor
        val spent = spentByKey[budget.categoryId to budget.currency] ?: Money.zero(budget.currency)
        val remaining = Money(BudgetMath.saturatingMinus(amount, spent.amountMinor), budget.currency)
        return BudgetProgress(
            budget = budget,
            category = category,
            spent = spent,
            remaining = remaining,
            percent = BudgetMath.percent(spent.amountMinor, amount),
            ratio = BudgetMath.ratio(spent.amountMinor, amount),
            tone = BudgetMath.tone(spent.amountMinor, amount),
            dailyAllowance = BudgetMath.dailyAllowance(remaining, month, today),
        )
    }

    /** Mỗi tiền tệ một dòng, không cộng chéo (ADR-001); cộng bão hoà (G10). */
    private fun totals(items: List<BudgetProgress>): List<BudgetTotal> =
        items.groupBy { it.budget.currency }
            .map { (currency, rows) ->
                val budgeted = rows.fold(0L) { total, it -> BudgetMath.saturatingPlus(total, it.budget.amount.amountMinor) }
                val spent = rows.fold(0L) { total, it -> BudgetMath.saturatingPlus(total, it.spent.amountMinor) }
                BudgetTotal(
                    budgeted = Money(budgeted, currency),
                    spent = Money(spent, currency),
                    remaining = Money(BudgetMath.saturatingMinus(budgeted, spent), currency),
                )
            }
            .sortedBy { it.currency }
}
