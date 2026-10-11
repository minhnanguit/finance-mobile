@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.outbox.SyncRef
import com.uit.finance.core.sync.support.inMemoryDatabase
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Thứ tự gửi của outbox (Outbox.sq `selectEligible`). Server áp từng op độc lập theo thứ tự trong batch,
 * nên máy phải tự giữ: bản cũ không đè bản mới, ví không bị archive/xoá trước giao dịch của nó.
 */
class OutboxOrderingTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock()
    private val ids = FakeUuidGenerator()
    private val writer = OutboxWriter(ids, clock)
    private val store = SyncStore(appliers = emptyMap(), logger = Logger.withTag("test"))

    private fun account(id: String, name: String = id) =
        writer.upsert(database, "account", id, buildJsonObject { put("name", JsonPrimitive(name)) })

    private fun transaction(id: String, accountId: String) =
        writer.upsert(database, "transaction", id, buildJsonObject { put("note", JsonPrimitive(id)) }, listOf(SyncRef("account", accountId)))

    private fun eligible() = store.eligible(database, clock.now(), limit = 100).map { "${it.entity}:${it.entityId}" }

    private fun done(entityId: String) {
        val op = database.outboxQueries.selectAll().executeAsList().first { it.entity_id == entityId }
        database.outboxQueries.deleteByOpId(op.op_id)
    }

    private fun backOff(entityId: String) {
        val op = database.outboxQueries.selectAll().executeAsList().first { it.entity_id == entityId }
        database.outboxQueries.markAttempt("sync.server_error", (clock.now() + 1.minutes).toEpochMilliseconds(), op.op_id)
    }

    @Test
    fun `tạo ví, ghi giao dịch, archive ví - archive đợi giao dịch lên server trước`() {
        account("a1")
        transaction("t1", accountId = "a1")
        account("a1", name = "archived")

        assertEquals(listOf("account:a1", "transaction:t1"), eligible(), "lượt 1: ví rồi giao dịch, chưa archive")
        done("a1")
        assertEquals(listOf("transaction:t1"), eligible(), "archive vẫn đợi giao dịch, kể cả khi giao dịch đang RETRY")
        done("t1")
        assertEquals(listOf("account:a1"), eligible())
    }

    @Test
    fun `hai op của cùng một bản ghi không bao giờ đi chung batch`() {
        account("a1", name = "v1")
        account("b1")
        account("a1", name = "v2")

        assertEquals(listOf("account:a1", "account:b1"), eligible())
    }

    @Test
    fun `giao dịch đợi op tạo ví đang chờ backoff, op khác không bị chặn`() {
        account("a1")
        transaction("t1", accountId = "a1")
        account("b1")
        backOff("a1")

        assertEquals(listOf("account:b1"), eligible(), "gửi t1 bây giờ chỉ tốn một lần RETRY")
        clock.advanceBy(2.minutes)
        assertEquals(listOf("account:a1", "transaction:t1", "account:b1"), eligible())
    }

    @Test
    fun `op đầu hàng hết backoff thì luôn gửi được - hàng chờ không kẹt vĩnh viễn`() {
        account("a1")
        transaction("t1", accountId = "a1")
        backOff("a1")
        backOff("t1")

        assertEquals(emptyList(), eligible())
        clock.advanceBy(2.minutes)
        assertEquals(listOf("account:a1", "transaction:t1"), eligible())
    }
}
