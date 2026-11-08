package com.uit.finance.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.uit.finance.core.common.text.searchTextOf

/**
 * [FinanceDatabase.Schema] cộng các bước sửa dữ liệu chạy **trong** migration. Driver Android lẫn iOS đều mở DB
 * qua đây ([DatabaseDriverFactory]), nên các bước này chạy trong `openVerified`, trước khi DB được công bố cho
 * repository: không có sync, UI hay đổi user chen vào giữa (docs/SEARCH-PLAN.md F8).
 */
internal object UserDatabaseSchema : SqlSchema<QueryResult.Value<Unit>> by FinanceDatabase.Schema {

    /** Số của `migrations/2.sqm` (thêm `search_text`). Đổi tên file thì đổi cả số này. */
    private const val SEARCH_TEXT_MIGRATION = 2L

    private val dataMigrations = arrayOf(
        AfterVersion(SEARCH_TEXT_MIGRATION) { driver -> backfillSearchText(FinanceDatabase(driver)) },
    )

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = FinanceDatabase.Schema.migrate(driver, oldVersion, newVersion, *dataMigrations, *callbacks)
}

/**
 * Điền `search_text` cho các dòng chưa có, theo lô [batchSize] dòng đi theo khoá chính. Chạy lại không đổi gì: chỉ
 * đọc và ghi dòng còn `NULL`. Trả số lô đã điền.
 */
internal fun backfillSearchText(database: FinanceDatabase, batchSize: Long = BACKFILL_BATCH_SIZE): Int {
    val queries = database.ledgerSearchQueries
    var afterId = ""
    var batches = 0
    while (true) {
        val rows = queries.selectMissingSearchText(afterId = afterId, limit = batchSize).executeAsList()
        if (rows.isEmpty()) return batches
        rows.forEach { row -> queries.fillSearchText(searchText = searchTextOf(row.payee, row.note), id = row.id) }
        afterId = rows.last().id
        batches++
    }
}

private const val BACKFILL_BATCH_SIZE = 500L
