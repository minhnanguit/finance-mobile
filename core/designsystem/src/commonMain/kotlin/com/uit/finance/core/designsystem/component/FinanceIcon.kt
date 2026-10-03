package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.uit.finance.core.designsystem.theme.FinanceTheme

enum class FinanceIconType { Home, Transactions, Budget, Profile, Add, Reports, ChevronLeft, ChevronRight }

/** Small, themeable line icons shared by the home screen and primary navigation. */
@Composable
fun FinanceIcon(
    type: FinanceIconType,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val iconSize = FinanceTheme.spacing.lg
    Canvas(modifier = modifier.size(iconSize)) {
        val unit = size.minDimension
        val width = unit * 0.08f
        val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = width, cap = StrokeCap.Round)

        when (type) {
            FinanceIconType.Home -> {
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.14f, size.height * 0.46f)
                        lineTo(size.width * 0.5f, size.height * 0.16f)
                        lineTo(size.width * 0.86f, size.height * 0.46f)
                    },
                    color,
                    style = stroke,
                )
                line(0.23f, 0.43f, 0.23f, 0.84f)
                line(0.77f, 0.43f, 0.77f, 0.84f)
                line(0.23f, 0.84f, 0.77f, 0.84f)
                line(0.43f, 0.84f, 0.43f, 0.61f)
                line(0.57f, 0.84f, 0.57f, 0.61f)
                line(0.43f, 0.61f, 0.57f, 0.61f)
            }
            FinanceIconType.Transactions -> {
                line(0.32f, 0.18f, 0.32f, 0.81f)
                line(0.15f, 0.36f, 0.32f, 0.18f)
                line(0.49f, 0.36f, 0.32f, 0.18f)
                line(0.68f, 0.82f, 0.68f, 0.19f)
                line(0.51f, 0.64f, 0.68f, 0.82f)
                line(0.85f, 0.64f, 0.68f, 0.82f)
            }
            FinanceIconType.Budget -> {
                drawRoundRect(
                    color,
                    topLeft = p(0.14f, 0.18f),
                    size = Size(size.width * 0.72f, size.height * 0.64f),
                    cornerRadius = CornerRadius(unit * 0.1f),
                    style = stroke,
                )
                line(0.28f, 0.64f, 0.28f, 0.51f)
                line(0.5f, 0.64f, 0.5f, 0.36f)
                line(0.72f, 0.64f, 0.72f, 0.44f)
            }
            FinanceIconType.Profile -> {
                drawCircle(color, radius = unit * 0.15f, center = p(0.5f, 0.33f), style = stroke)
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.84f)
                        quadraticBezierTo(size.width * 0.23f, size.height * 0.63f, size.width * 0.5f, size.height * 0.63f)
                        quadraticBezierTo(size.width * 0.77f, size.height * 0.63f, size.width * 0.8f, size.height * 0.84f)
                    },
                    color,
                    style = stroke,
                )
            }
            FinanceIconType.Add -> {
                line(0.5f, 0.18f, 0.5f, 0.82f)
                line(0.18f, 0.5f, 0.82f, 0.5f)
            }
            FinanceIconType.Reports -> {
                line(0.16f, 0.82f, 0.84f, 0.82f)
                line(0.28f, 0.72f, 0.28f, 0.55f)
                line(0.5f, 0.72f, 0.5f, 0.24f)
                line(0.72f, 0.72f, 0.72f, 0.42f)
            }
            FinanceIconType.ChevronRight -> {
                line(0.39f, 0.22f, 0.65f, 0.5f)
                line(0.65f, 0.5f, 0.39f, 0.78f)
            }
            FinanceIconType.ChevronLeft -> {
                line(0.61f, 0.22f, 0.35f, 0.5f)
                line(0.35f, 0.5f, 0.61f, 0.78f)
            }
        }
    }
}
