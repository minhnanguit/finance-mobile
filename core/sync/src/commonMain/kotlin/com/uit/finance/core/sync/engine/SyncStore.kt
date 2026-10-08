@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.sync.outbox.OutboxAction
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Tóm tắt một lần áp kết quả push. */
internal data class PushTally(val done: Int, val rejected: Int, val retrying: Int)

/**
 * Mọi thao tác DB của engine. Mỗi method là **một transaction**: hoặc áp trọn kết quả của cả batch /
 * cả trang, hoặc không áp gì; kill app giữa chừng không để lại trạng thái nửa vời.
 */
internal class SyncStore(
    private val appliers: Map<String, SyncChangeApplier>,
    private val clock: Clock,
    private val logger: Logger,
) {

    fun pending(database: FinanceDatabase, limit: Int, maxAttempts: Int): List<OutboxEntry> =
        database.outboxQueries.selectPending(maxAttempts = maxAttempts.toLong(), limit = limit.toLong())
            .executeAsList()
            .map { row ->
                OutboxEntry(
                    opId = row.op_id,
                    entity = row.entity,
                    entityId = row.entity_id,
                    action = OutboxAction.valueOf(row.action),
                    payload = row.payload,
                    createdAt = Instant.fromEpochMilliseconds(row.created_at),
                    attempts = row.attempts.toInt(),
                    lastError = row.last_error,
                )
            }

    /**
     * Áp kết quả của một batch.
     *
     * Bản `current` của server chỉ được ghi đè lên local khi entity đó **không còn op nào chờ gửi**:
     * user sửa tiếp trong lúc batch đang bay thì bản sửa mới thắng, và sẽ được gửi ở lượt sau.
     */
    fun applyPushResults(database: FinanceDatabase, batch: List<OutboxEntry>, results: List<PushResult>): PushTally {
        val byOpId = batch.associateBy { it.opId }
        var done = 0
        var rejected = 0
        var retrying = 0
        database.transaction {
            for (result in results) {
                val entry = byOpId[result.opId] ?: continue
                when (result.outcome) {
                    SyncOutcome.APPLIED, SyncOutcome.DUPLICATE, SyncOutcome.CONFLICT -> {
                        database.outboxQueries.deleteByOpId(entry.opId)
                        result.current?.let { adoptIfIdle(database, it) }
                        done++
                    }
                    SyncOutcome.REJECTED -> {
                        database.outboxQueries.deleteByOpId(entry.opId)
                        database.syncRejectionQueries.insert(
                            entry.opId,
                            entry.entity,
                            entry.entityId,
                            result.code,
                            clock.now().toEpochMilliseconds(),
                        )
                        restoreIfIdle(database, entry, result.current)
                        rejected++
                    }
                    SyncOutcome.RETRY -> {
                        database.outboxQueries.markAttempt(result.code, entry.opId)
                        retrying++
                    }
                }
            }
        }
        return PushTally(done = done, rejected = rejected, retrying = retrying)
    }

    /** Cả batch hỏng (400 cho cả request): tính một lần thử cho mọi op để op lỗi cuối cùng bị dừng. */
    fun markBatchAttempt(database: FinanceDatabase, batch: List<OutboxEntry>, reason: String) {
        database.transaction {
            batch.forEach { database.outboxQueries.markAttempt(reason, it.opId) }
        }
    }

    fun cursor(database: FinanceDatabase): String? = database.syncCursorQueries.select().executeAsOneOrNull()

    fun clearCursor(database: FinanceDatabase) = database.syncCursorQueries.clear()

    /**
     * Áp một trang pull **cùng cursor mới** trong một transaction: không bao giờ có cursor đã tiến mà
     * dữ liệu chưa ghi. Bản ghi còn op chờ gửi thì bỏ qua (bản local chưa gửi thắng; kết quả push sau
     * đó mang về bản của server).
     */
    fun applyPage(database: FinanceDatabase, page: PullPage): Int {
        var applied = 0
        database.transaction {
            for (change in page.changes) {
                if (hasPending(database, change.entity, change.id)) continue
                if (applySafely(database, change)) applied++
            }
            database.syncCursorQueries.upsert(page.nextCursor)
        }
        return applied
    }

    private fun adoptIfIdle(database: FinanceDatabase, current: RemoteChange) {
        if (!hasPending(database, current.entity, current.id)) applySafely(database, current)
    }

    private fun restoreIfIdle(database: FinanceDatabase, entry: OutboxEntry, current: RemoteChange?) {
        if (hasPending(database, entry.entity, entry.entityId)) return
        if (current != null) {
            applySafely(database, current)
        } else {
            appliers[entry.entity]?.discard(database, entry.entityId)
        }
    }

    private fun hasPending(database: FinanceDatabase, entity: String, entityId: String): Boolean =
        database.outboxQueries.hasPendingFor(entity, entityId).executeAsOne() > 0

    /**
     * Một bản ghi không đọc được (enum lạ từ server mới hơn, field thiếu) không được chặn cả trang:
     * bỏ qua bản ghi đó, chỉ log id (ADR-006 B8).
     */
    private fun applySafely(database: FinanceDatabase, change: RemoteChange): Boolean {
        val applier = appliers[change.entity] ?: return false
        return try {
            applier.apply(database, change)
            true
        } catch (e: IllegalArgumentException) {
            logger.w { "Bỏ qua thay đổi không đọc được: entity=${change.entity} id=${change.id}" }
            false
        } catch (e: kotlinx.serialization.SerializationException) {
            logger.w { "Bỏ qua thay đổi không đọc được: entity=${change.entity} id=${change.id}" }
            false
        }
    }
}
