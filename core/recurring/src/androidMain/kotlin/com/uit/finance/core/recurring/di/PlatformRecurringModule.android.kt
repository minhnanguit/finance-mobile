package com.uit.finance.core.recurring.di

import com.uit.finance.core.recurring.reminder.RecurringGenerationWorker
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** Thông báo đi qua core/notification; ở đây chỉ còn worker sinh kỳ hằng ngày. */
internal actual fun platformRecurringModule(): Module = module {
    workerOf(::RecurringGenerationWorker)
}

internal actual fun scheduleDailyGeneration(koin: org.koin.core.Koin) {
    RecurringGenerationWorker.schedule(koin.get<android.content.Context>())
}
