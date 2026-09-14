package com.mosaicglobal.finance.core.sync.cursor

import com.mosaicglobal.finance.core.common.coroutines.DispatcherProvider
import com.mosaicglobal.finance.core.database.FinanceDatabase
import kotlinx.coroutines.withContext

/** Persists the server delta cursor per scope (`GET /sync/pull?since=<cursor>`). */
interface SyncCursorStore {
    suspend fun get(scope: String): String?
    suspend fun set(scope: String, cursor: String)
    suspend fun clear(scope: String)
}

internal class SqlDelightSyncCursorStore(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) : SyncCursorStore {
    override suspend fun get(scope: String): String? = withContext(dispatchers.io) {
        database.syncCursorQueries.selectByScope(scope).executeAsOneOrNull()?.cursor
    }

    override suspend fun set(scope: String, cursor: String) {
        withContext(dispatchers.io) { database.syncCursorQueries.upsert(scope, cursor) }
    }

    override suspend fun clear(scope: String) {
        withContext(dispatchers.io) { database.syncCursorQueries.deleteByScope(scope) }
    }
}
