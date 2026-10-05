package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.presentation.mvi.MviViewModel

internal class HomeViewModel : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    override fun onIntent(intent: HomeIntent) = when (intent) {
        is HomeIntent.Open -> sendEffect(HomeEffect.Navigate(intent.target))
    }
}
