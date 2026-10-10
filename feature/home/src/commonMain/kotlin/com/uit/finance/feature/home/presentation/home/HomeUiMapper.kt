package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.text.toLongLabel
import com.uit.finance.core.presentation.text.toRelativeLabel
import com.uit.finance.feature.home.domain.model.HomeDashboard
import com.uit.finance.feature.home.domain.model.RecentTransaction
import kotlinx.datetime.LocalDate

/** Domain → chuỗi hiển thị. Tách khỏi ViewModel để test riêng và để Screen không phải format gì. */
internal class HomeUiMapper(
    private val money: MoneyFormatter,
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
            recentTransactions = dashboard.recentTransactions.map { it.toRow(today) },
        )
    }

    private fun RecentTransaction.toRow(today: LocalDate): TransactionRowUi {
        val typeLabel = transaction.type.label()
        val categoryName = category?.name
        val title = transaction.payee?.takeIf { it.isNotBlank() } ?: categoryName ?: typeLabel
        val subtitle = listOfNotNull(
            categoryName?.takeIf { it != title },
            account?.name,
            transaction.occurredOn.toRelativeLabel(today),
        ).joinToString(SUBTITLE_SEPARATOR)
        val formatted = money.format(transaction.amount)
        return TransactionRowUi(
            id = transaction.id,
            type = transaction.type,
            title = title,
            subtitle = subtitle,
            currency = formatted.currency,
            amount = formatted.amount,
            sign = transaction.type.sign(),
            iconKey = category?.icon,
        )
    }

    private fun TransactionType.label(): String = when (this) {
        TransactionType.INCOME -> "Thu nhập"
        TransactionType.EXPENSE -> "Chi tiêu"
        TransactionType.TRANSFER -> "Chuyển tiền"
    }

    private fun TransactionType.sign(): String = when (this) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "-"
        TransactionType.TRANSFER -> ""
    }

    private companion object {
        const val SUBTITLE_SEPARATOR = " · "
    }
}
