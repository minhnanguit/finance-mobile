package com.uit.finance.core.notification.di

import com.uit.finance.core.notification.DefaultNotificationTaps
import com.uit.finance.core.notification.NotificationTaps
import org.koin.core.module.Module
import org.koin.dsl.module

internal expect fun platformNotificationModule(): Module

val coreNotificationModule: Module = module {
    single { DefaultNotificationTaps() }
    single<NotificationTaps> { get<DefaultNotificationTaps>() }
    includes(platformNotificationModule())
}
