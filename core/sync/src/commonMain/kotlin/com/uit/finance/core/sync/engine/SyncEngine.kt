@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.engine

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Failed(val message: String?) : SyncStatus
    data class Completed(val at: Instant, val pushed: Int, val pulled: Int) : SyncStatus
}

/**
 * @param pushed op đã xong (`APPLIED`, `DUPLICATE`, `CONFLICT`)
 * @param rejected op bị từ chối, đã khôi phục bản local theo server
 * @param retrying op phải gửi lại sau (`RETRY`)
 */
data class SyncReport(val pushed: Int, val pulled: Int, val rejected: Int, val retrying: Int) {
    companion object {
        val EMPTY = SyncReport(pushed = 0, pulled = 0, rejected = 0, retrying = 0)
    }
}

/**
 * Push outbox rồi pull thay đổi (ADR-002). Gọi chồng nhau thì gộp: đang chạy mà có yêu cầu mới thì
 * chạy thêm đúng một vòng sau khi xong, không chạy song song.
 */
interface SyncEngine {
    val status: StateFlow<SyncStatus>
    suspend fun sync(): AppResult<SyncReport>
}

/** ADR-002 §4. Mọi kết quả trừ [RETRY] là chốt. */
enum class SyncOutcome { APPLIED, DUPLICATE, CONFLICT, REJECTED, RETRY }

/** Một bản ghi như server thấy, kể cả tombstone (`deleted = true`, `data = null`). */
data class RemoteChange(
    val entity: String,
    val id: String,
    val changeSeq: Long,
    val deleted: Boolean,
    val data: JsonObject?,
) {
    override fun toString(): String = "RemoteChange(entity=$entity, id=$id, changeSeq=$changeSeq, deleted=$deleted)"
}

data class PushResult(val opId: String, val outcome: SyncOutcome, val code: String?, val current: RemoteChange?)

data class PullPage(val changes: List<RemoteChange>, val nextCursor: String, val hasMore: Boolean)

/** Phía server của giao thức sync. Implementation thật gọi `SyncApi` (contract 2.1.0). */
interface SyncRemoteDataSource {
    suspend fun push(deviceId: String, batch: List<OutboxEntry>, idempotencyKey: String): AppResult<List<PushResult>>
    suspend fun pull(since: String?, limit: Int): AppResult<PullPage>
}

/**
 * Module sở hữu entity đăng ký một applier cho mỗi entity. Mọi method chạy **bên trong transaction**
 * của engine, nên bản ghi, outbox và cursor luôn khớp nhau.
 */
interface SyncChangeApplier {
    val entity: String

    /** Ghi bản của server đè lên bản local (kể cả tombstone). */
    fun apply(database: FinanceDatabase, change: RemoteChange)

    /** Bản ghi chưa từng có trên server và vừa bị từ chối: bỏ khỏi máy. */
    fun discard(database: FinanceDatabase, entityId: String)
}

/** `installationId` của máy, gửi kèm mỗi lần push để server ghi nhật ký (ADR-006 B9). */
fun interface DeviceIdProvider {
    fun deviceId(): String
}
