package com.uit.finance.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Chấm trang của pager; chấm đang chọn kéo dài thành thanh (chuyển động mượt khi vuốt). */
@Composable
fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 20.dp else 6.dp,
                animationSpec = FinanceMotion.standard(),
                label = "indicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) activeColor else inactiveColor,
                animationSpec = FinanceMotion.standard(),
                label = "indicatorColor",
            )
            Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
        }
    }
}
