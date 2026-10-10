package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.AccountCardColors
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Thẻ ví theo bản thiết kế: nền màu đậm có hoa văn đồng mức, chip loại ví góc trên, tên ví và số dư
 * góc dưới. [balance] đã format sẵn (ViewModel lo), [colors] lấy từ `FinanceTheme.colors.accountCards`.
 * Người gọi quyết định kích thước (ví dụ `aspectRatio(1.6f)` trong pager).
 */
@Composable
fun AccountCard(
    name: String,
    balance: String,
    currency: String,
    colors: AccountCardColors,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    icon: ImageVector? = null,
    patternSeed: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val spacing = FinanceTheme.spacing
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.97f)
            .clip(shape)
            .background(colors.container)
            .contourPattern(color = colors.pattern, seed = patternSeed)
            .then(clickModifier)
            .padding(spacing.lg),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (typeLabel != null) {
                        LabelChip(typeLabel, containerColor = colors.chip, contentColor = colors.content)
                    }
                    Spacer(Modifier.weight(1f))
                    if (icon != null) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(spacing.xxs))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = balance,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.content.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = spacing.xs),
                    )
                }
            }
        }
    }
}
