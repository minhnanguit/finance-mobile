package com.mosaicglobal.finance.app

import androidx.compose.runtime.Composable
import com.mosaicglobal.finance.app.navigation.FinanceNavHost
import com.mosaicglobal.finance.core.designsystem.theme.FinanceTheme

/** Shared root of the UI for Android and iOS. Koin must be started before this composes. */
@Composable
fun App() {
    FinanceTheme {
        FinanceNavHost()
    }
}
