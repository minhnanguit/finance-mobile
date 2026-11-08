package com.uit.finance.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) : EncryptedDatabaseFiles {

    override fun create(fileName: String, passphrase: String): SqlDriver {
        SqlCipherLibrary.ensureLoaded()
        return AndroidSqliteDriver(
            schema = UserDatabaseSchema,
            context = context,
            name = fileName,
            factory = SupportOpenHelperFactory(passphrase.encodeToByteArray()),
        )
    }

    override fun delete(fileName: String) {
        context.deleteDatabase(fileName)
    }

    // Sai khoá với SQLCipher là SQLiteNotADatabaseException; error handler mặc định của SQLCipher KHÔNG tự
    // xoá file khi có codec, nên quyết định xoá hay giữ nằm hết ở UserDatabaseManager.
    override fun isUnreadableFile(error: Throwable): Boolean = error.causeChain().any {
        it is SQLiteNotADatabaseException || it is SQLiteDatabaseCorruptException
    }
}

/** Thư viện native của SQLCipher phải được nạp trước câu lệnh đầu tiên, một lần cho cả process. */
private object SqlCipherLibrary {
    private val loaded by lazy { System.loadLibrary("sqlcipher") }

    fun ensureLoaded() = loaded
}
