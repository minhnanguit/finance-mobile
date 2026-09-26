package com.uit.finance.app

import androidx.compose.runtime.Composable
import com.uit.finance.app.navigation.FinanceNavHost
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Shared root of the UI for Android and iOS. Koin must be started before this composes. */
@Composable
fun App() {
    FinanceTheme {
        FinanceNavHost()
    }
}
