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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Điều phối một vòng sync: push hết outbox, rồi pull tới khi `hasMore = false` (ADR-002 §4).
 *
 * - Lỗi mạng, 5xx, 401: dừng vòng này, giữ nguyên mọi op (không tính vào số lần thử).
 * - 429: dừng và không gọi server nữa cho tới khi hết `Retry-After`.
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
    private val pullPageSize: Int = DEFAULT_PULL_PAGE,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) : SyncEngine {

    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    private val mutex = Mutex()
    private var rerunRequested = false
    private var notBefore: Instant? = null

    override suspend fun sync(): AppResult<SyncReport> {
        if (!mutex.tryLock()) {
            // Đang có một vòng chạy: nhờ nó chạy thêm một vòng nữa thay vì xếp hàng.
            rerunRequested = true
            return AppResult.Success(SyncReport.EMPTY)
        }
        try {
            var result: AppResult<SyncReport>
            do {
                rerunRequested = false
                result = runOnce()
            } while (rerunRequested && result is AppResult.Success)
            return result
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runOnce(): AppResult<SyncReport> {
        val database = databases.database.value ?: return AppResult.Success(SyncReport.EMPTY)
        notBefore?.let { until ->
            val waitSeconds = (until - clock.now()).inWholeSeconds
            if (waitSeconds > 0) return AppResult.Failure(rateLimited(waitSeconds))
            notBefore = null
        }

        mutableStatus.value = SyncStatus.Running
        val result = try {
            withContext(dispatchers.io) { pushThenPull(database) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DB bị đóng giữa chừng (đăng xuất) hoặc lỗi SQLite: dừng vòng này, op vẫn còn nguyên.
            logger.w { "Sync dừng vì lỗi cục bộ: ${e::class.simpleName}" }
            AppResult.Failure(AppError.Storage(e::class.simpleName))
        }
        mutableStatus.value = when (result) {
            is AppResult.Success -> SyncStatus.Completed(clock.now(), result.value.pushed, result.value.pulled)
            is AppResult.Failure -> SyncStatus.Failed(result.error.describe())
        }
        return result
    }

    private suspend fun pushThenPull(database: FinanceDatabase): AppResult<SyncReport> {
        var pushed = 0
        var rejected = 0
        var retrying = 0

        // Có giới hạn: user cứ sửa liên tục thì hàng chờ không bao giờ rỗng; phần còn lại để vòng sau.
        for (round in 1..MAX_PUSH_ROUNDS) {
            val batch = store.pending(database, pushBatchSize, maxAttempts)
            if (batch.isEmpty()) break
            val response = remote.push(deviceId.deviceId(), batch, idempotencyKey = uuidGenerator.generate())
            val results = when (response) {
                is AppResult.Success -> response.value
                is AppResult.Failure -> return stop(database, batch, response.error)
            }
            val tally = store.applyPushResults(database, batch, results)
            pushed += tally.done
            rejected += tally.rejected
            retrying += tally.retrying
            // Không op nào xong (toàn RETRY): thử lại ở vòng sau, đừng quay tại chỗ.
            if (tally.done + tally.rejected == 0) break
        }

        var pulled = 0
        var cursor = store.cursor(database)
        var restarted = false
        while (true) {
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
                    return stop(database, emptyList(), response.error)
                }
            }
            pulled += store.applyPage(database, page)
            cursor = page.nextCursor
            if (!page.hasMore) break
        }

        logger.i { "Sync xong: pushed=$pushed pulled=$pulled rejected=$rejected retrying=$retrying" }
        return AppResult.Success(SyncReport(pushed = pushed, pulled = pulled, rejected = rejected, retrying = retrying))
    }

    private fun stop(database: FinanceDatabase, batch: List<OutboxEntry>, error: AppError): AppResult<SyncReport> {
        when {
            error is AppError.Api && error.status == HTTP_TOO_MANY_REQUESTS -> {
                val wait = error.retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS
                notBefore = clock.now() + wait.seconds
            }
            // Cả batch bị từ chối vì hình dạng request: tính một lần thử để op lỗi cuối cùng bị dừng.
            error is AppError.Api && error.status == HTTP_BAD_REQUEST && batch.isNotEmpty() ->
                store.markBatchAttempt(database, batch, error.code ?: "http 400")
        }
        logger.w { "Sync dừng: ${error.describe()}" }
        return AppResult.Failure(error)
    }

    private fun rateLimited(waitSeconds: Long) = AppError.Api(
        status = HTTP_TOO_MANY_REQUESTS,
        code = RATE_LIMITED_CODE,
        title = null,
        detail = null,
        retryAfterSeconds = waitSeconds,
    )

    private fun AppError.isCursorAhead() = this is AppError.Api && status == HTTP_CONFLICT && code == CURSOR_AHEAD_CODE

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
        const val DEFAULT_PULL_PAGE = 500 // = SyncApi.MAX_PULL_LIMIT
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val MAX_PUSH_ROUNDS = 20 // 20 × 100 op mỗi lần sync
        const val DEFAULT_RETRY_AFTER_SECONDS = 60L
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val CURSOR_AHEAD_CODE = "sync.cursor_ahead"
        const val RATE_LIMITED_CODE = "request.rate_limited"
    }
}
