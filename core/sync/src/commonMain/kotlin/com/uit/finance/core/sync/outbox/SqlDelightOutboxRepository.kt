@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.Outbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class SqlDelightOutboxRepository(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) : OutboxRepository {

    private val queries get() = database.outboxQueries

    override suspend fun enqueue(entry: OutboxEntry): AppResult<Unit> = dbCall {
        queries.insert(
            id = entry.id,
            entity = entry.entity,
            op = entry.op.name,
            payload = entry.payload,
            idempotency_key = entry.idempotencyKey,
            created_at = entry.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun pending(limit: Int): AppResult<List<OutboxEntry>> = dbCall {
        queries.selectPending(limit.toLong()).executeAsList().map { it.toEntry() }
    }

    override suspend fun markFailed(id: String, error: String?): AppResult<Unit> = dbCall {
        queries.markAttempt(last_error = error?.take(MAX_ERROR_LENGTH), id = id)
    }

    override suspend fun remove(ids: Collection<String>): AppResult<Unit> = dbCall {
        database.transaction {
            ids.forEach { queries.deleteById(it) }
        }
    }

    override fun observeCount(): Flow<Long> = queries.countAll().asFlow().mapToOne(dispatchers.io)

    private suspend fun <T> dbCall(block: () -> T): AppResult<T> = withContext(dispatchers.io) {
        try {
            AppResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(e.message))
        }
    }

    private fun Outbox.toEntry() = OutboxEntry(
        id = id,
        entity = entity,
        op = OutboxOp.valueOf(op),
        payload = payload,
        idempotencyKey = idempotency_key,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        attempts = attempts.toInt(),
        lastError = last_error,
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 500
    }
}
