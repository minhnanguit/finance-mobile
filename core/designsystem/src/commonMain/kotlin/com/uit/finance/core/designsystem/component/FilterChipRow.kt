package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Một điều kiện đang bật. [key] để nơi gọi biết chip nào bị gỡ, và để LazyRow giữ đúng vị trí. */
data class FilterChipItem(val key: String, val label: String)

/**
 * Hàng chip các điều kiện lọc đang bật, cuộn ngang: mỗi chip có ✕ để gỡ riêng điều kiện đó, cuối hàng có
 * "Xoá lọc" để gỡ hết. Không có chip nào thì không vẽ gì.
 */
@Composable
fun FilterChipRow(
    chips: List<FilterChipItem>,
    onRemove: (key: String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (chips.isEmpty()) return
    val spacing = FinanceTheme.spacing
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.key }) { chip ->
            RemovableChip(label = chip.label, onRemove = { onRemove(chip.key) }, modifier = Modifier.animateItem())
        }
        item(key = "clear-all") {
            TextButton(onClick = onClearAll) { Text("Xoá lọc") }
        }
    }
}

/** Chip của [LabelChip] cộng nút ✕; chạm vào đâu trên chip cũng là gỡ. */
@Composable
fun RemovableChip(label: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClickLabel = "Gỡ điều kiện $label", role = Role.Button, onClick = onRemove)
            .padding(start = spacing.sm + spacing.xxs, end = spacing.sm, top = spacing.xs + spacing.xxs, bottom = spacing.xs + spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = FinanceIcons.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(CHIP_ICON_SIZE),
        )
    }
}

/**
 * Chip bật/tắt trong sheet lọc (loại, ví, trạng thái): đang chọn thì nền màu nhấn. Dùng được cho chọn nhiều lẫn chọn
 * một; nơi gọi quyết định [selected].
 */
@Composable
fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    )
}

private val CHIP_ICON_SIZE: Dp = 16.dp
