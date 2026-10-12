package com.uit.finance.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import co.touchlab.kermit.Logger
import com.uit.finance.core.testing.testDispatcherProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Review Phase 4 (Codex #3): chỉ được xoá DB của user khi chắc chắn không bao giờ đọc lại được. Mọi lỗi
 * khác phải giữ nguyên file và khoá, vì trong file có thể còn thay đổi chưa gửi lên server.
 */
class SqlCipherUserDatabasesTest {

    private val dir: File = Files.createTempDirectory("user-db").toFile()
    private val files = FakeEncryptedFiles(dir)
    private val keys = FakeKeyStore()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun TestScope.databases() =
        SqlCipherUserDatabases(files, keys, testDispatcherProvider(), Logger.withTag("test"))

    private fun FinanceDatabase.queuedOps() = outboxQueries.countAll().executeAsOne()

    private fun FinanceDatabase.queueOp() =
        outboxQueries.insert("op-1", "account", "a1", "UPSERT", "{}", 0)

    @Test
    fun `mở lại đúng user thì dùng lại khoá cũ, dữ liệu còn nguyên`() = runTest {
        val databases = databases()
        databases.open(USER)
        databases.database.value!!.queueOp()
        val key = keys.stored.getValue(USER)
        databases.close()

        databases.open(USER)

        assertEquals(key, keys.stored.getValue(USER))
        assertEquals(1L, databases.database.value!!.queuedOps())
    }

    @Test
    fun `lỗi tạm thời khi mở (đĩa đầy, DB bận, migration lỗi) thì giữ nguyên file và khoá`() = runTest {
        databases().apply { open(USER); database.value!!.queueOp(); close() }
        val key = keys.stored.getValue(USER)
        files.failNextOpen = IllegalStateException("disk I/O error")
        val databases = databases()

        assertFailsWith<IllegalStateException> { databases.open(USER) }

        assertTrue(files.exists(USER), "file không bị xoá")
        assertEquals(key, keys.stored.getValue(USER), "khoá không bị thay")
        databases.open(USER)
        assertEquals(1L, databases.database.value!!.queuedOps(), "mở lại được, op chưa gửi vẫn còn")
    }

    @Test
    fun `Keystore tạm không đọc được thì không mở, không xoá gì`() = runTest {
        databases().apply { open(USER); database.value!!.queueOp(); close() }
        keys.unavailable = true

        assertFailsWith<IllegalStateException> { databases().open(USER) }

        keys.unavailable = false
        assertTrue(files.exists(USER))
        val databases = databases()
        databases.open(USER)
        assertEquals(1L, databases.database.value!!.queuedOps())
    }

    @Test
    fun `sai khoá hoặc file hỏng thì tạo lại file mới với khoá mới`() = runTest {
        databases().apply { open(USER); database.value!!.queueOp(); close() }
        keys.stored[USER] = "khoa-khac" // Keystore bị thay, khoá không còn khớp file

        val databases = databases()
        databases.open(USER)

        assertNotEquals("khoa-khac", keys.stored.getValue(USER))
        assertEquals(0L, databases.database.value!!.queuedOps(), "file cũ không đọc được nữa nên bị thay")
    }

    @Test
    fun `mất hẳn khoá thì file cũ vô dụng - tạo lại`() = runTest {
        databases().apply { open(USER); database.value!!.queueOp(); close() }
        keys.stored.remove(USER)

        val databases = databases()
        databases.open(USER)

        assertEquals(0L, databases.database.value!!.queuedOps())
    }

    @Test
    fun `đóng thì gỡ DB khỏi provider trước, đăng xuất thì xoá cả file lẫn khoá`() = runTest {
        val databases = databases()
        databases.open(USER)
        databases.close()
        assertNull(databases.database.value)
        assertNull(databases.activeUserId)

        databases.open(USER)
        databases.delete(USER)

        assertNull(databases.database.value)
        assertTrue(!files.exists(USER) && USER !in keys.stored)
    }

    @Test
    fun `từ chối mở nếu thư viện không phải SQLCipher (file sẽ không được mã hoá)`() = runTest {
        files.pretendCipher = false

        assertFailsWith<UnencryptedDatabaseException> { databases().open(USER) }
    }

    private companion object {
        const val USER = "user-a"
    }
}

/** Khoá giữ trong bộ nhớ; [unavailable] giả lập Keystore tạm bận (ném lỗi, không trả null). */
private class FakeKeyStore : DatabaseKeyStore {
    val stored = mutableMapOf<String, String>()
    var unavailable = false
    private var counter = 0

    override suspend fun find(userId: String): String? {
        if (unavailable) throw IllegalStateException("keystore busy")
        return stored[userId]
    }

    override suspend fun create(userId: String): String = "key-${++counter}".also { stored[userId] = it }
    override suspend fun delete(userId: String) { stored.remove(userId) }
    override suspend fun userIds(): Set<String> = stored.keys.toSet()
}

/**
 * File SQLite thật trên đĩa (JDBC), giả lập phần SQLCipher: nhớ file được tạo bằng khoá nào; mở bằng khoá khác
 * thì câu lệnh đầu tiên ném [NotADatabase] như SQLCipher thật.
 */
private class FakeEncryptedFiles(private val dir: File) : EncryptedDatabaseFiles {
    private val keyOfFile = mutableMapOf<String, String>()
    var failNextOpen: Exception? = null
    var pretendCipher = true

    class NotADatabase : RuntimeException("file is not a database")

    fun exists(userId: String) = File(dir, SqlCipherUserDatabases.fileNameOf(userId)).exists()

    override fun create(fileName: String, passphrase: String): SqlDriver {
        failNextOpen?.let { failNextOpen = null; throw it }
        val file = File(dir, fileName)
        val isNew = !file.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        if (isNew) {
            FinanceDatabase.Schema.create(driver)
            keyOfFile[fileName] = passphrase
        }
        val wrongKey = keyOfFile[fileName] != passphrase
        return CipherLike(driver, wrongKey, pretendCipher)
    }

    override fun delete(fileName: String) {
        File(dir, fileName).delete()
        keyOfFile.remove(fileName)
    }

    override fun isUnreadableFile(error: Throwable): Boolean = error.causeChain().any { it is NotADatabase }

    /** Trả lời `PRAGMA cipher_version` như SQLCipher; sai khoá thì mọi câu lệnh đều hỏng. */
    private class CipherLike(
        private val delegate: SqlDriver,
        private val wrongKey: Boolean,
        private val pretendCipher: Boolean,
    ) : SqlDriver by delegate {
        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            if (wrongKey) throw NotADatabase()
            if (sql == "PRAGMA cipher_version") {
                return delegate.executeQuery(null, if (pretendCipher) "SELECT '4.19.1'" else "SELECT NULL", mapper, 0, null)
            }
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }
}
