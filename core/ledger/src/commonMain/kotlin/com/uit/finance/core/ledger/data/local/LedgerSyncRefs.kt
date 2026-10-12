package com.uit.finance.core.ledger.data.local

import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.outbox.SyncRef

/*
 * Bản ghi mà op trỏ tới. Outbox dựa vào đây để không gửi "xoá/sửa ví" trước giao dịch còn nằm trong ví đó
 * (server sẽ từ chối: ledger.in_use, hoặc áp lệch thứ tự user đã thấy), xem OutboxWriter.
 */

internal fun Transaction.syncRefs(): List<SyncRef> = buildList {
    add(SyncRef(SyncEntity.ACCOUNT, accountId))
    counterAccountId?.let { add(SyncRef(SyncEntity.ACCOUNT, it)) }
    categoryId?.let { add(SyncRef(SyncEntity.CATEGORY, it)) }
}

internal fun Category.syncRefs(): List<SyncRef> = listOfNotNull(parentId?.let { SyncRef(SyncEntity.CATEGORY, it) })
