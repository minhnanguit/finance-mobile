package com.uit.finance.core.ledger.support

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/** Mỗi câu SELECT được chạy kèm `EXPLAIN QUERY PLAN` với cùng tham số; ghi lại cột `detail` theo câu SQL. */
class PlanRecordingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    val plans = mutableMapOf<String, List<String>>()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        if (sql.trimStart().startsWith("SELECT")) {
            plans[sql] = delegate.executeQuery(null, "EXPLAIN QUERY PLAN $sql", ::details, parameters, binders).value
        }
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    private fun details(cursor: SqlCursor): QueryResult<List<String>> {
        val rows = mutableListOf<String>()
        while (cursor.next().value) rows += cursor.getString(DETAIL_COLUMN).orEmpty()
        return QueryResult.Value(rows)
    }

    private companion object {
        const val DETAIL_COLUMN = 3 // id, parent, notused, detail
    }
}
