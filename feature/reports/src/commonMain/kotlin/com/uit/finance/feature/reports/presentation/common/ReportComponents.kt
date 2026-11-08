package com.uit.finance.feature.reports.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.component.CategoryShareRow
import com.uit.finance.core.designsystem.component.DonutChart
import com.uit.finance.core.designsystem.component.DonutSlice
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.CategoryPalette
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors

/** Màu một nhóm: màu danh mục; "Khác" là xám nhạt theo theme (V3). */
@Composable
internal fun shareColor(colorHex: String?, style: ShareStyle): Color = when (style) {
    ShareStyle.Other -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OTHER_ALPHA)
    else -> CategoryPalette.color(colorHex) ?: MaterialTheme.colorScheme.primary
}

/** Biểu đồ tròn, tổng rút gọn ở giữa. */
@Composable
internal fun ReportDonut(
    slices: List<SliceUi>,
    centerValue: String,
    centerLabel: String,
    description: String,
    animationKey: Any,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DonutChart(
            slices = slices.map { DonutSlice(it.weight, shareColor(it.colorHex, it.style)) },
            contentDescription = description,
            animationKey = animationKey,
            modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(centerValue, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1)
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Một dòng danh sách; [expanded] chỉ dùng cho dòng "Khác". */
@Composable
internal fun ReportShareRow(row: ShareRowUi, onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val color = shareColor(row.colorHex, row.style)
    val tile = when (row.style) {
        ShareStyle.Category -> categoryTileColors(row.colorHex, row.kind).let { it.container to it.content }
        ShareStyle.Uncategorized -> CategoryPalette.container(color) to color
        ShareStyle.Other -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (row.style) {
        ShareStyle.Category -> categoryIconOrDefault(row.iconKey, row.kind)
        ShareStyle.Uncategorized -> FinanceIcons.Tag
        ShareStyle.Other -> FinanceIcons.More
    }
    CategoryShareRow(
        name = row.name,
        amount = row.amount,
        currency = row.currency,
        percentLabel = row.percentLabel,
        fraction = row.fraction,
        color = color,
        icon = icon,
        iconContainerColor = tile.first,
        iconColor = tile.second,
        subtitle = row.subtitle,
        onClick = if (row.action == ShareAction.None) null else onClick,
        trailingIcon = when (row.action) {
            ShareAction.None -> null
            ShareAction.ToggleOthers -> if (expanded) FinanceIcons.MoveUp else FinanceIcons.Expand
            else -> FinanceIcons.ChevronRight
        },
        modifier = modifier,
    )
}

private const val OTHER_ALPHA = 0.45f
