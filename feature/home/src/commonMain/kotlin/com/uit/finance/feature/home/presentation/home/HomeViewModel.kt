package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.presentation.mvi.MviViewModel

internal class HomeViewModel : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    override fun onIntent(intent: HomeIntent) {
        val target = when (intent) {
            HomeIntent.AddTransaction -> HomeTarget.AddTransaction
            HomeIntent.OpenTransactions -> HomeTarget.Transactions
            HomeIntent.OpenWallets -> HomeTarget.Wallets
            HomeIntent.OpenBudgets -> HomeTarget.Budgets
            HomeIntent.OpenReports -> HomeTarget.Reports
            HomeIntent.OpenBills -> HomeTarget.Bills
        }
        sendEffect(HomeEffect.Navigate(target))
    }
}
