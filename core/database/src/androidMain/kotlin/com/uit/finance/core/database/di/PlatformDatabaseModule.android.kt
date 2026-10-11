package com.uit.finance.core.database.di

import com.uit.finance.core.database.DatabaseDriverFactory
import com.uit.finance.core.database.EncryptedDatabaseFiles
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformDatabaseModule(): Module = module {
    single<EncryptedDatabaseFiles> { DatabaseDriverFactory(androidContext()) }
}
