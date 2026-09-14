package com.mosaicglobal.finance.core.sync.outbox

import com.mosaicglobal.finance.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(entry: OutboxEntry): AppResult<Unit>
    suspend fun pending(limit: Int): AppResult<List<OutboxEntry>>
    suspend fun markFailed(id: String, error: String?): AppResult<Unit>
    suspend fun remove(ids: Collection<String>): AppResult<Unit>
    fun observeCount(): Flow<Long>
}
