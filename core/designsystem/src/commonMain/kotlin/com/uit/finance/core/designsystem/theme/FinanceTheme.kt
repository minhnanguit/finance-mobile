package com.uit.finance.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

@Composable
fun FinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
    CompositionLocalProvider(
        LocalFinanceColors provides financeColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinanceTypography,
            content = content,
        )
    }
}

object FinanceTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val colors: FinanceColors
        @Composable @ReadOnlyComposable get() = LocalFinanceColors.current
}
