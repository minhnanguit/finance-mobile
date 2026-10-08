package com.uit.finance.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Mở và xoá file DB đã mã hoá bằng SQLCipher (ADR-006 B6). Android: `AndroidSqliteDriver` +
 * `SupportOpenHelperFactory` của SQLCipher. iOS: `NativeSqliteDriver` + `PRAGMA key` (cần app link
 * SQLCipher thay cho libsqlite3).
 */
expect class DatabaseDriverFactory {
    /** Mở (tạo nếu chưa có) [fileName] bằng [passphrase]. Chỉ mở thật ở câu lệnh đầu tiên. */
    fun create(fileName: String, passphrase: String): SqlDriver

    /** Xoá file và các file phụ (journal, WAL). Không có thì thôi. */
    fun delete(fileName: String)
}
