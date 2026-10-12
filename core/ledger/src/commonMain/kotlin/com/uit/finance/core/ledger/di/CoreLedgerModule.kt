package com.uit.finance.core.ledger.di

import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.repository.AccountRepositoryImpl
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.data.repository.CategoryRepositoryImpl
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.data.sync.AccountSyncApplier
import com.uit.finance.core.ledger.data.sync.CategorySyncApplier
import com.uit.finance.core.ledger.data.sync.TransactionSyncApplier
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.sync.engine.SyncChangeApplier
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Cần có trong graph: `UserDatabaseProvider` (core/database), `OutboxWriter` + `SyncScheduler`
 * (core/sync), `SyncPayloadCodec` (core/network), `UuidGenerator` + `Clock` + `DispatcherProvider`.
 */
val coreLedgerModule = module {
    single { LedgerStore(databases = get(), scheduler = get(), dispatchers = get()) }

    single<AccountRepository> {
        AccountRepositoryImpl(store = get(), outbox = get(), codec = get(), uuidGenerator = get(), dispatchers = get())
    }
    single<CategoryRepository> {
        CategoryRepositoryImpl(store = get(), outbox = get(), codec = get(), uuidGenerator = get(), dispatchers = get())
    }
    single<TransactionRepository> {
        TransactionRepositoryImpl(
            store = get(),
            outbox = get(),
            codec = get(),
            uuidGenerator = get(),
            clock = get(),
            dispatchers = get(),
        )
    }
    single<BalanceRepository> { BalanceRepositoryImpl(store = get(), dispatchers = get()) }

    // SyncEngine nhận mọi applier qua getAll<SyncChangeApplier>().
    single { AccountSyncApplier(codec = get()) } bind SyncChangeApplier::class
    single { CategorySyncApplier(codec = get()) } bind SyncChangeApplier::class
    single { TransactionSyncApplier(codec = get()) } bind SyncChangeApplier::class
}
