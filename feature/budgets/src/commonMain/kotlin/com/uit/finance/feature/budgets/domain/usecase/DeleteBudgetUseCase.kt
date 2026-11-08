package com.uit.finance.feature.budgets.domain.usecase

import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.common.result.AppResult

class DeleteBudgetUseCase(private val budgets: BudgetRepository) {
    suspend operator fun invoke(budgetId: String): AppResult<Unit> = budgets.delete(budgetId)
}
