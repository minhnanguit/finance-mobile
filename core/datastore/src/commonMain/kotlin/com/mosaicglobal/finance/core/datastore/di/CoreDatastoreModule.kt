package com.mosaicglobal.finance.core.datastore.di

import com.mosaicglobal.finance.core.datastore.session.SecureSessionStore
import com.mosaicglobal.finance.core.datastore.session.SessionStore
import com.mosaicglobal.finance.core.datastore.settings.AppSettings
import com.mosaicglobal.finance.core.network.auth.DeviceIdProvider
import com.mosaicglobal.finance.core.network.auth.TokenProvider
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

/** Provides [com.mosaicglobal.finance.core.datastore.secure.SecureStorage] and [com.russhwolf.settings.Settings]. */
internal expect fun platformDatastoreModule(): Module

val coreDatastoreModule: Module = module {
    includes(platformDatastoreModule())
    single { AppSettings(settings = get(), uuidGenerator = get()) }
    single { SecureSessionStore(secureStorage = get(), dispatchers = get(), clock = get()) } binds
        arrayOf(SessionStore::class, TokenProvider::class)
    single<DeviceIdProvider> { DeviceIdProvider { get<AppSettings>().installationId } }
}
