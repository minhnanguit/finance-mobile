package com.uit.finance.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext

/**
 * `PRAGMA key` chỉ có tác dụng khi app link SQLCipher (iosApp/project.yml) thay cho libsqlite3; nếu
 * không, [requireEncrypted] chặn lại ngay khi mở.
 */
actual class DatabaseDriverFactory {

    actual fun create(fileName: String, passphrase: String): SqlDriver = NativeSqliteDriver(
        schema = FinanceDatabase.Schema,
        name = fileName,
        onConfiguration = { config ->
            config.copy(encryptionConfig = DatabaseConfiguration.Encryption(key = passphrase))
        },
    )

    actual fun delete(fileName: String) {
        DatabaseFileContext.deleteDatabase(fileName)
    }
}
