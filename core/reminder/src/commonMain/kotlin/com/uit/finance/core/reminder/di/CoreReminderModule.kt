package com.uit.finance.core.reminder.di

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.reminder.DefaultReminderCoordinator
import com.uit.finance.core.reminder.ReminderCoordinator
import com.uit.finance.core.reminder.data.ReminderUserDataCleaner
import com.uit.finance.core.reminder.data.repository.ReminderSettingsRepositoryImpl
import com.uit.finance.core.reminder.domain.ReminderPlanner
import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import com.uit.finance.core.session.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

val coreReminderModule = module {
    single<ReminderSettingsRepository> { ReminderSettingsRepositoryImpl(get(), get()) }
    single { ReminderPlanner() }
    single { ReminderUserDataCleaner(get()) } bind UserDataCleaner::class
    single<ReminderCoordinator> {
        DefaultReminderCoordinator(
            session = get(),
            settings = get(),
            transactions = get(),
            scheduler = get(),
            planner = get(),
            clock = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
            logger = get<Logger>(),
        )
    }
}
