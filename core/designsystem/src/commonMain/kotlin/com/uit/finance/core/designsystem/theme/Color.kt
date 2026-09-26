package com.uit.finance.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette: deep teal primary, warm amber secondary, semantic income/expense colours.
private val Teal10 = Color(0xFF002020)
private val Teal20 = Color(0xFF003737)
private val Teal40 = Color(0xFF006A6A)
private val Teal80 = Color(0xFF4CDADA)
private val Teal90 = Color(0xFF6FF7F6)
private val Amber40 = Color(0xFF8A5100)
private val Amber80 = Color(0xFFFFB86C)
private val Amber90 = Color(0xFFFFDCBE)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber90,
    onSecondaryContainer = Color(0xFF2C1600),
    error = Red40,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Teal90,
    secondary = Amber80,
    onSecondary = Color(0xFF482900),
    secondaryContainer = Color(0xFF673D00),
    onSecondaryContainer = Amber90,
    error = Red80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
)

/** Semantic colours that Material3 does not model. */
data class FinanceColors(
    val income: Color,
    val expense: Color,
)

internal val LightFinanceColors = FinanceColors(income = Color(0xFF1B7F3B), expense = Red40)
internal val DarkFinanceColors = FinanceColors(income = Color(0xFF7EDB9A), expense = Red80)
