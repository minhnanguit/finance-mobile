package com.uit.finance.feature.budgets.domain.usecase

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.budgets.domain.model.BudgetInput

/** Tạo mới, hoặc đổi số tiền của ngân sách đã có (chỉ số tiền đổi được, G8). */
class SaveBudgetUseCase(private val budgets: BudgetRepository) {
    suspend operator fun invoke(input: BudgetInput): AppResult<Budget> =
        if (input.id == null) {
            budgets.create(BudgetDraft(input.categoryId, input.currency, input.amountMinor, input.startMonth))
        } else {
            budgets.updateAmount(input.id, input.amountMinor)
        }
}
