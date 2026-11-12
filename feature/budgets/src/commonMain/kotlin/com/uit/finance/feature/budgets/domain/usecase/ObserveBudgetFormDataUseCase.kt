package com.uit.finance.feature.budgets.domain.usecase

import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.feature.budgets.domain.model.BudgetFormData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Tiền tệ của ví, danh mục chọn được và ngân sách hiện có, cho form tạo/sửa ngân sách (G2, G3, G13, G15). */
class ObserveBudgetFormDataUseCase(
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<BudgetFormData> = combine(
        accounts.observeAccounts(),
        categories.observeCategories(),
        budgets.observeBudgets(),
    ) { allAccounts, allCategories, allBudgets -> BudgetFormData.from(allAccounts, allCategories, allBudgets) }
}
