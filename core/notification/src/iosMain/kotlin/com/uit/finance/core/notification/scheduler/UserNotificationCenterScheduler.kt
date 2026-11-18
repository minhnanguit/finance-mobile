package com.uit.finance.core.notification.scheduler

import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
internal class UserNotificationCenterScheduler(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : LocalNotificationScheduler {
    override suspend fun replaceGroup(group: String, notifications: List<LocalNotification>) {
        val existing = pendingIds(group)
        if (existing.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(existing)
        notifications.take(IOS_GROUP_BUDGET).forEach { notification -> add(notification) }
    }

    override suspend fun clearDelivered(group: String) {
        val ids = deliveredIds(group)
        if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private suspend fun pendingIds(group: String): List<String> = suspendCancellableCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            continuation.resume(
                requests.orEmpty().mapNotNull { it as? UNNotificationRequest }
                    .filter { it.content.threadIdentifier == group }
                    .map { it.identifier },
            )
        }
    }

    private suspend fun deliveredIds(group: String): List<String> = suspendCancellableCoroutine { continuation ->
        center.getDeliveredNotificationsWithCompletionHandler { notifications ->
            continuation.resume(
                notifications.orEmpty().mapNotNull { it as? UNNotification }
                    .filter { it.request.content.threadIdentifier == group }
                    .map { it.request.identifier },
            )
        }
    }

    private suspend fun add(notification: LocalNotification) = suspendCancellableCoroutine { continuation ->
        val local = notification.fireAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents().apply {
            setYear(local.year.toLong())
            setMonth(local.monthNumber.toLong())
            setDay(local.dayOfMonth.toLong())
            setHour(local.hour.toLong())
            setMinute(local.minute.toLong())
        }
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setThreadIdentifier(notification.group)
            setUserInfo(mapOf(ACTION_KEY to notification.action))
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(notification.id, content, trigger)
        center.addNotificationRequest(request) { continuation.resume(Unit) }
    }

    private companion object { const val IOS_GROUP_BUDGET = 14 }
}

internal const val ACTION_KEY = "finance.notification.action"
