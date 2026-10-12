package com.uit.finance.core.database

import app.cash.sqldelight.db.SqlDriver

/** File DB đã mã hoá bằng SQLCipher (ADR-006 B6). Tách thành interface để test vòng đời DB không cần SQLCipher. */
interface EncryptedDatabaseFiles {
    /** Mở (tạo nếu chưa có) [fileName] bằng [passphrase]. Chỉ mở thật ở câu lệnh đầu tiên. */
    fun create(fileName: String, passphrase: String): SqlDriver

    /** Xoá file và các file phụ (journal, WAL). Không có thì thôi. */
    fun delete(fileName: String)

    /**
     * [error] nghĩa là file **không bao giờ** đọc lại được bằng khoá này: sai khoá, không phải file DB,
     * file hỏng (`SQLITE_NOTADB`, `SQLITE_CORRUPT`). Mọi lỗi khác (I/O, đĩa đầy, DB đang bận, migration
     * lỗi) là tạm thời: phải giữ nguyên file, vì trong đó có thể còn thay đổi chưa gửi lên server.
     */
    fun isUnreadableFile(error: Throwable): Boolean
}

/**
 * Android: `AndroidSqliteDriver` + `SupportOpenHelperFactory` của SQLCipher. iOS: `NativeSqliteDriver` +
 * `PRAGMA key` (cần app link SQLCipher thay cho libsqlite3).
 */
expect class DatabaseDriverFactory : EncryptedDatabaseFiles

/** Duyệt lỗi và các nguyên nhân của nó; giới hạn độ sâu để chuỗi cause vòng tròn không treo app. */
internal fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)

private const val MAX_CAUSE_DEPTH = 8
