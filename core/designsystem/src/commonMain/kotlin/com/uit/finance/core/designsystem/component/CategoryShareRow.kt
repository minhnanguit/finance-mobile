package com.uit.finance.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Một dòng "danh mục chiếm bao nhiêu" dưới biểu đồ tròn (docs/REPORTS-PLAN.md V3): icon, tên, số tiền đầy đủ, thanh tỷ
 * lệ cùng màu với lát và %. Đây cũng là chú thích của biểu đồ, nên màu thanh = màu lát. Có [onClick] thì hiện ›.
 *
 * @param fraction 0..1, độ dài thanh
 */
@Composable
fun CategoryShareRow(
    name: String,
    amount: String,
    currency: String,
    percentLabel: String,
    fraction: Float,
    color: Color,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = if (onClick != null) FinanceIcons.ChevronRight else null,
) {
    val spacing = FinanceTheme.spacing
    val width by animateFloatAsState(fraction.coerceIn(0f, 1f), FinanceMotion.standard(), label = "shareBar")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = spacing.sm)
            // Dòng không bấm được vẫn đọc thành một cụm "tên, số tiền, %"; dòng bấm được thì clickable đã gộp sẵn.
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, containerColor = iconContainerColor, contentColor = iconColor, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AmountText(
                    amount = amount,
                    currency = currency,
                    style = MaterialTheme.typography.titleSmall,
                    currencyStyle = MaterialTheme.typography.labelSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(width).clip(CircleShape).background(color))
                }
                Text(
                    text = percentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(PERCENT_WIDTH),
                    maxLines = 1,
                )
            }
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}

private val PERCENT_WIDTH = 44.dp
