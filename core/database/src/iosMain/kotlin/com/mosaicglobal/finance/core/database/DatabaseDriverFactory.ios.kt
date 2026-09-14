package com.mosaicglobal.finance.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(schema = FinanceDatabase.Schema, name = DATABASE_NAME)
}
