package com.uit.finance.core.budget.di

import com.uit.finance.core.budget.data.local.BudgetStore
import com.uit.finance.core.budget.data.repository.BudgetRepositoryImpl
import com.uit.finance.core.budget.data.sync.BudgetSyncApplier
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.sync.engine.SyncChangeApplier
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Cần có trong graph: `UserDatabaseProvider` (core/database), `OutboxWriter` + `SyncScheduler` (core/sync),
 * `BudgetPayloadCodec` (core/network), `UuidGenerator` + `DispatcherProvider` (core/common).
 */
val coreBudgetModule = module {
    single { BudgetStore(databases = get(), scheduler = get(), dispatchers = get()) }

    single<BudgetRepository> {
        BudgetRepositoryImpl(store = get(), outbox = get(), codec = get(), uuidGenerator = get(), dispatchers = get())
    }

    // SyncEngine nhận mọi applier qua getAll<SyncChangeApplier>().
    single { BudgetSyncApplier(codec = get()) } bind SyncChangeApplier::class
}
