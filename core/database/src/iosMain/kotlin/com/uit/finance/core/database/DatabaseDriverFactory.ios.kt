package com.uit.finance.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.interop.SQLiteExceptionErrorCode
import co.touchlab.sqliter.interop.SqliteErrorType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * `PRAGMA key` chỉ có tác dụng khi app link SQLCipher (iosApp/project.yml) thay cho libsqlite3; nếu
 * không, [requireEncrypted] chặn lại ngay khi mở.
 *
 * Mọi file sổ nằm ở `Library/Application Support/databases` — đúng thư mục mặc định của SQLiter, giữ nguyên để
 * bản cài cũ không mất file — và thư mục đó được loại khỏi iCloud/Finder backup (ADR-008 P3). Đặt cờ trên
 * **thư mục** nên file `-wal`/`-shm` sinh ra sau cũng không bị sao lưu. Khoá giải mã đã là
 * `AfterFirstUnlockThisDeviceOnly` nên bản sao lưu cũng không mở được, nhưng không để file rời máy là tốt hơn.
 */
actual class DatabaseDriverFactory : EncryptedDatabaseFiles {

    private val directory: String by lazy { databaseDirectory() }

    override fun create(fileName: String, passphrase: String): SqlDriver = NativeSqliteDriver(
        schema = UserDatabaseSchema,
        name = fileName,
        onConfiguration = { config ->
            config.copy(
                extendedConfig = config.extendedConfig.copy(basePath = directory),
                encryptionConfig = DatabaseConfiguration.Encryption(key = passphrase),
            )
        },
    )

    override fun delete(fileName: String) {
        DatabaseFileContext.deleteDatabase(fileName, directory)
    }

    // Sai khoá với SQLCipher là SQLITE_NOTADB ("file is not a database"); file hỏng là SQLITE_CORRUPT.
    override fun isUnreadableFile(error: Throwable): Boolean = error.causeChain().any {
        it is SQLiteExceptionErrorCode &&
            (it.errorType == SqliteErrorType.SQLITE_NOTADB || it.errorType == SqliteErrorType.SQLITE_CORRUPT)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun databaseDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val support: NSURL = checkNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "Application Support directory unavailable" }
    val directory: NSURL = checkNotNull(support.URLByAppendingPathComponent(DATABASES_FOLDER, isDirectory = true))
    fileManager.createDirectoryAtURL(directory, withIntermediateDirectories = true, attributes = null, error = null)
    // Đặt lại mỗi lần mở app: rẻ, và tự sửa nếu cờ bị mất (khôi phục từ bản backup cũ).
    directory.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return checkNotNull(directory.path) { "database directory has no path" }
}

private const val DATABASES_FOLDER = "databases"
