package com.uit.finance.feature.home.domain.usecase

import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.feature.home.domain.model.AccountOverview
import com.uit.finance.feature.home.domain.model.HomeDashboard
import com.uit.finance.feature.home.domain.model.RecentTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Gom ví + số dư + thu/chi tháng của [today] + giao dịch gần đây thành một [HomeDashboard]; mỗi nguồn đổi là
 * phát bản mới (offline-first: dữ liệu từ DB trên máy, sync chạy ngầm).
 *
 * Ngày do nơi gọi truyền vào: kỳ "tháng này" gắn với [today], nên qua tháng thì gọi lại với ngày mới
 * (HomeViewModel làm việc đó mỗi lần màn hình resume) thay vì giữ kỳ cũ cho tới khi ViewModel được tạo lại.
 */
class ObserveHomeDashboardUseCase(
    private val accounts: AccountRepository,
    private val balances: BalanceRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) {
    operator fun invoke(today: LocalDate): Flow<HomeDashboard> {
        val monthStart = LocalDate(today.year, today.month, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        return combine(
            accounts.observeAccounts(),
            balances.observeBalances(),
            balances.observeCashFlow(monthStart, monthEnd),
            transactions.observeRecent(RECENT_LIMIT),
            categories.observeCategories(),
        ) { accountList, balanceSummary, cashFlow, recent, categoryList ->
            val balanceById = balanceSummary.accounts.associate { it.accountId to it.balance }
            val accountById = accountList.associateBy { it.id }
            val categoryById = categoryList.associateBy { it.id }
            HomeDashboard(
                accounts = accountList
                    .filterNot { it.archived }
                    .map { account -> AccountOverview(account, balanceById[account.id] ?: account.openingBalance) },
                monthCashFlow = cashFlow,
                recentTransactions = recent.map { transaction ->
                    RecentTransaction(
                        transaction = transaction,
                        account = accountById[transaction.accountId],
                        category = transaction.categoryId?.let(categoryById::get),
                    )
                },
            )
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
