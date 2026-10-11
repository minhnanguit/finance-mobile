package com.uit.finance.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DB của user đã có trên máy (schema 1, Phase 4) phải nâng lên đúng schema mới mà không mất gì: migration
 * `1.sqm` và các file `.sq` phải cho ra cùng một schema. Lệch nhau thì máy cũ và máy mới chạy hai schema khác
 * nhau mà không ai biết.
 */
class UserDatabaseMigrationTest {

    @Test
    fun `nâng từ schema 1 ra đúng schema của DB tạo mới`() {
        val upgraded = schemaOneDriver().also { FinanceDatabase.Schema.migrate(it, oldVersion = 1, newVersion = FinanceDatabase.Schema.version) }
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { FinanceDatabase.Schema.create(it) }

        assertEquals(describe(fresh), describe(upgraded))
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

        FinanceDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FinanceDatabase.Schema.version)

        // Bản cũ dừng hẳn op sau 5 lần RETRY; bản mới không bỏ op nào, op cũ được gửi lại.
        val eligible = FinanceDatabase(driver).outboxQueries.selectEligible(now = 0, limit = 10).executeAsList()
        assertEquals(listOf("op-1"), eligible.map { it.op_id })
    }

    /** Schema 1 đóng băng đúng như đã phát hành ở Phase 4 (commit 9c84e89). Không sửa. */
    private fun schemaOneDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { driver ->
        SCHEMA_ONE.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
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
