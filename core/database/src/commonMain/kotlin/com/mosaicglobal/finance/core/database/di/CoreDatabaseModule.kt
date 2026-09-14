package com.mosaicglobal.finance.core.database.di

import com.mosaicglobal.finance.core.database.DatabaseDriverFactory
import com.mosaicglobal.finance.core.database.FinanceDatabase
import com.mosaicglobal.finance.core.database.createDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform [DatabaseDriverFactory]. */
internal expect fun platformDatabaseModule(): Module

val coreDatabaseModule: Module = module {
    includes(platformDatabaseModule())
    single<FinanceDatabase> { get<DatabaseDriverFactory>().createDatabase() }
}
