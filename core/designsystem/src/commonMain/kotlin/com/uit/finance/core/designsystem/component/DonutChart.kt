package com.uit.finance.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.FinanceMotion

/** Một lát của [DonutChart]. [weight] là số tương đối (số tiền), chart tự chia tỷ lệ. */
@Immutable
data class DonutSlice(val weight: Double, val color: Color)

/**
 * Biểu đồ vành khuyên (docs/REPORTS-PLAN.md L1, V3): vẽ bằng Canvas, lát nối nhau theo chiều kim đồng hồ từ 12 giờ,
 * có khe nhỏ giữa các lát. Không chú thích, không nhãn trên lát: danh sách bên dưới làm chú thích. [center] là nội dung
 * ở giữa (thường là tổng).
 *
 * Animation chỉ chạy khi [animationKey] đổi (mở màn, đổi kỳ: V7). Số đổi vì sync về thì vẽ ngay giá trị mới, không
 * chạy lại từ đầu. Screen reader đọc [contentDescription] (V8).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    animationKey: Any? = Unit,
    thickness: Dp = 22.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, FinanceMotion.emphasized())
    }
    val total = slices.sumOf { it.weight.coerceAtLeast(0.0) }
    val visible = slices.filter { it.weight > 0.0 }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = thickness.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth)
            drawArc(trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (total <= 0.0) return@Canvas

            // Khe giữa các lát: chỉ khi có từ 2 lát, và không lấn mất lát quá nhỏ.
            val gap = if (visible.size > 1) GAP_DEGREES else 0f
            var start = START_ANGLE
            val sweepTotal = FULL_CIRCLE * progress.value
            visible.forEach { slice ->
                val sweep = (slice.weight / total).toFloat() * sweepTotal
                val drawn = (sweep - gap).coerceAtLeast(MIN_SLICE_DEGREES.coerceAtMost(sweep))
                drawArc(slice.color, startAngle = start, sweepAngle = drawn, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                start += sweep
            }
        }
        Box(Modifier.padding(thickness + 8.dp), contentAlignment = Alignment.Center, content = center)
    }
}

private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f
private const val GAP_DEGREES = 2f
private const val MIN_SLICE_DEGREES = 1f
