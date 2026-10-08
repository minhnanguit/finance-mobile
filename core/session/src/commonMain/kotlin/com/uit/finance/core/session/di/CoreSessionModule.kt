package com.uit.finance.core.session.di

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.session.DefaultUserSession
import com.uit.finance.core.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Cần có trong graph: `SessionStore` (core/datastore), `UserApi` (core/network), `UserDatabases`
 * (core/database), `SyncScheduler` (core/sync). App gọi `UserSession.start()` một lần lúc khởi động.
 */
val coreSessionModule = module {
    single<UserSession> {
        DefaultUserSession(
            sessions = get(),
            userApi = get(),
            databases = get(),
            scheduler = get(),
            // Sống theo process: theo dõi phiên suốt đời app, không gắn với màn hình nào.
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
            logger = get<Logger>(),
        )
    }
}
