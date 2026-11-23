package com.uit.finance.core.recurring.data.sync

import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.RecurringPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.recurring.data.local.save
import com.uit.finance.core.recurring.data.local.toDomain
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.engine.SyncChangeApplier
import kotlinx.datetime.LocalDate

/**
 * Ghi bản của server vào bảng recurring, trong transaction của SyncEngine. `revision` local = revision server (RS9);
 * `blocked_from` là trạng thái chỉ trên máy nên giữ nguyên.
 *
 * `recurring.revision_conflict`: các op sửa còn chờ của khoản dựa trên revision đã bị máy khác thay, engine bỏ hết
 * (giữ op DELETE: xoá luôn thắng) rồi áp bản server nếu không còn gì chờ (RS10) và báo user qua `sync_rejection`.
 */
internal class RecurringSyncApplier(private val codec: RecurringPayloadCodec) : SyncChangeApplier {
    override val entity: String = SyncEntity.RECURRING

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        if (change.deleted) {
            database.recurringQueries.markDeleted(change.id)
            return
        }
        val blockedFrom = database.recurringQueries.selectById(change.id).executeAsOneOrNull()
            ?.blocked_from?.let(LocalDate::parse)
        database.save(codec.decodeRecurring(requireNotNull(change.data) { "missing data" }).toDomain(change.id, blockedFrom))
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        database.recurringQueries.deleteRevisions(entityId)
        database.recurringQueries.remove(entityId)
    }

    override fun dropPendingOnConflict(code: String?): Boolean = code == RecurringErrorCode.REVISION_CONFLICT

    override fun clearSynced(database: FinanceDatabase) {
        database.recurringQueries.deleteSyncedRevisions()
        database.recurringQueries.deleteSynced()
    }
}
