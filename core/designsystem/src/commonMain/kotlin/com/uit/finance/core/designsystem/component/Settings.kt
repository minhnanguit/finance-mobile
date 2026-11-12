package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Một nhóm cài đặt: tiêu đề nhỏ phía trên và khối bo góc chứa các dòng [SettingsItem].
 * Phẳng, không elevation, cùng nhịp với thẻ ở các màn khác.
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.xs).semantics { heading() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = spacing.xs),
            content = content,
        )
    }
}

/**
 * Một dòng cài đặt: ô icon, tiêu đề + mô tả, phần cuối tuỳ chọn ([SettingsChevron], [SettingsExternalMark]). [destructive] tô icon và tiêu đề màu lỗi (xoá tài khoản).
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Dòng không bấm được vẫn đọc thành một mục ("Phiên bản, 0.1.0"), không phải từng chữ rời.
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    SettingsRow(icon, title, subtitle, enabled, destructive, modifier.then(clickModifier), trailing)
}

@Composable
fun SettingsChevron() {
    Icon(FinanceIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Dòng mở trang ngoài app (trình duyệt). */
@Composable
fun SettingsExternalMark() {
    Icon(
        FinanceIcons.ExternalLink,
        contentDescription = "Mở trong trình duyệt",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    destructive: Boolean,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
) {
    val spacing = FinanceTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = icon,
            containerColor = if (destructive) colors.errorContainer else colors.surfaceContainer,
            contentColor = if (destructive) colors.onErrorContainer else colors.onSurface,
            size = 40.dp,
            shape = MaterialTheme.shapes.small,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) colors.error else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

private const val DISABLED_ALPHA = 0.5f
