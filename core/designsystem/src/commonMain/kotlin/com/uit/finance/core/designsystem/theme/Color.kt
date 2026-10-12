package com.uit.finance.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Palette theo bản thiết kế tham chiếu (Purrweb "Finance Mobile iOS App", dribbble.com/shots/21793126):
 * xanh sage #94C1B2 (tiền), cam đào #FFC789 (đồng xu vàng), nền tối #121212. Mọi màu của app khai báo ở
 * đây; màn hình chỉ dùng `MaterialTheme.colorScheme.*` và `FinanceTheme.colors.*`, không hardcode.
 */
private object Palette {
    val Sage = Color(0xFF94C1B2)
    val SageDeep = Color(0xFF4A7D6D) // text/icon xanh trên nền sáng (đạt 4.5:1 trên trắng)
    val SageInk = Color(0xFF1C3730) // chữ trên thẻ sage
    val SageTint = Color(0xFFE4F0EA)
    val SageTintDark = Color(0xFF203A33)

    val Peach = Color(0xFFFFC789)
    val PeachDeep = Color(0xFFAD5B0B) // link "Xem tất cả" trên nền sáng: 4.9:1 trên trắng (WCAG AA chữ thường ≥ 4.5)
    val PeachInk = Color(0xFF3D2300)
    val PeachTint = Color(0xFFFFEFDB)
    val PeachTintDark = Color(0xFF3F2E1B)

    val Rose = Color(0xFFF2B8C6)
    val RoseInk = Color(0xFF4A1F2B)

    val Ink = Color(0xFF121212)
    val Ink2 = Color(0xFF1C1C1C)
    val Ink3 = Color(0xFF272727)
    val Ink4 = Color(0xFF333333)

    val White = Color(0xFFFFFFFF)
    val Mist = Color(0xFFF4F4F4)
    val Mist2 = Color(0xFFE6E6E6)
    val Graphite = Color(0xFF6B6B6B)
    val Silver = Color(0xFF9E9E9E)

    val Green = Color(0xFF1B7F4F) // số tiền thu trên nền sáng: 5.0:1 trên trắng, 4.6:1 trên nền ô (F4F4F4)
    val GreenBright = Color(0xFF6FD39F)
    val Red = Color(0xFFD64545)
    val RedBright = Color(0xFFFF8A80)
}

internal val LightColors = lightColorScheme(
    primary = Palette.SageDeep,
    onPrimary = Palette.White,
    primaryContainer = Palette.Sage,
    onPrimaryContainer = Palette.SageInk,
    secondary = Palette.PeachDeep,
    onSecondary = Palette.White,
    secondaryContainer = Palette.Peach,
    onSecondaryContainer = Palette.PeachInk,
    tertiary = Palette.Graphite,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.Mist,
    onTertiaryContainer = Palette.Ink,
    error = Palette.Red,
    onError = Palette.White,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF5A1414),
    background = Palette.White,
    onBackground = Palette.Ink,
    surface = Palette.White,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Mist,
    onSurfaceVariant = Palette.Graphite,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Palette.Mist,
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Palette.Mist2,
    outline = Palette.Mist2,
    outlineVariant = Palette.Mist,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.White,
)

internal val DarkColors = darkColorScheme(
    primary = Palette.Sage,
    onPrimary = Palette.SageInk,
    primaryContainer = Palette.Sage,
    onPrimaryContainer = Palette.SageInk,
    secondary = Palette.Peach,
    onSecondary = Palette.PeachInk,
    secondaryContainer = Palette.Peach,
    onSecondaryContainer = Palette.PeachInk,
    tertiary = Palette.Silver,
    onTertiary = Palette.Ink,
    tertiaryContainer = Palette.Ink3,
    onTertiaryContainer = Palette.White,
    error = Palette.RedBright,
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFD9D6),
    background = Palette.Ink,
    onBackground = Palette.White,
    surface = Palette.Ink,
    onSurface = Palette.White,
    surfaceVariant = Palette.Ink3,
    onSurfaceVariant = Palette.Silver,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Palette.Ink2,
    surfaceContainerHigh = Palette.Ink3,
    surfaceContainerHighest = Palette.Ink4,
    outline = Palette.Ink3,
    outlineVariant = Palette.Ink2,
    inverseSurface = Palette.White,
    inverseOnSurface = Palette.Ink,
)

/** Màu của một thẻ ví: nền, chữ, nét hoa văn và chip. Xoay vòng theo [FinanceColors.accountCards]. */
@Immutable
data class AccountCardColors(
    val container: Color,
    val content: Color,
    val pattern: Color,
    val chip: Color,
)

/** Màu ngữ nghĩa mà Material3 không có: chiều tiền và nền mờ cho ô icon theo chiều tiền. */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val incomeContainer: Color,
    val expenseContainer: Color,
    val transferContainer: Color,
    /** Thẻ ví thứ i dùng `accountCards[i % size]`. */
    val accountCards: List<AccountCardColors>,
)

private fun card(container: Color, content: Color, patternAlpha: Float, chipAlpha: Float) = AccountCardColors(
    container = container,
    content = content,
    pattern = content.copy(alpha = patternAlpha),
    chip = content.copy(alpha = chipAlpha),
)

internal val LightFinanceColors = FinanceColors(
    income = Palette.Green,
    expense = Palette.Red,
    transfer = Palette.SageDeep,
    incomeContainer = Palette.SageTint,
    expenseContainer = Palette.PeachTint,
    transferContainer = Palette.Mist,
    accountCards = listOf(
        card(Palette.Sage, Palette.SageInk, patternAlpha = 0.18f, chipAlpha = 0.14f),
        card(Palette.Peach, Palette.PeachInk, patternAlpha = 0.16f, chipAlpha = 0.12f),
        card(Palette.Ink, Palette.White, patternAlpha = 0.14f, chipAlpha = 0.14f),
        card(Palette.Rose, Palette.RoseInk, patternAlpha = 0.16f, chipAlpha = 0.12f),
    ),
)

internal val DarkFinanceColors = FinanceColors(
    income = Palette.GreenBright,
    expense = Palette.RedBright,
    transfer = Palette.Sage,
    incomeContainer = Palette.SageTintDark,
    expenseContainer = Palette.PeachTintDark,
    transferContainer = Palette.Ink3,
    accountCards = listOf(
        card(Palette.Sage, Palette.SageInk, patternAlpha = 0.18f, chipAlpha = 0.14f),
        card(Palette.Peach, Palette.PeachInk, patternAlpha = 0.16f, chipAlpha = 0.12f),
        card(Palette.Ink4, Palette.White, patternAlpha = 0.12f, chipAlpha = 0.12f),
        card(Palette.Rose, Palette.RoseInk, patternAlpha = 0.16f, chipAlpha = 0.12f),
    ),
)
