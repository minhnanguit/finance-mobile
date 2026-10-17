package com.uit.finance.core.budget.data.sync

import com.uit.finance.core.budget.data.local.save
import com.uit.finance.core.budget.data.local.toDomain
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.BudgetPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.engine.SyncChangeApplier

/**
 * Ghi bản của server vào bảng `budget`. Chạy trong transaction của SyncEngine, nên cursor và dữ liệu luôn
 * khớp. Bản ghi không đọc được ném lỗi; engine cất vào `sync_deferred`.
 *
 * [discard] chạy khi op tạo bị `REJECTED` mà server không có bản nào (`current = null`), ví dụ
 * `budget.duplicate` lúc 2 máy offline cùng tạo cho một cặp (danh mục, tiền tệ): bỏ bản local, bản của máy
 * kia về qua pull (ADR-007 GS4).
 */
internal class BudgetSyncApplier(private val codec: BudgetPayloadCodec) : SyncChangeApplier {
    override val entity: String = SyncEntity.BUDGET

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        if (change.deleted) {
            database.budgetQueries.markDeleted(change.id)
        } else {
            database.save(codec.decodeBudget(requireNotNull(change.data) { "missing data" }).toDomain(change.id))
        }
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        database.budgetQueries.remove(entityId)
    }
}
