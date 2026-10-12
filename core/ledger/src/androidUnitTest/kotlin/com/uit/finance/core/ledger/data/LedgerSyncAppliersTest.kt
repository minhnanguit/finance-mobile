package com.uit.finance.core.ledger.data

import com.uit.finance.core.ledger.data.sync.AccountSyncApplier
import com.uit.finance.core.ledger.data.sync.TransactionSyncApplier
import com.uit.finance.core.ledger.support.FakeCodec
import com.uit.finance.core.ledger.support.inMemoryDatabase
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.sync.engine.RemoteChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class LedgerSyncAppliersTest {

    private val database = inMemoryDatabase()
    private val codec = FakeCodec()

    private val account = AccountPayload("Ví", "BANK", "VND", 5_000, 0, archived = false)

    @Test
    fun `bản server ghi đè bản local`() {
        val applier = AccountSyncApplier(codec)

        applier.apply(database, RemoteChange("account", "a1", 1, deleted = false, data = codec.encode(account)))
        applier.apply(database, RemoteChange("account", "a1", 2, deleted = false, data = codec.encode(account.copy(name = "Ví lương"))))

        assertEquals("Ví lương", database.accountQueries.selectById("a1").executeAsOne().name)
    }

    @Test
    fun `tombstone đánh dấu xoá, discard xoá hẳn`() {
        val applier = AccountSyncApplier(codec)
        applier.apply(database, RemoteChange("account", "a1", 1, deleted = false, data = codec.encode(account)))
        applier.apply(database, RemoteChange("account", "a2", 2, deleted = false, data = codec.encode(account)))

        applier.apply(database, RemoteChange("account", "a1", 3, deleted = true, data = null))
        applier.discard(database, "a2")

        assertEquals(1L, database.accountQueries.selectById("a1").executeAsOne().deleted)
        assertNull(database.accountQueries.selectById("a2").executeAsOneOrNull())
    }

    @Test
    fun `giao dịch về trước ví của nó vẫn lưu được - bảng local không có FK`() {
        val payload = TransactionPayload("EXPENSE", "CONFIRMED", "chưa-có-ví", null, 1_000, "VND", "c1", LocalDate(2026, 10, 6), null, null, null)

        TransactionSyncApplier(codec).apply(database, RemoteChange("transaction", "t1", 1, false, codec.encode(payload)))

        assertEquals("chưa-có-ví", database.ledgerTransactionQueries.selectById("t1").executeAsOne().account_id)
    }

    @Test
    fun `thiếu data mà không phải tombstone thì ném lỗi để engine bỏ qua bản ghi đó`() {
        assertFailsWith<IllegalArgumentException> {
            AccountSyncApplier(codec).apply(database, RemoteChange("account", "a1", 1, deleted = false, data = null))
        }
    }
}
