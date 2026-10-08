package com.uit.finance.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * DB của user đang đăng nhập, nhìn từ phía repository. Mọi repository đọc DB qua đây chứ không giữ
 * một [FinanceDatabase] cố định, nên đổi user là đổi DB, không bao giờ ghi nhầm sang sổ của người
 * khác (ADR-006 B5).
 */
interface UserDatabaseProvider {
    /** `null` khi chưa có user nào được mở (chưa đăng nhập, hoặc chưa biết `userId`). */
    val database: StateFlow<FinanceDatabase?>
}

/** Vòng đời các file DB trên máy. Chỉ lớp quản lý phiên (`core/session`) được gọi. */
interface UserDatabases : UserDatabaseProvider {
    val activeUserId: String?

    /** Mở DB của [userId], đóng DB của user khác nếu đang mở. Gọi lại với cùng user là no-op. */
    suspend fun open(userId: String)

    /** Đóng nhưng giữ file và khoá: hết phiên đăng nhập thì giữ dữ liệu chưa gửi (ADR-006 B7). */
    suspend fun close()

    /** Xoá hẳn file và khoá của [userId]: user bấm đăng xuất (ADR-006 B5). */
    suspend fun delete(userId: String)

    /** Các user còn DB trên máy này. */
    suspend fun storedUserIds(): Set<String>
}

/**
 * Mỗi user một file `finance-<userId>.db`, mã hoá bằng SQLCipher với khoá riêng (ADR-006 B5, B6).
 *
 * - Mở xong là kiểm `PRAGMA cipher_version`: lỡ link SQLite thường (không mã hoá) thì **từ chối mở**
 *   thay vì lặng lẽ ghi sổ ra file đọc được.
 * - Mất khoá (Keystore bị reset, app bị restore sang máy khác) thì file không đọc lại được nữa: xoá và
 *   tạo mới. Dữ liệu đã sync vẫn kéo về được; phần chưa gửi thì mất, không cứu được.
 */
internal class SqlCipherUserDatabases(
    private val factory: DatabaseDriverFactory,
    private val keys: DatabaseKeyStore,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : UserDatabases {

    private class Open(val userId: String, val driver: SqlDriver, val database: FinanceDatabase)

    private val mutex = Mutex()
    private var open: Open? = null
    private var legacyRemoved = false
    private val current = MutableStateFlow<FinanceDatabase?>(null)

    override val database: StateFlow<FinanceDatabase?> = current.asStateFlow()

    override val activeUserId: String? get() = open?.userId

    override suspend fun open(userId: String) = mutex.withLock {
        if (open?.userId == userId) return@withLock
        closeLocked()
        withContext(dispatchers.io) {
            removeLegacyDatabase()
            val opened = openVerified(userId, fileNameOf(userId))
            open = opened
            current.value = opened.database
        }
    }

    override suspend fun close() = mutex.withLock { closeLocked() }

    override suspend fun delete(userId: String) = mutex.withLock {
        if (open?.userId == userId) closeLocked()
        withContext(dispatchers.io) { factory.delete(fileNameOf(userId)) }
        keys.delete(userId)
    }

    override suspend fun storedUserIds(): Set<String> = keys.userIds()

    private suspend fun openVerified(userId: String, fileName: String): Open {
        val existingKey = keys.find(userId)
        if (existingKey == null) {
            // File không có khoá thì không bao giờ đọc được nữa; xoá để mở file mới sạch sẽ.
            factory.delete(fileName)
        }
        val key = existingKey ?: keys.create(userId)
        return try {
            connect(userId, fileName, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnencryptedDatabaseException) {
            throw e
        } catch (e: Exception) {
            if (existingKey == null) throw e
            // Khoá có nhưng không mở được file (hỏng, sai khoá): bỏ file cũ, mở lại từ đầu.
            logger.w { "Không mở được DB cục bộ (${e::class.simpleName}); tạo lại file mới" }
            factory.delete(fileName)
            connect(userId, fileName, keys.create(userId))
        }
    }

    private fun connect(userId: String, fileName: String, key: String): Open {
        val driver = factory.create(fileName, key)
        try {
            requireEncrypted(driver)
        } catch (e: Exception) {
            driver.close()
            throw e
        }
        return Open(userId, driver, FinanceDatabase(driver))
    }

    private fun closeLocked() {
        open?.driver?.close()
        open = null
        current.value = null
    }

    private fun removeLegacyDatabase() {
        if (legacyRemoved) return
        // DB dùng chung, không mã hoá của bản trước Phase 4: chỉ chứa hàng chờ của sync no-op.
        factory.delete(LEGACY_DATABASE_NAME)
        legacyRemoved = true
    }

    companion object {
        private const val LEGACY_DATABASE_NAME = "finance.db"
        private val SAFE_USER_ID = Regex("[A-Za-z0-9-]{1,64}")

        /** `userId` đến từ server; vẫn kiểm để không bao giờ thành đường dẫn lạ (`../`). */
        fun fileNameOf(userId: String): String {
            require(SAFE_USER_ID.matches(userId)) { "userId không hợp lệ cho tên file DB" }
            return "finance-$userId.db"
        }
    }
}

/** App đang link SQLite thường thay vì SQLCipher: không được ghi sổ ra file không mã hoá. */
class UnencryptedDatabaseException : IllegalStateException("SQLCipher is not linked; refusing to open an unencrypted database")

/** `PRAGMA cipher_version` chỉ có kết quả khi thư viện đang chạy là SQLCipher. Câu này cũng ép mở file bằng khoá. */
internal fun requireEncrypted(driver: SqlDriver) {
    val version = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA cipher_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0,
    ).value
    if (version.isNullOrBlank()) throw UnencryptedDatabaseException()
}
