package com.mosaicglobal.finance.core.sync.di

import com.mosaicglobal.finance.core.sync.conflict.ConflictPolicy
import com.mosaicglobal.finance.core.sync.conflict.LastWriteWinsPolicy
import com.mosaicglobal.finance.core.sync.cursor.SqlDelightSyncCursorStore
import com.mosaicglobal.finance.core.sync.cursor.SyncCursorStore
import com.mosaicglobal.finance.core.sync.engine.DefaultSyncEngine
import com.mosaicglobal.finance.core.sync.engine.NoOpSyncRemoteDataSource
import com.mosaicglobal.finance.core.sync.engine.SyncChangeApplier
import com.mosaicglobal.finance.core.sync.engine.SyncEngine
import com.mosaicglobal.finance.core.sync.engine.SyncRemoteDataSource
import com.mosaicglobal.finance.core.sync.outbox.OutboxRepository
import com.mosaicglobal.finance.core.sync.outbox.SqlDelightOutboxRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform [com.mosaicglobal.finance.core.sync.scheduler.SyncScheduler]. */
internal expect fun platformSyncModule(): Module

val coreSyncModule: Module = module {
    includes(platformSyncModule())
    single<OutboxRepository> { SqlDelightOutboxRepository(database = get(), dispatchers = get()) }
    single<SyncCursorStore> { SqlDelightSyncCursorStore(database = get(), dispatchers = get()) }
    single<ConflictPolicy> { LastWriteWinsPolicy }
    single<SyncRemoteDataSource> { NoOpSyncRemoteDataSource() }
    single<SyncEngine> {
        DefaultSyncEngine(
            outbox = get(),
            cursors = get(),
            remote = get(),
            appliers = getAll<SyncChangeApplier>(),
            clock = get(),
            logger = get(),
        )
    }
}
