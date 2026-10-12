package com.uit.finance.core.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Ô số liệu nhỏ (thu/chi tháng này, còn lại của ngân sách…): hàng trên icon tint theo chiều tiền + nhãn,
 * hàng dưới số tiền chiếm trọn bề ngang để số dài (chục triệu đồng) vẫn đọc được khi hai ô đứng cạnh nhau.
 */
@Composable
fun StatTile(
    label: String,
    amount: String,
    currency: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize(FinanceMotion.standard())
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = icon,
                containerColor = iconContainerColor,
                contentColor = iconColor,
                size = 32.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AmountText(
            amount = amount,
            currency = currency,
            amountColor = amountColor,
            style = MaterialTheme.typography.titleMedium,
            currencyStyle = MaterialTheme.typography.labelSmall,
        )
    }
}
