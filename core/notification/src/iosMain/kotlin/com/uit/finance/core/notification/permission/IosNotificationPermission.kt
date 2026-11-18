package com.uit.finance.core.notification.permission

import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.NotificationPermissionStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
internal class IosNotificationPermission(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : NotificationPermission {
    override suspend fun status(): NotificationPermissionStatus = suspendCancellableCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val authorizationStatus = settings?.authorizationStatus
            val granted = authorizationStatus == UNAuthorizationStatusAuthorized ||
                authorizationStatus == UNAuthorizationStatusProvisional ||
                authorizationStatus == UNAuthorizationStatusEphemeral
            continuation.resume(if (granted) NotificationPermissionStatus.Granted else NotificationPermissionStatus.Denied)
        }
    }

    override suspend fun request(): NotificationPermissionStatus = suspendCancellableCoroutine { continuation ->
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            continuation.resume(if (granted) NotificationPermissionStatus.Granted else NotificationPermissionStatus.Denied)
        }
    }

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
