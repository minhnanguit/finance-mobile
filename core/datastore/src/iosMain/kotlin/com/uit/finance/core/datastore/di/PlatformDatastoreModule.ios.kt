package com.uit.finance.core.datastore.di

import com.uit.finance.core.datastore.secure.KeychainSecureStorage
import com.uit.finance.core.datastore.secure.SecureStorage
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

@OptIn(ExperimentalSettingsImplementation::class)
internal actual fun platformDatastoreModule(): Module = module {
    single<SecureStorage> { KeychainSecureStorage() }
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
