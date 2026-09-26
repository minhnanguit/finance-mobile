package com.uit.finance.core.sync.di

import com.uit.finance.core.sync.scheduler.BackgroundTaskSyncScheduler
import com.uit.finance.core.sync.scheduler.SyncScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSyncModule(): Module = module {
    single<SyncScheduler> {
        BackgroundTaskSyncScheduler(syncEngine = get(), dispatchers = get(), logger = get()).also { it.registerLaunchHandler() }
    }
}
