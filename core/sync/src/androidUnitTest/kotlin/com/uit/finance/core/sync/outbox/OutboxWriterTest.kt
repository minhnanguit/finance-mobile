package com.uit.finance.core.sync.outbox

import com.uit.finance.core.sync.support.inMemoryDatabase
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class OutboxWriterTest {

    private val database = inMemoryDatabase()
    private val ids = FakeUuidGenerator()
    private val writer = OutboxWriter(ids, TestClock())

    private fun payload(name: String) = buildJsonObject { put("name", JsonPrimitive(name)) }

    private fun rows() = database.outboxQueries.selectAll().executeAsList()

    private fun refsOf(seq: Long) =
        database.outboxQueries.selectRefs(seq).executeAsList().map { "${it.entity}:${it.entity_id}" }

    @Test
    fun `sửa liên tiếp cùng một bản ghi thì gộp vào op cuối hàng, đổi op id`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        writer.upsert(database, "account", "a1", payload("Ví lương"))

        val row = rows().single()
        assertEquals("""{"name":"Ví lương"}""", row.payload)
        assertEquals(ids.generated[1], row.op_id, "op id mới để kết quả của op cũ không khớp nữa")
    }

    @Test
    fun `không gộp vào op nằm giữa hàng - trạng thái mới không được vượt lên trước op ghi sau nó`() {
        // Tạo ví → ghi giao dịch vào ví → archive ví. Gộp archive vào op tạo ví thì server nhận "ví đã archive"
        // trước giao dịch và từ chối giao dịch (ledger.archived).
        writer.upsert(database, "account", "a1", payload("Ví"))
        writer.upsert(database, "transaction", "t1", payload("phở"), listOf(SyncRef("account", "a1")))
        writer.upsert(database, "account", "a1", payload("Ví (archived)"))

        assertEquals(listOf("a1", "t1", "a1"), rows().map { it.entity_id })
        assertEquals("""{"name":"Ví"}""", rows().first().payload, "op đầu giữ nguyên trạng thái lúc ghi")
    }

    @Test
    fun `sửa lại op cuối thì reset số lần thử và thời gian chờ vì đây là bản mới`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        database.outboxQueries.markAttempt(lastError = "ledger.reference_pending", nextAttemptAt = 99_000, opId = ids.generated[0])

        writer.upsert(database, "account", "a1", payload("Ví 2"))

        val row = rows().single()
        assertEquals(0L, row.attempts)
        assertEquals(0L, row.next_attempt_at)
        assertNull(row.last_error)
    }

    @Test
    fun `xoá sau khi upsert thì thêm op DELETE phía sau, không gộp`() {
        writer.upsert(database, "account", "a1", payload("Ví"))
        writer.delete(database, "account", "a1")

        assertEquals(listOf("UPSERT", "DELETE"), rows().map { it.action })
        assertNull(rows().last().payload)
    }

    @Test
    fun `ghi lại bản ghi mà op trỏ tới, gộp thì thay bằng tham chiếu mới`() {
        writer.upsert(database, "transaction", "t1", payload("phở"), listOf(SyncRef("account", "a1"), SyncRef("category", "c1")))
        val seq = rows().single().seq
        assertEquals(listOf("account:a1", "category:c1"), refsOf(seq))

        writer.upsert(database, "transaction", "t1", payload("phở"), listOf(SyncRef("account", "a2"), SyncRef("category", "c1")))

        assertEquals(listOf("account:a2", "category:c1"), refsOf(seq))
    }

    @Test
    fun `op xong thì tham chiếu của nó tự mất theo`() {
        writer.upsert(database, "transaction", "t1", payload("phở"), listOf(SyncRef("account", "a1")))
        val row = rows().single()

        database.outboxQueries.deleteByOpId(row.op_id)

        assertTrue(refsOf(row.seq).isEmpty())
    }
}
