package com.uit.finance.core.sync.issue

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.database.UserDatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Thay đổi bị server từ chối (`REJECTED`) mà user chưa xem, để UI báo. */
interface SyncIssueRepository {
    fun observeUnacknowledgedCount(): Flow<Long>

    suspend fun acknowledgeAll()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class SqlDelightSyncIssueRepository(
    private val databases: UserDatabaseProvider,
    private val dispatchers: DispatcherProvider,
) : SyncIssueRepository {

    override fun observeUnacknowledgedCount(): Flow<Long> = databases.database.flatMapLatest { database ->
        database?.syncRejectionQueries?.countUnacknowledged()?.asFlow()?.mapToOne(dispatchers.io) ?: flowOf(0L)
    }

    override suspend fun acknowledgeAll() = withContext(dispatchers.io) {
        databases.database.value?.syncRejectionQueries?.acknowledgeAll()
        Unit
    }
}
