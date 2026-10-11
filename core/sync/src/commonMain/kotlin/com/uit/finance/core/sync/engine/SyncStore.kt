@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.Sync_deferred
import com.uit.finance.core.sync.outbox.OutboxAction
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Tóm tắt một lần áp kết quả push. */
internal data class PushTally(val done: Int, val rejected: Int, val retrying: Int) {
    operator fun plus(other: PushTally) =
        PushTally(done + other.done, rejected + other.rejected, retrying + other.retrying)

    companion object {
        val ZERO = PushTally(0, 0, 0)
    }
}

/**
 * Mọi thao tác DB của engine. Mỗi method là **một transaction**: hoặc áp trọn kết quả của cả batch /
 * cả trang, hoặc không áp gì; kill app giữa chừng không để lại trạng thái nửa vời.
 */
internal class SyncStore(
    private val appliers: Map<String, SyncChangeApplier>,
    private val logger: Logger,
) {

    /** Op gửi được lúc [now], đúng thứ tự ghi và không phá thứ tự giữa các bản ghi (Outbox.sq `selectEligible`). */
    fun eligible(database: FinanceDatabase, now: Instant, limit: Int): List<OutboxEntry> =
        database.outboxQueries.selectEligible(now = now.toEpochMilliseconds(), limit = limit.toLong())
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
    fun applyPushResults(database: FinanceDatabase, batch: List<OutboxEntry>, results: List<PushResult>, now: Instant): PushTally {
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
                        result.current?.let { adoptIfIdle(database, it, now) }
                        done++
                    }
                    SyncOutcome.REJECTED -> {
                        database.outboxQueries.deleteByOpId(entry.opId)
                        database.syncRejectionQueries.insert(
                            entry.opId,
                            entry.entity,
                            entry.entityId,
                            result.code,
                            now.toEpochMilliseconds(),
                        )
                        restoreIfIdle(database, entry, result.current, now)
                        rejected++
                    }
                    SyncOutcome.RETRY -> {
                        markFailedAttempt(database, entry, result.code, now)
                        retrying++
                    }
                }
            }
        }
        return PushTally(done = done, rejected = rejected, retrying = retrying)
    }

    /** Server từ chối cả request chỉ vì [entries] (đã chia nhỏ tới mức một op): chờ backoff rồi thử lại. */
    fun markFailed(database: FinanceDatabase, entries: List<OutboxEntry>, reason: String, now: Instant) {
        database.transaction {
            entries.forEach { markFailedAttempt(database, it, reason, now) }
        }
    }

    private fun markFailedAttempt(database: FinanceDatabase, entry: OutboxEntry, reason: String?, now: Instant) {
        val nextAttemptAt = now + PushBackoff.delayAfter(entry.attempts + 1)
        database.outboxQueries.markAttempt(
            lastError = reason,
            nextAttemptAt = nextAttemptAt.toEpochMilliseconds(),
            opId = entry.opId,
        )
    }

    fun cursor(database: FinanceDatabase): String? = database.syncCursorQueries.select().executeAsOneOrNull()

    fun clearCursor(database: FinanceDatabase) = database.syncCursorQueries.clear()

    /**
     * Áp một trang pull **cùng cursor mới** trong một transaction: không bao giờ có cursor đã tiến mà
     * dữ liệu chưa ghi. Bản ghi còn op chờ gửi thì bỏ qua (bản local chưa gửi thắng; kết quả push sau
     * đó mang về bản của server). Bản ghi chưa đọc được thì cất vào `sync_deferred` chứ không mất.
     *
     * @return số bản ghi đã áp
     */
    fun applyPage(database: FinanceDatabase, page: PullPage, now: Instant): Int {
        var applied = 0
        database.transaction {
            for (change in page.changes) {
                if (hasPending(database, change.entity, change.id)) continue
                if (applyOrDefer(database, change, now)) applied++
            }
            database.syncCursorQueries.upsert(page.nextCursor)
        }
        return applied
    }

    /**
     * Thử áp lại các thay đổi đã cất ở `sync_deferred` (vd sau khi nâng cấp app biết thêm entity/enum mới).
     * Bản ghi còn op chờ gửi thì để lần sau, giống [applyPage].
     *
     * @return số bản ghi đã áp được
     */
    fun replayDeferred(database: FinanceDatabase, limit: Int, now: Instant): Int {
        var applied = 0
        database.transaction {
            for (row in database.syncDeferredQueries.selectOldest(limit.toLong()).executeAsList()) {
                if (row.entity !in appliers) continue // bản app này vẫn chưa biết entity đó
                if (hasPending(database, row.entity, row.entity_id)) continue
                if (applyOrDefer(database, row.toRemoteChange(), now)) applied++
            }
        }
        return applied
    }

    private fun adoptIfIdle(database: FinanceDatabase, current: RemoteChange, now: Instant) {
        if (!hasPending(database, current.entity, current.id)) applyOrDefer(database, current, now)
    }

    private fun restoreIfIdle(database: FinanceDatabase, entry: OutboxEntry, current: RemoteChange?, now: Instant) {
        if (hasPending(database, entry.entity, entry.entityId)) return
        if (current != null) {
            applyOrDefer(database, current, now)
        } else {
            appliers[entry.entity]?.discard(database, entry.entityId)
        }
    }

    private fun hasPending(database: FinanceDatabase, entity: String, entityId: String): Boolean =
        database.outboxQueries.hasPendingFor(entity, entityId).executeAsOne() > 0

    /**
     * Áp bản của server. Không áp được (entity lạ, enum mới từ server mới hơn, dữ liệu sai hình dạng) thì
     * **không chặn cả trang**, nhưng cũng không bỏ: cất bản mới nhất của bản ghi đó vào `sync_deferred` để
     * áp lại sau. Chỉ log entity và id (ADR-006 B8).
     */
    private fun applyOrDefer(database: FinanceDatabase, change: RemoteChange, now: Instant): Boolean {
        val reason = applyOrReason(database, change)
        if (reason == null) {
            // Bản mới hơn đã áp được: bản cũ đang chờ (nếu có) không còn giá trị.
            database.syncDeferredQueries.deleteFor(change.entity, change.id)
            return true
        }
        logger.w { "Chưa áp được thay đổi ($reason): entity=${change.entity} id=${change.id}" }
        val stored = database.syncDeferredQueries.changeSeqOf(change.entity, change.id).executeAsOneOrNull()
        if (stored == null || change.changeSeq >= stored) {
            database.syncDeferredQueries.upsert(
                entity = change.entity,
                entity_id = change.id,
                change_seq = change.changeSeq,
                deleted = if (change.deleted) 1L else 0L,
                data_ = change.data?.toString(),
                reason = reason,
                deferred_at = now.toEpochMilliseconds(),
            )
        }
        return false
    }

    private fun applyOrReason(database: FinanceDatabase, change: RemoteChange): String? {
        val applier = appliers[change.entity] ?: return UNKNOWN_ENTITY
        return try {
            applier.apply(database, change)
            null
        } catch (e: IllegalArgumentException) {
            UNREADABLE
        } catch (e: SerializationException) {
            UNREADABLE
        }
    }

    private fun Sync_deferred.toRemoteChange() = RemoteChange(
        entity = entity,
        id = entity_id,
        changeSeq = change_seq,
        deleted = deleted != 0L,
        data = data_?.let { Json.parseToJsonElement(it).jsonObject },
    )

    private companion object {
        const val UNKNOWN_ENTITY = "unknown_entity"
        const val UNREADABLE = "unreadable"
    }
}
