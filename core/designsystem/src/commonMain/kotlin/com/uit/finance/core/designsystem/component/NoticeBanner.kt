package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Thông báo trung tính (không phải lỗi): "Tài khoản đã được xoá", "Đã bật khoá ứng dụng". Lỗi dùng [ErrorBanner];
 * cảnh báo cần chú ý đặt [warning] = true (nền màu lỗi nhạt).
 */
@Composable
fun NoticeBanner(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = FinanceIcons.Info,
    warning: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val spacing = FinanceTheme.spacing
    val container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    }
}
