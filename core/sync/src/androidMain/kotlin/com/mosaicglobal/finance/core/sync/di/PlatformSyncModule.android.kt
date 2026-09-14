package com.mosaicglobal.finance.core.sync.di

import com.mosaicglobal.finance.core.sync.scheduler.SyncScheduler
import com.mosaicglobal.finance.core.sync.scheduler.SyncWorker
import com.mosaicglobal.finance.core.sync.scheduler.WorkManagerSyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformSyncModule(): Module = module {
    single<SyncScheduler> { WorkManagerSyncScheduler(androidContext()) }
    workerOf(::SyncWorker)
}
