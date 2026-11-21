package com.uit.finance.core.capture.di

import co.touchlab.kermit.Logger
import com.uit.finance.core.capture.data.CaptureInboxNotifier
import com.uit.finance.core.capture.data.CaptureIngestor
import com.uit.finance.core.capture.data.CaptureNotificationIngress
import com.uit.finance.core.capture.data.CaptureStartup
import com.uit.finance.core.capture.data.repository.CaptureRepositoryImpl
import com.uit.finance.core.capture.data.settings.CaptureDeviceKeys
import com.uit.finance.core.capture.data.settings.CaptureSettingsRepositoryImpl
import com.uit.finance.core.capture.data.settings.CaptureUserDataCleaner
import com.uit.finance.core.capture.domain.repository.CaptureRepository
import com.uit.finance.core.capture.domain.repository.CaptureSettingsRepository
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.notification.NotificationIngress
import com.uit.finance.core.session.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Tự ghi giao dịch từ thông báo ngân hàng (AUTO-CAPTURE-PLAN). Cần trong graph: `UserDatabaseProvider`, `UserSession`,
 * `Settings`, `LedgerOccurrenceWriter` + repository của core/ledger, `SyncScheduler`, `LocalNotificationScheduler` +
 * `NotificationAccess` (core/notification), `Clock`, `DispatcherProvider`, và `CaptureEnvironment` (composeApp).
 */
val coreCaptureModule = module {
    // CaptureDeviceKeys không phụ thuộc UserSession: cleaner dùng nó mà không tạo vòng UserSession → cleaner → session.
    single { CaptureDeviceKeys(settings = get(), environment = get()) }
    single { CaptureSettingsRepositoryImpl(settings = get(), session = get(), deviceKeys = get()) }
    single<CaptureSettingsRepository> { get<CaptureSettingsRepositoryImpl>() }
    single { CaptureUserDataCleaner(settings = get(), deviceKeys = get()) } bind UserDataCleaner::class

    single {
        CaptureInboxNotifier(
            scheduler = get(),
            pendingCount = { get<CaptureRepository>().observePendingCount() },
            clock = get(),
        )
    }

    single {
        CaptureIngestor(
            databases = get(),
            session = get(),
            settings = get(),
            writer = get(),
            accounts = get(),
            categories = get(),
            syncScheduler = get(),
            notifier = get(),
            clock = get(),
            dispatchers = get(),
            logger = Logger.withTag("capture"),
        )
    }

    single<CaptureRepository> {
        CaptureRepositoryImpl(
            databases = get(),
            session = get(),
            settings = get(),
            ingestor = get(),
            writer = get(),
            transactions = get(),
            notifier = get(),
            syncScheduler = get(),
            clock = get(),
            dispatchers = get(),
        )
    }

    // Listener của core/notification lấy port này qua Koin; chưa có module này thì listener không làm gì.
    single<NotificationIngress> {
        CaptureNotificationIngress(
            settings = get(),
            ingestor = get(),
            environment = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
        )
    }

    single(createdAtStart = true) {
        CaptureStartup(
            session = get(),
            settings = get(),
            repository = get(),
            access = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
        ).also { it.start() }
    }
}
