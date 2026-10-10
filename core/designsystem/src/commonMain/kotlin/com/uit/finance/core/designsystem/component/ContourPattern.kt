package com.uit.finance.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hoa văn "đường đồng mức" (topographic) mờ cho thẻ ví, vẽ bằng Canvas nên không cần ảnh và theo được
 * mọi màu nền. [seed] cho mỗi thẻ một hoa văn khác nhau nhưng ổn định giữa các lần recompose.
 * Đặt sau `clip(shape)` để phần vượt ra ngoài thẻ bị cắt.
 */
fun Modifier.contourPattern(
    color: Color,
    seed: Int = 0,
    rings: Int = 11,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val paths = buildContours(size, seed, rings)
    val stroke = Stroke(width = strokeWidth.toPx())
    onDrawBehind {
        paths.forEach { drawPath(path = it, color = color, style = stroke) }
    }
}

private const val STEPS = 96
private const val TWO_PI = (2 * PI).toFloat()

private fun buildContours(size: Size, seed: Int, rings: Int): List<Path> {
    val random = Random(seed)
    val centerX = size.width * (0.6f + random.nextFloat() * 0.3f)
    val centerY = size.height * (0.25f + random.nextFloat() * 0.5f)
    val phases = FloatArray(3) { random.nextFloat() * TWO_PI }
    val amplitudes = floatArrayOf(
        0.10f + random.nextFloat() * 0.06f,
        0.05f + random.nextFloat() * 0.04f,
        0.02f + random.nextFloat() * 0.02f,
    )
    val maxRadius = max(size.width, size.height) * 0.95f

    return List(rings) { ring ->
        val base = maxRadius * (ring + 1) / rings
        Path().apply {
            for (step in 0..STEPS) {
                val theta = TWO_PI * step / STEPS
                val wobble = 1f +
                    amplitudes[0] * sin(3 * theta + phases[0]) +
                    amplitudes[1] * sin(5 * theta + phases[1]) +
                    amplitudes[2] * sin(8 * theta + phases[2])
                val radius = base * wobble
                val x = centerX + radius * cos(theta)
                val y = centerY + radius * sin(theta)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}
