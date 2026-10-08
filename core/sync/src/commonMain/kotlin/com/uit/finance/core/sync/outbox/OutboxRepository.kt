package com.uit.finance.core.sync.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.database.UserDatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Số thay đổi chưa gửi được, cho UI (badge "chưa đồng bộ", cảnh báo khi đăng xuất). */
interface OutboxRepository {
    /** Gồm cả op đã bị dừng vì `RETRY` quá nhiều lần. Đổi user là tự đổi theo DB của user đó. */
    fun observeUnsentCount(): Flow<Long>

    suspend fun unsentCount(): Long
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class SqlDelightOutboxRepository(
    private val databases: UserDatabaseProvider,
    private val dispatchers: DispatcherProvider,
) : OutboxRepository {

    override fun observeUnsentCount(): Flow<Long> = databases.database.flatMapLatest { database ->
        database?.outboxQueries?.countAll()?.asFlow()?.mapToOne(dispatchers.io) ?: flowOf(0L)
    }

    override suspend fun unsentCount(): Long = withContext(dispatchers.io) {
        databases.database.value?.outboxQueries?.countAll()?.executeAsOne() ?: 0L
    }
}
