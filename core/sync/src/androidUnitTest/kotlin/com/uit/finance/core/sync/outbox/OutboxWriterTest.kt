package com.uit.finance.core.sync.outbox

import com.uit.finance.core.sync.support.inMemoryDatabase
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class OutboxWriterTest {

    private val database = inMemoryDatabase()
    private val ids = FakeUuidGenerator()
    private val writer = OutboxWriter(ids, TestClock())

    private fun payload(name: String) = buildJsonObject { put("name", JsonPrimitive(name)) }

    private fun rows() = database.outboxQueries.selectPending(maxAttempts = 99, limit = 99).executeAsList()

    @Test
    fun `sửa lại bản ghi còn chờ gửi thì cập nhật tại chỗ, giữ vị trí trong hàng`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        writer.upsert(database, "transaction", "t1", payload("phở"))
        writer.upsert(database, "account", "a1", payload("Ví lương"))

        val rows = rows()
        assertEquals(listOf("a1", "t1"), rows.map { it.entity_id }, "ví vẫn đi trước giao dịch")
        assertEquals("""{"name":"Ví lương"}""", rows.first().payload)
        assertEquals(ids.generated[2], rows.first().op_id, "op id mới để kết quả của op cũ không khớp nữa")
    }

    @Test
    fun `sửa lại reset số lần thử vì đây là op mới`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        database.outboxQueries.markAttempt("ledger.reference_pending", ids.generated[0])

        writer.upsert(database, "account", "a1", payload("Ví 2"))

        val row = rows().single()
        assertEquals(0L, row.attempts)
        assertNull(row.last_error)
    }

    @Test
    fun `xoá sau khi upsert thì thêm op DELETE phía sau, không gộp`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        writer.delete(database, "account", "a1")

        assertEquals(listOf("UPSERT", "DELETE"), rows().map { it.action })
        assertNull(rows().last().payload)
    }
}
