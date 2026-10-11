package com.uit.finance.core.sync.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.sync.engine.STUCK_AFTER_ATTEMPTS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Thay đổi chưa gửi được, cho UI (badge "chưa đồng bộ", cảnh báo khi đăng xuất, kéo để làm mới). */
interface OutboxRepository {
    /** Mọi thay đổi chưa lên server. Đổi user là tự đổi theo DB của user đó. */
    fun observeUnsentCount(): Flow<Long>

    suspend fun unsentCount(): Long

    /** Thay đổi server đã nhiều lần chưa nhận (vẫn đang được thử lại): UI nên báo "chưa gửi được". */
    fun observeStuckCount(): Flow<Long>

    /** User chủ động làm mới: bỏ thời gian chờ backoff để lượt sync kế tiếp gửi lại ngay mọi thay đổi. */
    suspend fun retryAllNow()
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

    override fun observeStuckCount(): Flow<Long> = databases.database.flatMapLatest { database ->
        database?.outboxQueries?.countStuck(STUCK_AFTER_ATTEMPTS.toLong())?.asFlow()?.mapToOne(dispatchers.io) ?: flowOf(0L)
    }

    override suspend fun retryAllNow() = withContext(dispatchers.io) {
        databases.database.value?.outboxQueries?.resetBackoff()
        Unit
    }
}
