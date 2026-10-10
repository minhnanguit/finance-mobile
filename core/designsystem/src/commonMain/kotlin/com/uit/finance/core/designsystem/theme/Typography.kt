package com.uit.finance.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.uit.finance.core.designsystem.resources.Res
import com.uit.finance.core.designsystem.resources.montserrat_bold
import com.uit.finance.core.designsystem.resources.montserrat_medium
import com.uit.finance.core.designsystem.resources.montserrat_regular
import com.uit.finance.core.designsystem.resources.montserrat_semibold
import org.jetbrains.compose.resources.Font

/** Montserrat (OFL) theo bản thiết kế; file font nằm trong `composeResources/font`. */
@Composable
internal fun montserratFamily(): FontFamily = FontFamily(
    Font(Res.font.montserrat_regular, FontWeight.Normal),
    Font(Res.font.montserrat_medium, FontWeight.Medium),
    Font(Res.font.montserrat_semibold, FontWeight.SemiBold),
    Font(Res.font.montserrat_bold, FontWeight.Bold),
)

/**
 * Thang chữ của app. Số tiền lớn dùng `headline*`, tiêu đề mục `titleLarge`, tên dòng `titleMedium`,
 * nhãn phụ `labelMedium`/`bodySmall`.
 */
internal fun financeTypography(fontFamily: FontFamily): Typography {
    fun style(weight: FontWeight, size: TextUnit, lineHeight: TextUnit, letterSpacing: TextUnit = 0.sp) =
        TextStyle(fontFamily = fontFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = letterSpacing)

    return Typography(
        displaySmall = style(FontWeight.SemiBold, 36.sp, 44.sp, (-0.5).sp),
        headlineLarge = style(FontWeight.SemiBold, 32.sp, 40.sp, (-0.5).sp),
        headlineMedium = style(FontWeight.SemiBold, 28.sp, 36.sp, (-0.25).sp),
        headlineSmall = style(FontWeight.SemiBold, 24.sp, 32.sp),
        titleLarge = style(FontWeight.SemiBold, 20.sp, 28.sp),
        titleMedium = style(FontWeight.SemiBold, 16.sp, 24.sp),
        titleSmall = style(FontWeight.SemiBold, 14.sp, 20.sp),
        bodyLarge = style(FontWeight.Normal, 16.sp, 24.sp),
        bodyMedium = style(FontWeight.Normal, 14.sp, 20.sp),
        bodySmall = style(FontWeight.Normal, 12.sp, 16.sp),
        labelLarge = style(FontWeight.Medium, 14.sp, 20.sp),
        labelMedium = style(FontWeight.Medium, 12.sp, 16.sp),
        labelSmall = style(FontWeight.Medium, 11.sp, 16.sp, 0.3.sp),
    )
}
