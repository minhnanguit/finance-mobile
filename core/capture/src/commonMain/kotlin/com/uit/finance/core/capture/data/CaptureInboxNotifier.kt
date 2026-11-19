@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.data

import com.uit.finance.core.capture.domain.model.CaptureNotifications
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Thông báo "Có N giao dịch cần xác nhận" (S6, B13): **không có số tiền**, vì màn hình khoá ai cũng thấy. Một thông
 * báo duy nhất trong nhóm [GROUP], hiện ngay; N = 0 thì gỡ.
 */
internal class CaptureInboxNotifier(
    private val scheduler: LocalNotificationScheduler,
    private val pendingCount: () -> Flow<Int>,
    private val clock: Clock,
) {
    suspend fun refresh() {
        val count = pendingCount().first()
        if (count <= 0) {
            clear()
            return
        }
        scheduler.replaceGroup(
            GROUP,
            listOf(
                LocalNotification(
                    id = NOTIFICATION_ID,
                    group = GROUP,
                    fireAt = clock.now(),
                    title = TITLE,
                    body = "Có $count giao dịch cần xác nhận",
                    action = ACTION_OPEN_INBOX,
                ),
            ),
        )
    }

    /** Mở màn Chờ xác nhận thì gỡ thông báo đang hiện. */
    suspend fun clear() {
        scheduler.replaceGroup(GROUP, emptyList())
        scheduler.clearDelivered(GROUP)
    }

    companion object {
        const val GROUP = "capture"
        const val NOTIFICATION_ID = "capture.inbox"
        const val TITLE = "Giao dịch chờ xác nhận"

        const val ACTION_OPEN_INBOX = CaptureNotifications.ACTION_OPEN_INBOX
    }
}
