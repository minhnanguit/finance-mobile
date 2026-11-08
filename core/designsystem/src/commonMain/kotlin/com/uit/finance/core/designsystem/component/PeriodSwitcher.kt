package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * `‹  Tháng 10/2026  ›`: lùi / tới một kỳ. [canGoNext] = false (đang ở kỳ hiện tại) thì nút › mờ và không bấm được,
 * vì không cho xem kỳ tương lai.
 */
@Composable
fun PeriodSwitcher(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(icon = FinanceIcons.ChevronLeft, contentDescription = "Kỳ trước", onClick = onPrevious, size = 40.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        CircleIconButton(
            icon = FinanceIcons.ChevronRight,
            contentDescription = if (canGoNext) "Kỳ sau" else null,
            onClick = { if (canGoNext) onNext() },
            size = 40.dp,
            modifier = Modifier.alpha(if (canGoNext) 1f else DISABLED_ALPHA),
        )
    }
}

/**
 * Vuốt ngang để đổi kỳ (vuốt sang phải = kỳ trước). Chỉ tính khi kéo đủ xa theo chiều ngang, nên cuộn dọc vẫn bình
 * thường. [canGoNext] = false thì vuốt sang trái không làm gì.
 */
@Composable
fun Modifier.swipeToChangePeriod(canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val allowNext by rememberUpdatedState(canGoNext)
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    return this.pointerInput(threshold) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onHorizontalDrag = { change, amount ->
                dragged += amount
                change.consume()
            },
            onDragEnd = {
                when {
                    dragged > threshold -> previous()
                    dragged < -threshold && allowNext -> next()
                }
            },
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
private val SWIPE_THRESHOLD = 64.dp
