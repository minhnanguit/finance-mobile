package com.uit.finance.core.recurring.di

import com.uit.finance.core.recurring.reminder.AndroidNotificationPermissionGate
import com.uit.finance.core.recurring.reminder.AndroidReminderScheduler
import com.uit.finance.core.recurring.reminder.NotificationPermissionGate
import com.uit.finance.core.recurring.reminder.RecurringGenerationWorker
import com.uit.finance.core.recurring.reminder.ReminderScheduler
import com.uit.finance.core.recurring.reminder.ReminderWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual fun platformRecurringModule(): Module = module {
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext(), get()) }
    single { AndroidNotificationPermissionGate(androidContext()) } bind NotificationPermissionGate::class
    workerOf(::ReminderWorker)
    workerOf(::RecurringGenerationWorker)
}

internal actual fun scheduleDailyGeneration(koin: org.koin.core.Koin) {
    RecurringGenerationWorker.schedule(koin.get<android.content.Context>())
}
