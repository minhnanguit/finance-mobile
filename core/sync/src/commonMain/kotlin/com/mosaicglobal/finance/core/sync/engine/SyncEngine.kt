@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.sync.engine

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.sync.outbox.OutboxEntry
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Failed(val message: String?) : SyncStatus
    data class Completed(val at: Instant, val pushed: Int, val pulled: Int) : SyncStatus
}

data class SyncReport(val pushed: Int, val pulled: Int, val failed: Int)

/** Push the outbox, then pull deltas. Safe to call concurrently: overlapping runs are coalesced. */
interface SyncEngine {
    val status: StateFlow<SyncStatus>
    suspend fun sync(): AppResult<SyncReport>
}

/** Result of `POST /api/v1/sync/push` for one batch. */
data class PushOutcome(
    val acknowledgedIds: List<String>,
    val rejected: Map<String, String>,
)

/** Result of `GET /api/v1/sync/pull?since=<cursor>`. Item application is delegated to [SyncChangeApplier]s. */
data class PullOutcome(
    val nextCursor: String?,
    val changes: List<RemoteChange>,
)

data class RemoteChange(val entity: String, val id: String, val payload: String, val updatedAt: Instant, val deleted: Boolean)

/**
 * Remote side of the sync protocol. v1 of the contract has no sync endpoints yet, so the only
 * implementation is a no-op stub; the interface is the stable seam the real client will plug into.
 */
interface SyncRemoteDataSource {
    suspend fun push(batch: List<OutboxEntry>): AppResult<PushOutcome>
    suspend fun pull(scope: String, since: String?): AppResult<PullOutcome>
}

/** Feature modules register one per entity to apply pulled changes into their SQLDelight tables. */
interface SyncChangeApplier {
    val entity: String
    suspend fun apply(changes: List<RemoteChange>)
}
