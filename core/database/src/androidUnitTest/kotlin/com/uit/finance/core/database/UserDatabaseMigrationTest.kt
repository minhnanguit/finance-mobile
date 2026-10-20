package com.uit.finance.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DB của user đã có trên máy phải nâng lên đúng schema mới mà không mất gì: các file migration `.sqm` và các
 * file `.sq` phải cho ra cùng một schema. Lệch nhau thì máy cũ và máy mới chạy hai schema khác nhau mà không ai biết.
 * App mở DB qua [UserDatabaseSchema], nên test cũng nâng qua đó (gồm cả bước điền dữ liệu).
 */
class UserDatabaseMigrationTest {

    @Test
    fun `nâng từ schema 1 ra đúng schema của DB tạo mới`() {
        val upgraded = schemaOneDriver().also { UserDatabaseSchema.migrate(it, oldVersion = 1, newVersion = UserDatabaseSchema.version) }

        assertEquals(describe(freshDriver()), describe(upgraded))
    }

    @Test
    fun `nâng từ schema 2 ra đúng schema của DB tạo mới`() {
        val upgraded = schemaTwoDriver().also { UserDatabaseSchema.migrate(it, oldVersion = 2, newVersion = UserDatabaseSchema.version) }

        assertEquals(describe(freshDriver()), describe(upgraded))
    }

    @Test
    fun `op đang chờ gửi từ trước vẫn còn và gửi được ngay sau khi nâng cấp`() {
        val driver = schemaOneDriver()
        driver.execute(
            null,
            "INSERT INTO outbox(op_id, entity, entity_id, action, payload, created_at, attempts) " +
                "VALUES ('op-1', 'account', 'a1', 'UPSERT', '{}', 0, 7)",
            0,
        )

        UserDatabaseSchema.migrate(driver, oldVersion = 1, newVersion = UserDatabaseSchema.version)

        // Bản cũ dừng hẳn op sau 5 lần RETRY; bản mới không bỏ op nào, op cũ được gửi lại.
        val eligible = FinanceDatabase(driver).outboxQueries.selectEligible(now = 0, limit = 10).executeAsList()
        assertEquals(listOf("op-1"), eligible.map { it.op_id })
    }

    // ---- search_text (docs/SEARCH-PLAN.md F8) ----

    @Test
    fun `nâng từ schema 2 thì mọi giao dịch cũ có search_text - kể cả dòng đã xoá`() {
        val driver = schemaTwoDriver()
        driver.insertLegacyTransaction("t-1", payee = "Highlands", note = "Cà phê  sáng")
        driver.insertLegacyTransaction("t-2", payee = null, note = null, deleted = true)
        driver.insertLegacyTransactions(count = 1_200)

        UserDatabaseSchema.migrate(driver, oldVersion = 2, newVersion = UserDatabaseSchema.version)

        assertEquals(listOf("highlands ca phe sang"), driver.query("SELECT search_text FROM ledger_transaction WHERE id = 't-1'"))
        assertEquals(listOf(""), driver.query("SELECT search_text FROM ledger_transaction WHERE id = 't-2'"))
        assertEquals(listOf("0"), driver.query("SELECT COUNT(*) FROM ledger_transaction WHERE search_text IS NULL"))
    }

    @Test
    fun `backfill đi theo lô 500 dòng - 1200 dòng là 3 lô và không sót dòng nào`() {
        val driver = freshDriver()
        driver.insertLegacyTransactions(count = 1_200)

        val batches = backfillSearchText(FinanceDatabase(driver))

        assertEquals(3, batches)
        assertEquals(listOf("0"), driver.query("SELECT COUNT(*) FROM ledger_transaction WHERE search_text IS NULL"))
    }

    @Test
    fun `chạy backfill lần 2 không đổi gì`() {
        val driver = freshDriver()
        driver.insertLegacyTransactions(count = 30)
        backfillSearchText(FinanceDatabase(driver))
        val before = driver.query("SELECT id || '=' || search_text FROM ledger_transaction ORDER BY id")

        val batches = backfillSearchText(FinanceDatabase(driver))

        assertEquals(0, batches)
        assertEquals(before, driver.query("SELECT id || '=' || search_text FROM ledger_transaction ORDER BY id"))
    }

    @Test
    fun `dòng đã có search_text thì không bị ghi đè`() {
        val driver = freshDriver()
        driver.insertLegacyTransaction("t-1", payee = "Highlands", note = null)
        driver.execute(null, "UPDATE ledger_transaction SET search_text = 'da ghi qua save' WHERE id = 't-1'", 0)
        driver.insertLegacyTransaction("t-2", payee = "Grab", note = null)

        backfillSearchText(FinanceDatabase(driver))

        assertEquals(
            listOf("t-1=da ghi qua save", "t-2=grab"),
            driver.query("SELECT id || '=' || search_text FROM ledger_transaction ORDER BY id"),
        )
    }

    @Test
    fun `DB tạo mới không chạy backfill - không có dòng nào thiếu search_text để điền`() {
        val driver = freshDriver()

        assertEquals(0, backfillSearchText(FinanceDatabase(driver)))
        assertTrue(driver.query("SELECT name FROM pragma_table_info('ledger_transaction')").contains("search_text"))
    }

    private fun freshDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { UserDatabaseSchema.create(it) }

    /** Schema 1 đóng băng đúng như đã phát hành ở Phase 4 (commit 9c84e89). Không sửa. */
    private fun schemaOneDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { driver ->
        SCHEMA_ONE.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
    }

    /** Schema 2 = schema 1 + `1.sqm`; file đó đã phát hành nên không đổi nữa. */
    private fun schemaTwoDriver(): SqlDriver = schemaOneDriver().also { FinanceDatabase.Schema.migrate(it, oldVersion = 1, newVersion = 2) }

    /** Giao dịch ghi bằng bản trước khi có `search_text`: chỉ các cột của schema 1 và 2. */
    private fun SqlDriver.insertLegacyTransaction(id: String, payee: String?, note: String?, deleted: Boolean = false) {
        execute(
            null,
            "INSERT INTO ledger_transaction(id, type, status, account_id, amount_minor, currency, category_id, " +
                "occurred_on, payee, note, deleted) VALUES (?, 'EXPENSE', 'CONFIRMED', 'a1', 1000, 'VND', 'c1', '2023-11-14', ?, ?, ?)",
            4,
        ) {
            bindString(0, id)
            bindString(1, payee)
            bindString(2, note)
            bindLong(3, if (deleted) 1L else 0L)
        }
    }

    private fun SqlDriver.insertLegacyTransactions(count: Int) = repeat(count) { index ->
        insertLegacyTransaction("legacy-${index.toString().padStart(5, '0')}", payee = "Người nhận $index", note = "Ghi chú")
    }

    /**
     * Bảng + cột (thứ tự, tên, kiểu, NOT NULL, default, PK) + index + trigger, đủ để so hai schema. Thứ tự cột
     * cũng phải khớp: code SQLDelight đọc `SELECT *` theo vị trí cột.
     */
    private fun describe(driver: SqlDriver): List<String> {
        val objects = driver.query("SELECT type || ' ' || name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name")
        val tables = driver.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        val columns = tables.flatMap { table ->
            driver.query("SELECT '$table#' || cid || '.' || name || ' ' || type || ' nn=' || \"notnull\" || ' def=' || ifnull(dflt_value, '-') || ' pk=' || pk FROM pragma_table_info('$table') ORDER BY cid")
        }
        val indexColumns = driver.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%' ORDER BY name")
            .flatMap { index -> driver.query("SELECT '$index:' || seqno || '=' || name FROM pragma_index_info('$index') ORDER BY seqno") }
        return objects + columns + indexColumns
    }

    private fun SqlDriver.query(sql: String): List<String> = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0).orEmpty()
            QueryResult.Value(rows.toList())
        },
        parameters = 0,
    ).value

    private companion object {
        val SCHEMA_ONE = """
            CREATE TABLE account (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                currency TEXT NOT NULL,
                opening_balance_minor INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE category (
                id TEXT NOT NULL PRIMARY KEY,
                kind TEXT NOT NULL,
                name TEXT NOT NULL,
                parent_id TEXT,
                icon TEXT,
                color TEXT,
                template_key TEXT,
                archived INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX category_parent_idx ON category(parent_id);
            CREATE TABLE ledger_transaction (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                account_id TEXT NOT NULL,
                counter_account_id TEXT,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                category_id TEXT,
                occurred_on TEXT NOT NULL,
                occurred_at INTEGER,
                payee TEXT,
                note TEXT,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX ledger_transaction_occurred_idx ON ledger_transaction(occurred_on DESC, id);
            CREATE INDEX ledger_transaction_account_idx ON ledger_transaction(account_id);
            CREATE INDEX ledger_transaction_counter_idx ON ledger_transaction(counter_account_id);
            CREATE INDEX ledger_transaction_category_idx ON ledger_transaction(category_id);
            CREATE TABLE outbox (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                op_id TEXT NOT NULL UNIQUE,
                entity TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                action TEXT NOT NULL,
                payload TEXT,
                created_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            );
            CREATE INDEX outbox_entity_idx ON outbox(entity, entity_id);
            CREATE TABLE sync_cursor (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                cursor TEXT NOT NULL
            );
            CREATE TABLE sync_rejection (
                op_id TEXT NOT NULL PRIMARY KEY,
                entity TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                code TEXT,
                rejected_at INTEGER NOT NULL,
                acknowledged INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
    }
}
