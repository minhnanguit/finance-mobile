package com.mosaicglobal.finance.core.datastore.di

import android.content.Context
import com.mosaicglobal.finance.core.datastore.secure.AndroidKeystoreSecureStorage
import com.mosaicglobal.finance.core.datastore.secure.SecureStorage
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformDatastoreModule(): Module = module {
    single<SecureStorage> { AndroidKeystoreSecureStorage(androidContext()) }
    single<Settings> {
        SharedPreferencesSettings(androidContext().getSharedPreferences("finance_settings", Context.MODE_PRIVATE))
    }
}
