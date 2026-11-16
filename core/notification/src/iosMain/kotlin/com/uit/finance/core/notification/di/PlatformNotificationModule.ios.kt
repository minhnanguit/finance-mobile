package com.uit.finance.core.notification.di

import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.permission.IosNotificationPermission
import com.uit.finance.core.notification.scheduler.UserNotificationCenterScheduler
import com.uit.finance.core.notification.tap.NotificationCenterDelegate
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformNotificationModule(): Module = module {
    single<LocalNotificationScheduler> { UserNotificationCenterScheduler() }
    single<NotificationPermission> { IosNotificationPermission() }
    single { NotificationCenterDelegate(get()) }
}
