package com.uit.finance.core.ledger.data.sync

import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.local.save
import com.uit.finance.core.ledger.data.local.toDomain
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.engine.SyncChangeApplier

/*
 * Ghi bản của server vào bảng local. Chạy bên trong transaction của SyncEngine, nên cursor và dữ liệu
 * luôn khớp nhau. Bản ghi không đọc được (enum lạ...) ném lỗi; engine bỏ qua đúng bản ghi đó.
 */

internal class AccountSyncApplier(private val codec: SyncPayloadCodec) : SyncChangeApplier {
    override val entity: String = SyncEntity.ACCOUNT

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        if (change.deleted) {
            database.accountQueries.markDeleted(change.id)
        } else {
            database.save(codec.decodeAccount(requireNotNull(change.data) { "missing data" }).toDomain(change.id))
        }
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        database.accountQueries.remove(entityId)
    }
}

internal class CategorySyncApplier(private val codec: SyncPayloadCodec) : SyncChangeApplier {
    override val entity: String = SyncEntity.CATEGORY

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        if (change.deleted) {
            database.categoryQueries.markDeleted(change.id)
        } else {
            database.save(codec.decodeCategory(requireNotNull(change.data) { "missing data" }).toDomain(change.id))
        }
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        database.categoryQueries.remove(entityId)
    }
}

internal class TransactionSyncApplier(private val codec: SyncPayloadCodec) : SyncChangeApplier {
    override val entity: String = SyncEntity.TRANSACTION

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        if (change.deleted) {
            database.ledgerTransactionQueries.markDeleted(change.id)
        } else {
            database.save(codec.decodeTransaction(requireNotNull(change.data) { "missing data" }).toDomain(change.id))
        }
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        database.ledgerTransactionQueries.remove(entityId)
    }
}
