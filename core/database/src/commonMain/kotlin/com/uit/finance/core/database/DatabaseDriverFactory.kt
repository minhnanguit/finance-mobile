package com.uit.finance.core.database

import app.cash.sqldelight.db.SqlDriver

const val DATABASE_NAME: String = "finance.db"

/** Android: `AndroidSqliteDriver(context)`; iOS: `NativeSqliteDriver`. */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun DatabaseDriverFactory.createDatabase(): FinanceDatabase = FinanceDatabase(createDriver())
