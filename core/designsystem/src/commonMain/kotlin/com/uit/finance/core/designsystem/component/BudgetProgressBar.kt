package com.uit.finance.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Ba mức của thanh tiến độ: bình thường, sắp chạm giới hạn, đã vượt. Ai dùng thì tự quyết ngưỡng. */
enum class ProgressTone { Normal, Warning, Over }

/**
 * Thanh tiến độ ngân sách: nền mờ + phần đã dùng tô theo [tone] (`primary` / `FinanceTheme.colors.warning` /
 * `colorScheme.error`). [progress] được kẹp vào 0..1, nên vượt 100% thì thanh đầy và màu báo vượt.
 */
@Composable
fun BudgetProgressBar(
    progress: Float,
    tone: ProgressTone,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val fill by animateFloatAsState(clamped, FinanceMotion.standard(), label = "budget-progress")
    val color by animateColorAsState(tone.color(), FinanceMotion.standard(), label = "budget-tone")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FinanceTheme.spacing.sm)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
    }
}

@Composable
private fun ProgressTone.color(): Color = when (this) {
    ProgressTone.Normal -> MaterialTheme.colorScheme.primary
    ProgressTone.Warning -> FinanceTheme.colors.warning
    ProgressTone.Over -> MaterialTheme.colorScheme.error
}
