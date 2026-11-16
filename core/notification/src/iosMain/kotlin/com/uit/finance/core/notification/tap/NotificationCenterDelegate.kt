package com.uit.finance.core.notification.tap

import com.uit.finance.core.notification.DefaultNotificationTaps
import com.uit.finance.core.notification.scheduler.ACTION_KEY
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.Koin
import platform.darwin.NSObject
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol

@OptIn(ExperimentalForeignApi::class)
internal class NotificationCenterDelegate(private val taps: DefaultNotificationTaps) :
    NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        (didReceiveNotificationResponse.notification.request.content.userInfo[ACTION_KEY] as? String)?.let(taps::onTapped)
        withCompletionHandler()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun installNotificationDelegate(koin: Koin) {
    UNUserNotificationCenter.currentNotificationCenter().delegate = koin.get<NotificationCenterDelegate>()
}
