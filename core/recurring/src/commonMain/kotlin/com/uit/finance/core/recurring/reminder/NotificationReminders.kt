package com.uit.finance.core.recurring.reminder

import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.NotificationPermissionStatus

/**
 * Nhắc khoản định kỳ qua `core/notification` (module duy nhất được đụng API thông báo của nền tảng). Nội dung vẫn chỉ
 * có tên + ngày (R15); nhóm riêng [ReminderPlanner.GROUP] nên không đụng thông báo của tính năng khác.
 */
internal class NotificationReminderScheduler(private val scheduler: LocalNotificationScheduler) : ReminderScheduler {

    override suspend fun replaceAll(reminders: List<ReminderNotification>) {
        scheduler.replaceGroup(
            ReminderPlanner.GROUP,
            reminders.map { reminder ->
                LocalNotification(
                    id = reminder.id,
                    group = ReminderPlanner.GROUP,
                    fireAt = reminder.fireAt,
                    title = ReminderPlanner.TITLE,
                    body = ReminderPlanner.body(reminder),
                    action = ReminderPlanner.ACTION_OPEN_RECURRING,
                )
            },
        )
    }

    override suspend fun cancelAll() {
        scheduler.replaceGroup(ReminderPlanner.GROUP, emptyList())
        scheduler.clearDelivered(ReminderPlanner.GROUP)
    }
}

internal class NotificationPermissionGateAdapter(private val permission: NotificationPermission) :
    NotificationPermissionGate {

    override suspend fun isGranted(): Boolean = permission.status() == NotificationPermissionStatus.Granted

    override suspend fun request(): Boolean = permission.request() == NotificationPermissionStatus.Granted
}
