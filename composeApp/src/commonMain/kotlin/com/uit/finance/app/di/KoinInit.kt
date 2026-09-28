package com.uit.finance.app.di

import com.uit.finance.core.auth.di.coreAuthModule
import com.uit.finance.core.common.di.coreCommonModule
import com.uit.finance.core.database.di.coreDatabaseModule
import com.uit.finance.core.datastore.di.coreDatastoreModule
import com.uit.finance.core.network.di.coreNetworkModule
import com.uit.finance.core.sync.di.coreSyncModule
import com.uit.finance.feature.auth.di.authFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Composition root. Mỗi nền tảng gọi đúng một lần lúc process start: Android từ `FinanceApplication`,
 * iOS từ `iOSApp.init` qua [startKoinIos] (iosMain).
 */
fun initKoin(platformDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platformDeclaration()
    modules(
        appModule,
        coreCommonModule,
        coreNetworkModule,
        coreAuthModule,
        coreDatabaseModule,
        coreDatastoreModule,
        coreSyncModule,
        authFeatureModule,
    )
}
