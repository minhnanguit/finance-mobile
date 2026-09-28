package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.common.result.getOrElse
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.sync.cursor.SyncCursorStore
import com.uit.finance.core.sync.outbox.OutboxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSyncEngine(
    private val outbox: OutboxRepository,
    private val cursors: SyncCursorStore,
    private val remote: SyncRemoteDataSource,
    private val appliers: List<SyncChangeApplier>,
    private val clock: Clock,
    private val logger: Logger,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) : SyncEngine {

    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    private val mutex = Mutex()

    override suspend fun sync(): AppResult<SyncReport> = mutex.withLock {
        mutableStatus.value = SyncStatus.Running
        val pushResult = pushOutbox()
        val pulled = pullDeltas()
        val report = SyncReport(pushed = pushResult.first, pulled = pulled, failed = pushResult.second)
        mutableStatus.value = SyncStatus.Completed(clock.now(), report.pushed, report.pulled)
        AppResult.Success(report)
    }

    /** Returns (pushed, failed). Rejected entries stay in the outbox with an incremented attempt counter. */
    private suspend fun pushOutbox(): Pair<Int, Int> {
        var pushed = 0
        var failed = 0
        while (true) {
            val batch = outbox.pending(batchSize).getOrElse { emptyList() }
            if (batch.isEmpty()) break

            val (retryable, exhausted) = batch.partition { it.attempts < maxAttempts }
            exhausted.forEach { logger.w { "Outbox entry ${it.id} exceeded $maxAttempts attempts; parking" } }
            if (retryable.isEmpty()) break

            val outcome = remote.push(retryable).fold(
                onSuccess = { it },
                onFailure = { error ->
                    retryable.forEach { outbox.markFailed(it.id, error.describe()) }
                    mutableStatus.value = SyncStatus.Failed(error.describe())
                    return pushed to failed + retryable.size
                },
            )
            outbox.remove(outcome.acknowledgedIds)
            outcome.rejected.forEach { (id, reason) -> outbox.markFailed(id, reason) }
            pushed += outcome.acknowledgedIds.size
            failed += outcome.rejected.size
            if (outcome.acknowledgedIds.isEmpty()) break // nothing progressed; avoid spinning
        }
        return pushed to failed
    }

    private suspend fun pullDeltas(): Int {
        var pulled = 0
        for (applier in appliers) {
            val since = cursors.get(applier.entity)
            val outcome = remote.pull(applier.entity, since).getOrElse { error ->
                logger.w { "Pull for ${applier.entity} failed: ${error.describe()}" }
                null
            } ?: continue
            if (outcome.changes.isNotEmpty()) {
                applier.apply(outcome.changes)
                pulled += outcome.changes.size
            }
            outcome.nextCursor?.let { cursors.set(applier.entity, it) }
        }
        return pulled
    }

    private fun AppError.describe(): String = when (this) {
        is AppError.Network -> "network: $message"
        is AppError.Api -> "http $status ${code ?: ""}".trim()
        AppError.Unauthorized -> "unauthorized"
        is AppError.Validation -> "validation"
        is AppError.Storage -> "storage: $message"
        is AppError.Unknown -> "unknown: $message"
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}

/** v1 has no `/sync/...` endpoints: nothing is pushed or pulled, the outbox simply accumulates. */
internal class NoOpSyncRemoteDataSource : SyncRemoteDataSource {
    override suspend fun push(batch: List<com.uit.finance.core.sync.outbox.OutboxEntry>): AppResult<PushOutcome> =
        AppResult.Success(PushOutcome(acknowledgedIds = emptyList(), rejected = emptyMap()))

    override suspend fun pull(scope: String, since: String?): AppResult<PullOutcome> =
        AppResult.Success(PullOutcome(nextCursor = since, changes = emptyList()))
}
