package com.uit.finance.core.recurring.di

import com.uit.finance.core.recurring.data.local.RecurringStore
import com.uit.finance.core.recurring.data.repository.RecurringRepositoryImpl
import com.uit.finance.core.recurring.data.sync.RecurringSyncApplier
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.usecase.GenerateDueOccurrencesUseCase
import com.uit.finance.core.sync.engine.SyncChangeApplier
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Cần có trong graph: `UserDatabaseProvider` (core/database), `OutboxWriter` + `SyncScheduler` (core/sync),
 * `RecurringPayloadCodec` (core/network), `LedgerOccurrenceWriter` (core/ledger), `UuidGenerator` + `Clock` +
 * `DispatcherProvider` (core/common).
 */
val coreRecurringModule = module {
    single { RecurringStore(databases = get(), scheduler = get(), dispatchers = get()) }

    single<RecurringRepository> {
        RecurringRepositoryImpl(
            store = get(),
            outbox = get(),
            codec = get(),
            occurrences = get(),
            uuidGenerator = get(),
            clock = get(),
            dispatchers = get(),
        )
    }

    factory { GenerateDueOccurrencesUseCase(repository = get(), clock = get()) }

    // SyncEngine nhận mọi applier qua getAll<SyncChangeApplier>().
    single { RecurringSyncApplier(codec = get()) } bind SyncChangeApplier::class
}
