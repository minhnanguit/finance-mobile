package com.uit.finance.app

import android.app.Application
import com.uit.finance.app.di.initKoin
import com.uit.finance.core.sync.scheduler.SyncScheduler
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.logger.Level

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger(Level.WARNING)
            androidContext(this@FinanceApplication)
            workManagerFactory() // Koin builds SyncWorker; default WorkManager initializer is disabled in the manifest
        }
        get<SyncScheduler>().schedulePeriodic()
    }
}
