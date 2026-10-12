package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.text.toLongLabel
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.home.domain.model.HomeDashboard

/** Domain → chuỗi hiển thị. Tách khỏi ViewModel để test riêng và để Screen không phải format gì. */
internal class HomeUiMapper(
    private val money: MoneyFormatter,
    private val transactions: TransactionItemFormatter,
    private val clock: Clock,
) {
    fun map(dashboard: HomeDashboard, current: HomeState): HomeState {
        val today = clock.today()
        val accounts = dashboard.accounts.map { overview ->
            val formatted = money.format(overview.balance)
            AccountCardUi(
                id = overview.account.id,
                name = overview.account.name,
                type = overview.account.type,
                currency = overview.account.currency,
                balance = formatted.amount,
                isNegative = formatted.isNegative,
            )
        }
        val currencies = accounts.map { it.currency }.toSet() + dashboard.monthCashFlow.currencies
        return current.copy(
            isLoading = false,
            todayLabel = today.toLongLabel(),
            accounts = accounts,
            monthByCurrency = currencies.associateWith { currency ->
                MonthSummaryUi(
                    currency = currency,
                    income = money.format(dashboard.monthCashFlow.income(currency)).amount,
                    expense = money.format(dashboard.monthCashFlow.expense(currency)).amount,
                )
            },
            recentTransactions = dashboard.recentTransactions.map { recent ->
                transactions.format(recent.transaction, recent.account, recent.counterAccount, recent.category, today)
            },
        )
    }
}
