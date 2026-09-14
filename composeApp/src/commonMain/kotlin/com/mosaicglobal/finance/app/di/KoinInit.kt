package com.mosaicglobal.finance.app.di

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
 * Composition root. Platforms call this once at process start:
 * Android from `FinanceApplication`, iOS from `iOSApp.init` via [startKoinIos] (iosMain).
 */
fun initKoin(platformDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    platformDeclaration()
    modules(
        appModule,
        coreCommonModule,
        coreNetworkModule,
        coreDatabaseModule,
        coreDatastoreModule,
        coreSyncModule,
        authFeatureModule,
    )
}
