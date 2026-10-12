package com.uit.finance.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Mức độ của badge: chỉ đang chờ gửi (nhẹ) hay có thay đổi bị từ chối / kẹt (cần chú ý). */
enum class SyncBadgeTone { Pending, Warning }

/**
 * Viên nhỏ "3 chưa đồng bộ" cạnh tiêu đề. [text] `null` là ẩn (mọi thứ đã lên server); hiện/ẩn có hiệu ứng.
 * Chạm để làm mới hoặc xem chi tiết, tuỳ màn hình.
 */
@Composable
fun SyncStatusBadge(
    text: String?,
    tone: SyncBadgeTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(FinanceMotion.standard()) + scaleIn(FinanceMotion.standard()),
        exit = fadeOut(FinanceMotion.standard()) + scaleOut(FinanceMotion.standard()),
        modifier = modifier,
    ) {
        val container = when (tone) {
            SyncBadgeTone.Pending -> MaterialTheme.colorScheme.surfaceContainer
            SyncBadgeTone.Warning -> MaterialTheme.colorScheme.errorContainer
        }
        val content = when (tone) {
            SyncBadgeTone.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
            SyncBadgeTone.Warning -> MaterialTheme.colorScheme.onErrorContainer
        }
        val spacing = FinanceTheme.spacing
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(container)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.sm + spacing.xs, vertical = spacing.xs + spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FinanceIcons.Offline, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(text.orEmpty(), style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}
