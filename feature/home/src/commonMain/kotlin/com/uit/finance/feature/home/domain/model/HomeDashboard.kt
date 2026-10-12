package com.uit.finance.feature.home.domain.model

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.Transaction

/** Ví kèm số dư hiện tại (tính từ lịch sử, D4). */
data class AccountOverview(val account: Account, val balance: Money)

/** Giao dịch kèm ví, ví nhận (chuyển tiền) và danh mục đã tra sẵn để màn hình không phải tự ghép. `null` khi chưa pull về. */
data class RecentTransaction(
    val transaction: Transaction,
    val account: Account?,
    val category: Category?,
    val counterAccount: Account? = null,
)

/** Mọi số liệu trang chủ cần, gom từ `core/ledger` trong một lần phát. */
data class HomeDashboard(
    /** Ví chưa archive, theo `sortOrder`. */
    val accounts: List<AccountOverview>,
    /** Thu/chi của tháng hiện tại theo lịch máy. */
    val monthCashFlow: CashFlow,
    val recentTransactions: List<RecentTransaction>,
) {
    companion object {
        val EMPTY = HomeDashboard(emptyList(), CashFlow.EMPTY, emptyList())
    }
}
