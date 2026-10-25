package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Compact gradient brand mark used beside the Finance wordmark. */
@Composable
fun FinanceLogo(modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val radius = size.minDimension * 0.22f
        val brush = Brush.linearGradient(
            colors = listOf(colors.logoAqua, colors.logoMint),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        listOf(
            Offset(size.width * 0.50f, size.height * 0.28f),
            Offset(size.width * 0.30f, size.height * 0.62f),
            Offset(size.width * 0.70f, size.height * 0.62f),
        ).forEach { center ->
            drawCircle(brush = brush, radius = radius, center = center, style = Stroke(stroke))
        }
    }
}
