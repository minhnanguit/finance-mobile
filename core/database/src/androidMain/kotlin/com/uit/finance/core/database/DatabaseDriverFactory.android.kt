package com.uit.finance.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {

    actual fun create(fileName: String, passphrase: String): SqlDriver {
        SqlCipherLibrary.ensureLoaded()
        return AndroidSqliteDriver(
            schema = FinanceDatabase.Schema,
            context = context,
            name = fileName,
            factory = SupportOpenHelperFactory(passphrase.encodeToByteArray()),
        )
    }

    actual fun delete(fileName: String) {
        context.deleteDatabase(fileName)
    }
}

/** Thư viện native của SQLCipher phải được nạp trước câu lệnh đầu tiên, một lần cho cả process. */
private object SqlCipherLibrary {
    private val loaded by lazy { System.loadLibrary("sqlcipher") }

    fun ensureLoaded() = loaded
}
