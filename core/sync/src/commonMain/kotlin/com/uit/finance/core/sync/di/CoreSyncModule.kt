package com.uit.finance.core.sync.di

import com.uit.finance.core.datastore.settings.AppSettings
import com.uit.finance.core.sync.engine.DefaultSyncEngine
import com.uit.finance.core.sync.engine.DeviceIdProvider
import com.uit.finance.core.sync.engine.SyncChangeApplier
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncRemoteDataSource
import com.uit.finance.core.sync.engine.SyncStore
import com.uit.finance.core.sync.issue.SqlDelightSyncIssueRepository
import com.uit.finance.core.sync.issue.SyncIssueRepository
import com.uit.finance.core.sync.outbox.OutboxRepository
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.outbox.SqlDelightOutboxRepository
import com.uit.finance.core.sync.remote.ApiSyncRemoteDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform [com.uit.finance.core.sync.scheduler.SyncScheduler]. */
internal expect fun platformSyncModule(): Module

/**
 * Module sở hữu entity (core/ledger...) tự đăng ký [SyncChangeApplier] của mình; engine nhận tất cả qua
 * `getAll`, không biết module nào tồn tại.
 */
val coreSyncModule: Module = module {
    includes(platformSyncModule())
    single { OutboxWriter(uuidGenerator = get(), clock = get()) }
    single<OutboxRepository> { SqlDelightOutboxRepository(databases = get(), dispatchers = get()) }
    single<SyncIssueRepository> { SqlDelightSyncIssueRepository(databases = get(), dispatchers = get()) }
    single<DeviceIdProvider> { DeviceIdProvider { get<AppSettings>().installationId } }
    single<SyncRemoteDataSource> { ApiSyncRemoteDataSource(api = get(), json = get()) }
    single<SyncEngine> {
        DefaultSyncEngine(
            databases = get(),
            store = SyncStore(
                appliers = getAll<SyncChangeApplier>().associateBy { it.entity },
                clock = get(),
                logger = get(),
            ),
            remote = get(),
            deviceId = get(),
            uuidGenerator = get(),
            clock = get(),
            dispatchers = get(),
            logger = get(),
        )
    }
}
