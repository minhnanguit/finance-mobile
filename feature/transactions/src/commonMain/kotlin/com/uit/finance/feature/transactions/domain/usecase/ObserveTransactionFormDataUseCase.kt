package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.feature.transactions.domain.model.TransactionFormData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Ví + danh mục cho form giao dịch; tự cập nhật khi tạo ví/danh mục mới hay sync về. */
class ObserveTransactionFormDataUseCase(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(): Flow<TransactionFormData> =
        combine(accounts.observeAccounts(), categories.observeCategories()) { accountList, categoryList ->
            TransactionFormData(accounts = accountList, categories = categoryList)
        }
}
