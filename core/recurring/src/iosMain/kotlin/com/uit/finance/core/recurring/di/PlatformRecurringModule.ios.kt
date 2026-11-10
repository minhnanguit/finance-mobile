package com.uit.finance.core.recurring.di

import com.uit.finance.core.recurring.reminder.IosNotificationPermissionGate
import com.uit.finance.core.recurring.reminder.IosReminderScheduler
import com.uit.finance.core.recurring.reminder.NotificationPermissionGate
import com.uit.finance.core.recurring.reminder.ReminderScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformRecurringModule(): Module = module {
    single<ReminderScheduler> { IosReminderScheduler() }
    single<NotificationPermissionGate> { IosNotificationPermissionGate() }
}

/**
 * iOS không có worker hằng ngày riêng: BGAppRefreshTask của sync (core/sync) đánh thức app định kỳ, coordinator sinh kỳ
 * sau mỗi vòng sync xong; mở app lên foreground luôn bù đủ.
 */
internal actual fun scheduleDailyGeneration(koin: org.koin.core.Koin) = Unit
