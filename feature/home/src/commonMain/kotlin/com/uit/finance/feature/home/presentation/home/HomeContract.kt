package com.uit.finance.feature.home.presentation.home

/** Until finance endpoints exist, the home screen deliberately shows empty states. */
data class HomeState(
    val isLoading: Boolean = false,
)

sealed interface HomeIntent {
    data object AddTransaction : HomeIntent
    data object OpenTransactions : HomeIntent
    data object OpenWallets : HomeIntent
    data object OpenBudgets : HomeIntent
    data object OpenReports : HomeIntent
    data object OpenBills : HomeIntent
}

sealed interface HomeEffect {
    data class Navigate(val target: HomeTarget) : HomeEffect
}

enum class HomeTarget { AddTransaction, Transactions, Wallets, Budgets, Reports, Bills }
