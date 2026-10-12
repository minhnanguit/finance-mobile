@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Điều phối một vòng sync: push outbox, rồi pull tới khi `hasMore = false` (ADR-002 §4).
 *
 * - Mỗi vòng gắn với **một DB và một phiên**: trước mỗi request đều kiểm DB đang mở vẫn là DB của vòng này
 *   và chưa ai gọi [stop]. Đổi user giữa chừng thì vòng cũ dừng, không gửi sổ của người cũ bằng token mới.
 * - Batch theo cả số op (≤ 100) lẫn dung lượng (dưới giới hạn body của server). Server vẫn từ chối cả
 *   request (400/413) thì chia đôi batch tới khi tìm ra op gây lỗi; op đó chờ backoff, các op khác đi tiếp.
 * - `RETRY`: chờ backoff rồi gửi lại ([PushBackoff]), không bao giờ bỏ hẳn op.
 * - Lỗi mạng, 5xx, 401: dừng vòng này, giữ nguyên mọi op (không tính vào số lần thử).
 * - 429: dừng và không gọi server nữa cho tới khi hết `Retry-After`.
 * - Push bị từ chối vì nội dung không chặn pull: user vẫn nhận được thay đổi từ máy khác.
 * - `409 sync.cursor_ahead`: xoá cursor, pull lại từ đầu một lần (áp lại là idempotent).
 * - Chưa có DB của user (chưa đăng nhập): không làm gì.
 */
internal class DefaultSyncEngine(
    private val databases: UserDatabaseProvider,
    private val store: SyncStore,
    private val remote: SyncRemoteDataSource,
    private val deviceId: DeviceIdProvider,
    private val uuidGenerator: UuidGenerator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
    private val pushBatchSize: Int = DEFAULT_PUSH_BATCH,
    private val pushBatchBytes: Int = DEFAULT_PUSH_BATCH_BYTES,
    private val pullPageSize: Int = DEFAULT_PULL_PAGE,
) : SyncEngine {

    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    private val mutex = Mutex()
    private var rerunRequested = false
    private var notBefore: Instant? = null

    /** Mỗi lần [stop] tăng số này; vòng nào bắt đầu trước đó đều tự dừng ở lần kiểm kế tiếp. */
    @Volatile private var stopGeneration = 0

    @Volatile private var running: Job? = null

    /** DB đã được thử áp lại `sync_deferred` trong process này (nâng cấp app thì process mới, thử lại). */
    private var replayedFor: FinanceDatabase? = null

    override suspend fun sync(): AppResult<SyncReport> {
        if (!mutex.tryLock()) {
            // Đang có một vòng chạy: nhờ nó chạy thêm một vòng nữa thay vì xếp hàng.
            rerunRequested = true
            return AppResult.Success(SyncReport.EMPTY)
        }
        try {
            return coroutineScope {
                val round = async { runRequestedRounds() }
                running = round
                try {
                    round.await()
                } catch (e: CancellationException) {
                    ensureActive() // chính người gọi bị huỷ thì ném tiếp
                    mutableStatus.value = SyncStatus.Idle
                    AppResult.Failure(STOPPED)
                }
            }
        } finally {
            running = null
            mutex.unlock()
        }
    }

    override suspend fun stop() {
        stopGeneration++
        running?.cancelAndJoin()
    }

    private suspend fun runRequestedRounds(): AppResult<SyncReport> {
        var result: AppResult<SyncReport>
        do {
            rerunRequested = false
            result = runOnce()
        } while (rerunRequested && result is AppResult.Success)
        return result
    }

    private suspend fun runOnce(): AppResult<SyncReport> {
        val database = databases.database.value ?: return AppResult.Success(SyncReport.EMPTY)
        val round = Round(database, stopGeneration)
        notBefore?.let { until ->
            val waitSeconds = (until - clock.now()).inWholeSeconds
            if (waitSeconds > 0) return AppResult.Failure(rateLimited(waitSeconds))
            notBefore = null
        }

        mutableStatus.value = SyncStatus.Running
        val result = try {
            withContext(dispatchers.io) { pushThenPull(round) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionChangedException) {
            logger.i { "Phiên hoặc DB đã đổi; dừng vòng sync cũ" }
            AppResult.Failure(STOPPED)
        } catch (e: Exception) {
            // DB bị đóng giữa chừng (đăng xuất) hoặc lỗi SQLite: dừng vòng này, op vẫn còn nguyên.
            logger.w { "Sync dừng vì lỗi cục bộ: ${e::class.simpleName}" }
            AppResult.Failure(AppError.Storage(e::class.simpleName))
        }
        mutableStatus.value = when (result) {
            is AppResult.Success -> SyncStatus.Completed(clock.now(), result.value.pushed, result.value.pulled)
            is AppResult.Failure -> if (result.error == STOPPED) SyncStatus.Idle else SyncStatus.Failed(result.error.describe())
        }
        return result
    }

    private suspend fun pushThenPull(round: Round): AppResult<SyncReport> {
        val pushed = when (val phase = pushAll(round)) {
            is PushPhase.Stopped -> return stop(phase.error)
            is PushPhase.Done -> phase.tally
        }

        val database = round.database
        if (replayedFor !== database) {
            val replayed = store.replayDeferred(database, DEFERRED_REPLAY_LIMIT, clock.now())
            if (replayed > 0) logger.i { "Đã áp lại $replayed thay đổi bị hoãn" }
            replayedFor = database
        }

        var pulled = 0
        var cursor = store.cursor(database)
        var restarted = false
        while (true) {
            round.ensureCurrent()
            val page = when (val response = remote.pull(cursor, pullPageSize)) {
                is AppResult.Success -> response.value
                is AppResult.Failure -> {
                    if (response.error.isCursorAhead() && !restarted) {
                        logger.i { "Cursor vượt server; pull lại từ đầu" }
                        store.clearCursor(database)
                        cursor = null
                        restarted = true
                        continue
                    }
                    return stop(response.error)
                }
            }
            round.ensureCurrent()
            pulled += store.applyPage(database, page, clock.now())
            cursor = page.nextCursor
            if (!page.hasMore) break
        }

        logger.i { "Sync xong: pushed=${pushed.done} pulled=$pulled rejected=${pushed.rejected} retrying=${pushed.retrying}" }
        return AppResult.Success(
            SyncReport(pushed = pushed.done, pulled = pulled, rejected = pushed.rejected, retrying = pushed.retrying),
        )
    }

    /** Push tới khi hết op gửi được. Có giới hạn số lượt: user cứ sửa liên tục thì phần còn lại để vòng sau. */
    private suspend fun pushAll(round: Round): PushPhase {
        var tally = PushTally.ZERO
        repeat(MAX_PUSH_ROUNDS) {
            round.ensureCurrent()
            val candidates = store.eligible(round.database, clock.now(), pushBatchSize)
            if (candidates.isEmpty()) return PushPhase.Done(tally)
            when (val sent = send(round, candidates.fittingIn(pushBatchBytes))) {
                is Sent.Accepted -> tally += sent.tally
                is Sent.Stopped -> return PushPhase.Stopped(sent.error)
            }
        }
        return PushPhase.Done(tally)
    }

    /**
     * Gửi một batch. Server từ chối **cả request** (400 hình dạng, 413 quá lớn) thì chia đôi và gửi lần lượt
     * từng nửa, giữ nguyên thứ tự, cho tới khi khoanh được op gây lỗi. Không có op nào bị bỏ.
     */
    private suspend fun send(round: Round, batch: List<OutboxEntry>): Sent {
        round.ensureCurrent()
        val response = remote.push(deviceId.deviceId(), batch, idempotencyKey = uuidGenerator.generate())
        round.ensureCurrent()
        return when (response) {
            is AppResult.Success -> Sent.Accepted(store.applyPushResults(round.database, batch, response.value, clock.now()))
            is AppResult.Failure -> when {
                !response.error.isBatchRejection() -> Sent.Stopped(response.error)
                batch.size > 1 -> {
                    val half = batch.size / 2
                    val first = send(round, batch.subList(0, half))
                    if (first is Sent.Stopped) return first
                    when (val second = send(round, batch.subList(half, batch.size))) {
                        is Sent.Stopped -> second
                        is Sent.Accepted -> Sent.Accepted((first as Sent.Accepted).tally + second.tally)
                    }
                }
                else -> {
                    val reason = (response.error as AppError.Api).let { it.code ?: "http ${it.status}" }
                    logger.w { "Server từ chối op ${batch.single().opId} ($reason); thử lại sau" }
                    store.markFailed(round.database, batch, reason, clock.now())
                    Sent.Accepted(PushTally(done = 0, rejected = 0, retrying = 1))
                }
            }
        }
    }

    private fun stop(error: AppError): AppResult<SyncReport> {
        if (error is AppError.Api && error.status == HTTP_TOO_MANY_REQUESTS) {
            val wait = error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS
            notBefore = clock.now() + wait.seconds
        }
        logger.w { "Sync dừng: ${error.describe()}" }
        return AppResult.Failure(error)
    }

    /** Lấy prefix của batch vừa [budgetBytes]; luôn có ít nhất một op để hàng chờ không đứng yên. */
    private fun List<OutboxEntry>.fittingIn(budgetBytes: Int): List<OutboxEntry> {
        var used = 0
        var count = 0
        for (entry in this) {
            used += entry.estimatedWireBytes
            if (count > 0 && used > budgetBytes) break
            count++
        }
        return subList(0, count)
    }

    /** Vòng sync gắn với một DB và một lần [stop]. */
    private inner class Round(val database: FinanceDatabase, private val generation: Int) {
        fun ensureCurrent() {
            if (databases.database.value !== database || stopGeneration != generation) throw SessionChangedException()
        }
    }

    private class SessionChangedException : RuntimeException()

    private sealed interface PushPhase {
        data class Done(val tally: PushTally) : PushPhase
        data class Stopped(val error: AppError) : PushPhase
    }

    private sealed interface Sent {
        data class Accepted(val tally: PushTally) : Sent
        data class Stopped(val error: AppError) : Sent
    }

    private fun rateLimited(waitSeconds: Long) = AppError.Api(
        status = HTTP_TOO_MANY_REQUESTS,
        code = RATE_LIMITED_CODE,
        title = null,
        detail = null,
        retryAfterSeconds = waitSeconds,
    )

    private fun AppError.isCursorAhead() = this is AppError.Api && status == HTTP_CONFLICT && code == CURSOR_AHEAD_CODE

    /** Server từ chối cả request vì nội dung batch, không phải vì mạng/phiên/tần suất. */
    private fun AppError.isBatchRejection() = this is AppError.Api && (status == HTTP_BAD_REQUEST || status == HTTP_PAYLOAD_TOO_LARGE)

    private fun AppError.describe(): String = when (this) {
        is AppError.Network -> "network"
        is AppError.Api -> "http $status ${code.orEmpty()}".trim()
        AppError.Unauthorized -> "unauthorized"
        is AppError.Validation -> "validation"
        is AppError.Storage -> "storage"
        is AppError.Unknown -> "unknown"
    }

    private companion object {
        const val DEFAULT_PUSH_BATCH = 100 // = SyncApi.MAX_OPS_PER_PUSH
        // Server chặn body push trên 256 KiB (application.yml `body-limits`). Chừa phần cho envelope và sai số ước lượng.
        const val DEFAULT_PUSH_BATCH_BYTES = 192 * 1024
        const val DEFAULT_PULL_PAGE = 500 // = SyncApi.MAX_PULL_LIMIT
        const val MAX_PUSH_ROUNDS = 20 // 20 × 100 op mỗi lần sync
        const val DEFERRED_REPLAY_LIMIT = 500
        const val DEFAULT_RETRY_AFTER_SECONDS = 60L
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_CONFLICT = 409
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CURSOR_AHEAD_CODE = "sync.cursor_ahead"
        const val RATE_LIMITED_CODE = "request.rate_limited"
        val STOPPED = AppError.Unknown("sync stopped")
    }
}
