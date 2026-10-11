package com.uit.finance.core.sync.engine

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.sync.outbox.OutboxEntry
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.support.FakeDatabases
import com.uit.finance.core.sync.support.FakeRemote
import com.uit.finance.core.sync.support.RecordingApplier
import com.uit.finance.core.sync.support.inMemoryDatabase
import com.uit.finance.core.sync.support.remote
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

    private fun TestScope.engine(
        appliers: List<SyncChangeApplier> = listOf(applier),
        pushBatchBytes: Int = 192 * 1024,
    ) = DefaultSyncEngine(
        databases = databases,
        store = SyncStore(appliers = appliers.associateBy { it.entity }, logger = Logger.withTag("test")),
        remote = remote,
        deviceId = { "device-1" },
        uuidGenerator = ids,
        clock = clock,
        dispatchers = testDispatcherProvider(),
        logger = Logger.withTag("test"),
        pushBatchBytes = pushBatchBytes,
    )

    private fun queue(id: String, name: String = id) =
        writer.upsert(database, "account", id, buildJsonObject { put("name", JsonPrimitive(name)) })

    private fun rows() = database.outboxQueries.selectAll().executeAsList()

    private fun unsent() = database.outboxQueries.countAll().executeAsOne()

    private fun opIdOf(entityId: String) = rows().first { it.entity_id == entityId }.op_id

    private fun retryAll(batch: List<OutboxEntry>): AppResult<List<PushResult>> =
        AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.RETRY, "sync.server_error", null) })

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
                queue("a1") // user sửa lại đúng lúc batch đầu đang bay: op cuối hàng được thay bằng op mới
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
    fun `RETRY thì chờ backoff rồi gửi lại, không gửi dồn ngay`() = runTest {
        queue("a1")
        remote.onPush = { retryAll(it) }
        val engine = engine()

        engine.sync()
        engine.sync() // còn trong 15 giây chờ
        clock.advanceBy(16.seconds)
        engine.sync()

        assertEquals(2, remote.pushedBatches.size)
        assertEquals(2L, rows().single().attempts)
    }

    @Test
    fun `RETRY nhiều lần vẫn không bao giờ bỏ op - server hồi phục là gửi được`() = runTest {
        queue("a1")
        remote.onPush = { retryAll(it) }
        val engine = engine()
        repeat(10) {
            engine.sync()
            clock.advanceBy(2.hours)
        }
        assertEquals(1L, database.outboxQueries.countStuck(STUCK_AFTER_ATTEMPTS.toLong()).executeAsOne(), "UI thấy op kẹt")

        remote.onPush = { FakeRemote.applied(it) }
        engine.sync()

        assertEquals(10 + 1, remote.pushedBatches.size)
        assertEquals(0L, unsent())
    }

    @Test
    fun `mất mạng thì giữ nguyên op, không tính vào số lần thử, không pull`() = runTest {
        queue("a1")
        remote.onPush = { AppResult.Failure(AppError.Network("offline")) }

        val result = engine().sync()

        assertIs<AppResult.Failure>(result)
        assertEquals(0L, rows().single().attempts)
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
    fun `cả batch bị 400 thì chia đôi tới khi khoanh được op lỗi, các op khác vẫn lên server, vẫn pull`() = runTest {
        listOf("a1", "a2", "bad", "a3").forEach { queue(it) }
        remote.onPush = { batch ->
            if (batch.any { it.entityId == "bad" }) AppResult.Failure(AppError.Api(400, "request.validation_failed", null, null))
            else FakeRemote.applied(batch)
        }

        val result = engine().sync()

        assertEquals(listOf("bad"), rows().map { it.entity_id }, "chỉ op lỗi còn lại")
        assertEquals(1L, rows().single().attempts)
        assertEquals("request.validation_failed", rows().single().last_error)
        assertIs<AppResult.Success<SyncReport>>(result)
        assertEquals(listOf<String?>(null), remote.pulledCursors, "push bị từ chối vì nội dung không chặn pull")
    }

    @Test
    fun `batch được chia theo dung lượng để không vượt giới hạn body của server`() = runTest {
        val longName = "x".repeat(1_000)
        repeat(5) { queue("a$it", name = longName) }

        engine(pushBatchBytes = 2_500).sync()

        assertTrue(remote.pushedBatches.size > 1)
        assertTrue(remote.pushedBatches.all { batch -> batch.sumOf { it.estimatedWireBytes } <= 2_500 })
        assertEquals(0L, unsent())
    }

    @Test
    fun `413 thì chia nhỏ batch và gửi lại, không kẹt`() = runTest {
        repeat(4) { queue("a$it") }
        remote.onPush = { batch ->
            if (batch.size > 1) AppResult.Failure(AppError.Api(413, "request.too_large", null, null)) else FakeRemote.applied(batch)
        }

        engine().sync()

        assertEquals(0L, unsent())
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
    fun `thay đổi chưa đọc được thì cất lại chứ không mất, nâng cấp app xong áp lại được`() = runTest {
        applier.unreadable += "a2"
        remote.pages += AppResult.Success(
            PullPage(listOf(remote("a1", 1), remote("a2", 2), remote("b1", 3, entity = "budget")), "3", hasMore = false),
        )

        engine().sync()

        assertEquals("3", database.syncCursorQueries.select().executeAsOne(), "một bản ghi lạ không chặn cả sync")
        assertEquals(listOf("a2", "b1"), database.syncDeferredQueries.selectOldest(10).executeAsList().map { it.entity_id })

        // Bản app mới: đọc được enum mới, biết thêm entity budget. Process mới nên áp lại một lần.
        applier.unreadable.clear()
        val budgets = RecordingApplier(entity = "budget")
        engine(appliers = listOf(applier, budgets)).sync()

        assertEquals(listOf("a1", "a2"), applier.applied.map { it.id })
        assertEquals(listOf("b1"), budgets.applied.map { it.id })
        assertEquals(0L, database.syncDeferredQueries.countAll().executeAsOne())
    }

    @Test
    fun `bản mới hơn của cùng bản ghi áp được thì bản cũ đang hoãn bị bỏ`() = runTest {
        applier.unreadable += "a1"
        remote.pages += AppResult.Success(PullPage(listOf(remote("a1", 1)), "1", hasMore = false))
        val engine = engine()
        engine.sync()

        applier.unreadable.clear()
        remote.pages += AppResult.Success(PullPage(listOf(remote("a1", 5)), "5", hasMore = false))
        engine.sync()

        assertEquals(listOf(5L), applier.applied.map { it.changeSeq })
        assertEquals(0L, database.syncDeferredQueries.countAll().executeAsOne())
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
            FakeRemote.applied(batch)
        }

        engine().sync()

        assertTrue(remote.pushedBatches.size <= 20)
        assertEquals(1L, unsent(), "phần còn lại để vòng sync sau")
    }

    @Test
    fun `đổi DB (đổi user) giữa vòng thì vòng cũ dừng, không áp kết quả, không pull`() = runTest {
        queue("a1")
        val oldDatabase = database
        remote.onPush = { batch ->
            databases.database.value = inMemoryDatabase() // A đăng xuất, B đăng nhập đúng lúc request đang bay
            FakeRemote.applied(batch)
        }

        val result = engine().sync()

        assertIs<AppResult.Failure>(result)
        assertTrue(remote.pulledCursors.isEmpty(), "không pull bằng phiên mới vào vòng của phiên cũ")
        assertEquals(1L, oldDatabase.outboxQueries.countAll().executeAsOne(), "kết quả không được áp vào sổ cũ")
    }

    @Test
    fun `stop huỷ request đang bay, chờ vòng dừng hẳn, không gửi thêm gì`() = runTest {
        queue("a1")
        queue("a2")
        val inFlight = CompletableDeferred<Unit>()
        remote.onPush = { batch ->
            inFlight.complete(Unit)
            CompletableDeferred<Nothing>().await() // request treo cho tới khi bị huỷ
            FakeRemote.applied(batch)
        }
        val engine = engine()

        val running = async { engine.sync() }
        inFlight.await()
        engine.stop()
        runCurrent()

        assertTrue(running.isCompleted, "stop chỉ trả về khi vòng đã dừng hẳn")
        assertIs<AppResult.Failure>(running.await())
        assertEquals(1, remote.pushedBatches.size)
        assertTrue(remote.pulledCursors.isEmpty())
        assertEquals(2L, unsent())
        assertEquals(SyncStatus.Idle, engine.status.value)
    }
}
