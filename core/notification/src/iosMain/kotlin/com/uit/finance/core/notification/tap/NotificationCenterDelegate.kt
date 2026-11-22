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

/**
 * Koin không đăng ký trực tiếp được class Kotlin kế thừa NSObject: Kotlin/Native chưa hỗ trợ KClass của chúng
 * ("KClass for Kotlin subclasses of Objective-C classes is not supported yet") ⇒ app crash lúc startKoin.
 * Holder là class Kotlin thường, sống suốt app trong Koin và giữ tham chiếu mạnh tới delegate
 * (UNUserNotificationCenter.delegate là weak).
 */
internal class NotificationDelegateHolder(taps: DefaultNotificationTaps) {
    val delegate: NotificationCenterDelegate = NotificationCenterDelegate(taps)
}

@OptIn(ExperimentalForeignApi::class)
fun installNotificationDelegate(koin: Koin) {
    UNUserNotificationCenter.currentNotificationCenter().delegate = koin.get<NotificationDelegateHolder>().delegate
}
