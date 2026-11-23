package com.uit.finance.core.recurring.di

import com.uit.finance.core.recurring.reminder.ReminderScheduler
import com.uit.finance.core.recurring.reminder.NotificationReminderScheduler
import com.uit.finance.core.recurring.reminder.NotificationPermissionGateAdapter
import com.uit.finance.core.recurring.reminder.NotificationPermissionGate
import com.uit.finance.core.recurring.data.local.RecurringStore
import com.uit.finance.core.recurring.data.repository.RecurringRepositoryImpl
import com.uit.finance.core.recurring.data.sync.RecurringSyncApplier
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.usecase.GenerateDueOccurrencesUseCase
import com.uit.finance.core.sync.engine.SyncChangeApplier
import co.touchlab.kermit.Logger
import com.uit.finance.core.recurring.runtime.RecurringCoordinator
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Cần có trong graph: `UserDatabaseProvider` (core/database), `OutboxWriter` + `SyncScheduler` (core/sync),
 * `RecurringPayloadCodec` (core/network), `LedgerOccurrenceWriter` (core/ledger), `UuidGenerator` + `Clock` +
 * `DispatcherProvider` (core/common), `LocalNotificationScheduler` + `NotificationPermission` (core/notification).
 */
val coreRecurringModule = module {
    includes(platformRecurringModule())

    single<ReminderScheduler> { NotificationReminderScheduler(scheduler = get()) }
    single<NotificationPermissionGate> { NotificationPermissionGateAdapter(permission = get()) }

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

    // Chạy suốt đời process: sinh kỳ khi mở sổ, qua ngày mới, sau mỗi vòng sync; hẹn lại thông báo khi khoản đổi.
    single(createdAtStart = true) {
        RecurringCoordinator(
            databases = get(),
            syncEngine = get(),
            repository = get(),
            generate = get(),
            reminders = get(),
            clock = get(),
            dispatchers = get(),
            logger = Logger.withTag("recurring"),
        ).also {
            it.start()
            scheduleDailyGeneration(getKoin())
        }
    }

    // SyncEngine nhận mọi applier qua getAll<SyncChangeApplier>().
    single { RecurringSyncApplier(codec = get()) } bind SyncChangeApplier::class
}

/** Worker theo nền tảng (Android WorkManager sinh kỳ hằng ngày). */
internal expect fun platformRecurringModule(): Module

/** Android: worker sinh kỳ hằng ngày. iOS: dựa vào BGAppRefreshTask của sync. */
internal expect fun scheduleDailyGeneration(koin: Koin)
