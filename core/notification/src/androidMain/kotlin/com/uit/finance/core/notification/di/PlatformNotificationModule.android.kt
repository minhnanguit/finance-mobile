package com.uit.finance.core.notification.di

import com.uit.finance.core.notification.LocalNotificationScheduler
import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.permission.AndroidNotificationPermission
import com.uit.finance.core.notification.scheduler.WorkManagerNotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformNotificationModule(): Module = module {
    single<LocalNotificationScheduler> { WorkManagerNotificationScheduler(androidContext(), get(), get()) }
    single<NotificationPermission> { AndroidNotificationPermission(androidContext()) }
}
