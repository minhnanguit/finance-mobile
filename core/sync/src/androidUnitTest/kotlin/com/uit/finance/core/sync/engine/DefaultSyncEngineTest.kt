package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.support.FakeDatabases
import com.uit.finance.core.sync.support.FakeRemote
import com.uit.finance.core.sync.support.RecordingApplier
import com.uit.finance.core.sync.support.remote
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class DefaultSyncEngineTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val remote = FakeRemote()
    private val applier = RecordingApplier()
    private val clock = TestClock()
    private val ids = FakeUuidGenerator()
    private val writer = OutboxWriter(ids, clock)

    private fun TestScope.engine(maxAttempts: Int = 5) = DefaultSyncEngine(
        databases = databases,
        store = SyncStore(appliers = mapOf(applier.entity to applier), clock = clock, logger = Logger.withTag("test")),
        remote = remote,
        deviceId = { "device-1" },
        uuidGenerator = ids,
        clock = clock,
        dispatchers = testDispatcherProvider(),
        logger = Logger.withTag("test"),
        maxAttempts = maxAttempts,
    )

    private fun queue(id: String) =
        writer.upsert(database, "account", id, buildJsonObject { put("name", JsonPrimitive(id)) })

    private fun unsent() = database.outboxQueries.countAll().executeAsOne()

    private fun opIdOf(entityId: String) =
        database.outboxQueries.selectPending(99, 99).executeAsList().first { it.entity_id == entityId }.op_id

    @Test
    fun `chưa có DB của user thì không làm gì`() = runTest {
        databases.database.value = null

        val result = engine().sync()

        assertEquals(AppResult.Success(SyncReport.EMPTY), result)
        assertTrue(remote.pushedBatches.isEmpty() && remote.pulledCursors.isEmpty())
    }

    @Test
    fun `APPLIED xoá op khỏi hàng và ghi bản server về local`() = runTest {
        queue("a1")
        remote.onPush = { batch ->
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.APPLIED, null, remote(it.entityId, 7)) })
        }

        val report = engine().sync()

        assertEquals(0L, unsent())
        assertEquals(listOf("a1"), applier.applied.map { it.id })
        assertEquals(1, (report as AppResult.Success).value.pushed)
    }

    @Test
    fun `user sửa tiếp trong lúc batch đang bay thì bản sửa mới thắng, không bị bản server đè`() = runTest {
        queue("a1")
        var editedWhileInFlight = false
        remote.onPush = { batch ->
            if (!editedWhileInFlight) {
                queue("a1") // user sửa lại đúng lúc batch đầu đang bay: op cũ được thay bằng op mới
                editedWhileInFlight = true
            }
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.APPLIED, null, remote(it.entityId, 7)) })
        }

        engine().sync()

        // Lượt 1: bản server KHÔNG được đè bản đang chờ gửi. Lượt 2 gửi bản sửa mới, xong thì mới nhận bản server.
        assertEquals(2, remote.pushedBatches.size)
        assertEquals(1, applier.applied.size, "chỉ nhận bản server sau khi bản sửa mới đã được gửi")
        assertEquals(0L, unsent())
    }

    @Test
    fun `REJECTED bỏ op, ghi nhật ký từ chối, bản chưa từng lên server thì xoá khỏi máy`() = runTest {
        queue("a1")
        remote.onPush = { batch ->
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.REJECTED, "ledger.limit_exceeded", null) })
        }

        engine().sync()

        assertEquals(0L, unsent())
        assertEquals(listOf("a1"), applier.discarded)
        assertEquals("ledger.limit_exceeded", database.syncRejectionQueries.selectUnacknowledged().executeAsOne().code)
    }

    @Test
    fun `REJECTED có bản server thì khôi phục local theo bản đó`() = runTest {
        queue("a1")
        remote.onPush = { batch ->
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.REJECTED, "ledger.currency_locked", remote(it.entityId, 3)) })
        }

        engine().sync()

        assertEquals(listOf(3L), applier.applied.map { it.changeSeq })
        assertTrue(applier.discarded.isEmpty())
    }

    @Test
    fun `RETRY giữ op và tăng số lần thử, quá giới hạn thì không gửi nữa nhưng vẫn tính là chưa gửi`() = runTest {
        queue("a1")
        remote.onPush = { batch ->
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.RETRY, "ledger.reference_pending", null) })
        }
        val engine = engine(maxAttempts = 2)

        engine.sync()
        engine.sync()
        engine.sync()

        assertEquals(2, remote.pushedBatches.size, "lần thứ ba op đã bị dừng, không gửi nữa")
        assertEquals(1L, unsent())
    }

    @Test
    fun `mất mạng thì giữ nguyên op, không tính vào số lần thử, không pull`() = runTest {
        queue("a1")
        remote.onPush = { AppResult.Failure(AppError.Network("offline")) }

        val result = engine().sync()

        assertIs<AppResult.Failure>(result)
        assertEquals(0L, database.outboxQueries.selectPending(99, 99).executeAsOne().attempts)
        assertTrue(remote.pulledCursors.isEmpty())
    }

    @Test
    fun `429 thì không gọi server nữa cho tới khi hết Retry-After`() = runTest {
        queue("a1")
        remote.onPush = { AppResult.Failure(AppError.Api(429, "request.rate_limited", null, null, retryAfterSeconds = 30)) }
        val engine = engine()

        engine.sync()
        val blocked = engine.sync()
        clock.advanceBy(31.seconds)
        engine.sync()

        assertEquals(30L, ((blocked as AppResult.Failure).error as AppError.Api).retryAfterSeconds)
        assertEquals(2, remote.pushedBatches.size, "lần giữa không ra mạng")
    }

    @Test
    fun `cả batch bị 400 thì mỗi op bị tính một lần thử`() = runTest {
        queue("a1")
        queue("a2")
        remote.onPush = { AppResult.Failure(AppError.Api(400, "request.validation_failed", null, null)) }

        engine().sync()

        assertTrue(database.outboxQueries.selectPending(99, 99).executeAsList().all { it.attempts == 1L })
    }

    @Test
    fun `pull đi hết các trang, lưu cursor cuối, bỏ qua bản ghi còn op chờ gửi`() = runTest {
        queue("pending")
        remote.onPush = { AppResult.Success(emptyList()) } // server chưa trả gì cho op này
        remote.pages += AppResult.Success(PullPage(listOf(remote("a1", 1), remote("pending", 2)), "2", hasMore = true))
        remote.pages += AppResult.Success(PullPage(listOf(remote("a2", 3)), "3", hasMore = false))

        val report = engine().sync()

        assertEquals(listOf("a1", "a2"), applier.applied.map { it.id })
        assertEquals(listOf(null, "2"), remote.pulledCursors)
        assertEquals("3", database.syncCursorQueries.select().executeAsOne())
        assertEquals(2, (report as AppResult.Success).value.pulled)
    }

    @Test
    fun `cursor vượt server thì xoá cursor và pull lại từ đầu một lần`() = runTest {
        database.syncCursorQueries.upsert("999")
        remote.pages += AppResult.Failure(AppError.Api(409, "sync.cursor_ahead", null, null))
        remote.pages += AppResult.Success(PullPage(listOf(remote("a1", 1)), "1", hasMore = false))

        engine().sync()

        assertEquals(listOf("999", null), remote.pulledCursors)
        assertEquals("1", database.syncCursorQueries.select().executeAsOne())
    }

    @Test
    fun `mỗi lần push dùng một Idempotency-Key mới`() = runTest {
        queue("a1")
        engine().sync()
        queue("a2")
        engine().sync()

        assertEquals(2, remote.idempotencyKeys.toSet().size)
    }

    @Test
    fun `op id được gửi đúng như trong outbox`() = runTest {
        queue("a1")
        val opId = opIdOf("a1")

        engine().sync()

        assertEquals(listOf(opId), remote.pushedBatches.single().map { it.opId })
    }

    @Test
    fun `một lần sync có giới hạn số vòng push, không quay mãi khi hàng chờ cứ có op mới`() = runTest {
        queue("a1")
        remote.onPush = { batch ->
            queue("a1") // lần nào cũng có bản sửa mới chen vào
            AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.APPLIED, null, null) })
        }

        engine().sync()

        assertTrue(remote.pushedBatches.size <= 20)
        assertEquals(1L, unsent(), "phần còn lại để vòng sync sau")
    }
}
