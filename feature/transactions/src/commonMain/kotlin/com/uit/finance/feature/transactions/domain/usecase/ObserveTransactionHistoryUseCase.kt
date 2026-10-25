package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.feature.transactions.domain.model.TransactionDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * [limit] giao dịch mới nhất khớp [TransactionFilter], kèm ví/danh mục. Không lọc gì ([TransactionFilter.ALL]) là toàn
 * bộ lịch sử. Cuộn vô hạn = gọi lại với limit lớn hơn (dữ liệu nằm sẵn trên máy, câu lọc đi theo index ngày nên nhanh).
 * Khi một user có hàng chục nghìn giao dịch thì chuyển sang phân trang keyset `(occurred_on, id)` — xem LEDGER-PLAN
 * Phase 5.
 */
class ObserveTransactionHistoryUseCase(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(filter: TransactionFilter, limit: Int): Flow<List<TransactionDetails>> = combine(
        transactions.observeFiltered(filter, limit),
        accounts.observeAccounts(),
        categories.observeCategories(),
    ) { recent, accountList, categoryList ->
        val accountById = accountList.associateBy { it.id }
        val categoryById = categoryList.associateBy { it.id }
        recent.map { transaction ->
            TransactionDetails(
                transaction = transaction,
                account = accountById[transaction.accountId],
                counterAccount = transaction.counterAccountId?.let(accountById::get),
                category = transaction.categoryId?.let(categoryById::get),
            )
        }
    }
}
