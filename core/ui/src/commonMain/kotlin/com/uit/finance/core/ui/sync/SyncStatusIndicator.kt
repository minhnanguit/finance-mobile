package com.uit.finance.core.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.SyncBadgeTone
import com.uit.finance.core.designsystem.component.SyncStatusBadge
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview

/**
 * Badge đồng bộ dùng chung (trang chủ, lịch sử): ưu tiên báo thay đổi bị từ chối, rồi thay đổi kẹt, rồi số
 * thay đổi đang chờ gửi; mọi thứ đã lên server thì ẩn. Chạm: có thay đổi bị từ chối thì mở hộp giải thích
 * (xác nhận là ẩn badge), còn lại thì làm mới ngay.
 */
@Composable
fun SyncStatusIndicator(
    overview: SyncOverview,
    onRefresh: () -> Unit,
    onAcknowledgeIssues: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var explainIssues by rememberSaveable { mutableStateOf(false) }
    val (text, tone) = when {
        overview.rejected > 0 -> "${overview.rejected} thay đổi bị từ chối" to SyncBadgeTone.Warning
        overview.stuck > 0 -> "${overview.stuck} chưa gửi được" to SyncBadgeTone.Warning
        overview.pending > 0 -> "${overview.pending} chưa đồng bộ" to SyncBadgeTone.Pending
        else -> null to SyncBadgeTone.Pending
    }
    SyncStatusBadge(
        text = text,
        tone = tone,
        onClick = { if (overview.rejected > 0) explainIssues = true else onRefresh() },
        modifier = modifier,
    )
    if (explainIssues) {
        ConfirmDialog(
            title = "Thay đổi bị từ chối",
            message = "Máy chủ không nhận ${overview.rejected} thay đổi (thường do dữ liệu đã bị sửa hoặc xoá trên máy khác). " +
                "Sổ trên máy đã được đưa về đúng bản của máy chủ.",
            confirmLabel = "Đã hiểu",
            dismissLabel = "Đóng",
            onConfirm = {
                explainIssues = false
                onAcknowledgeIssues()
            },
            onDismiss = { explainIssues = false },
        )
    }
}

/** Câu báo khi user kéo để làm mới mà vòng sync lỗi. Mất mạng là bình thường với app offline-first. */
fun AppError.toSyncMessage(): UiText = when (this) {
    is AppError.Network -> UiText.Key("sync.offline", "Chưa có mạng. Thay đổi đã lưu trên máy và sẽ tự gửi khi có mạng.")
    AppError.Unauthorized -> UiText.Key("sync.unauthorized", "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để đồng bộ.")
    else -> UiText.Key("sync.failed", "Chưa đồng bộ được, ứng dụng sẽ tự thử lại sau.")
}
