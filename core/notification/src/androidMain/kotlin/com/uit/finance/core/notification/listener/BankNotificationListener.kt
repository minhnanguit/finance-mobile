@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.notification.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.uit.finance.core.notification.NotificationIngress
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.koin.core.component.KoinComponent

/**
 * Nhận thông báo của app khác khi user đã cấp quyền đọc thông báo (plan L1–L5). Không tự hiểu nội dung: chỉ lọc rồi
 * giao chuỗi cho [NotificationIngress].
 *
 * **Dòng đầu tiên** là kiểm package (L2): thông báo của app không được chọn thì không đọc `extras`.
 */
class BankNotificationListener : NotificationListenerService(), KoinComponent {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ingress = getKoin().getOrNull<NotificationIngress>() ?: return
        val packageName = sbn.packageName
        if (!ingress.accepts(packageName)) return

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.isOngoing) return // L3

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!ingress.acceptsSender(packageName, title)) return // L4: SMS chỉ đọc tiếp khi đúng người gửi

        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return
        // `when` ổn định khi app ngân hàng cập nhật lại thông báo; `postTime` đổi mỗi lần (L5).
        val postedAt = notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        ingress.onPosted(packageName, title, text, Instant.fromEpochMilliseconds(postedAt))
    }
}
