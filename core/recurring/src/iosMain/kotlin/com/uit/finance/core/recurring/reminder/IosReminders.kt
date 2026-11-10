@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.reminder

import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Hẹn thông báo bằng `UNUserNotificationCenter` theo giờ lịch của máy. iOS giới hạn 64 thông báo chờ cho cả app; feature
 * này chỉ hẹn tối đa [ReminderPlanner.MAX_PENDING]. App chỉ dùng thông báo cục bộ cho khoản định kỳ, nên thay toàn bộ.
 */
internal class IosReminderScheduler : ReminderScheduler {

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun replaceAll(reminders: List<ReminderNotification>) {
        center.removeAllPendingNotificationRequests()
        val zone = TimeZone.currentSystemDefault()
        reminders.forEach { reminder ->
            val local = reminder.fireAt.toLocalDateTime(zone)
            val components = NSDateComponents().apply {
                year = local.year.toLong()
                month = (local.month.ordinal + 1).toLong()
                day = local.day.toLong()
                hour = local.hour.toLong()
                minute = local.minute.toLong()
            }
            val content = UNMutableNotificationContent().apply {
                setTitle(ReminderPlanner.TITLE)
                setBody(ReminderPlanner.body(reminder))
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger),
                withCompletionHandler = null,
            )
        }
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}

internal class IosNotificationPermissionGate : NotificationPermissionGate {

    private var granted = false

    override fun isGranted(): Boolean = granted

    override suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            if (status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional) {
                granted = true
                if (continuation.isActive) continuation.resume(true)
            } else {
                center.requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
                ) { ok, _ ->
                    granted = ok
                    if (continuation.isActive) continuation.resume(ok)
                }
            }
        }
    }
}
