package com.uit.finance.core.database.di

import com.uit.finance.core.database.SqlCipherUserDatabases
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.database.UserDatabases
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

/** Provides the platform [com.uit.finance.core.database.DatabaseDriverFactory]. */
internal expect fun platformDatabaseModule(): Module

/** Cần có trong graph: `DatabaseKeyStore` (core/datastore), `DispatcherProvider` + `Logger` (core/common). */
val coreDatabaseModule: Module = module {
    includes(platformDatabaseModule())
    single { SqlCipherUserDatabases(factory = get(), keys = get(), dispatchers = get(), logger = get()) } binds
        arrayOf(UserDatabases::class, UserDatabaseProvider::class)
}
