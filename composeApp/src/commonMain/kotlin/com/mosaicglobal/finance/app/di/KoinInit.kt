package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.core.auth.di.coreAuthModule
import com.mosaicglobal.finance.core.common.di.coreCommonModule
import com.mosaicglobal.finance.core.database.di.coreDatabaseModule
import com.mosaicglobal.finance.core.datastore.di.coreDatastoreModule
import com.mosaicglobal.finance.core.network.di.coreNetworkModule
import com.mosaicglobal.finance.core.sync.di.coreSyncModule
import com.mosaicglobal.finance.feature.auth.di.authFeatureModule
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
