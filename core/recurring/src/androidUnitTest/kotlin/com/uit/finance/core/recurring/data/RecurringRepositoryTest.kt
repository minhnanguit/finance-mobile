@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.occurrence.ledgerOccurrenceWriter
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.RecurringPayload
import com.uit.finance.core.network.api.model.RecurringPayloadCodec
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.recurring.data.local.RecurringStore
import com.uit.finance.core.recurring.data.repository.RecurringRepositoryImpl
import com.uit.finance.core.recurring.data.sync.RecurringSyncApplier
import com.uit.finance.core.recurring.domain.model.OccurrenceIds
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.usecase.GenerateDueOccurrencesUseCase
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.scheduler.SyncScheduler
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * core/recurring trên schema thật: khoản + outbox (CAS, không gộp), sinh kỳ exactly-once trong cùng transaction,
 * kỳ đã bỏ qua không quay lại, chặn R13, applier và revision_conflict.
 */
class RecurringRepositoryTest {

    private val database = FinanceDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { FinanceDatabase.Schema.create(it) })
    private val databases = object : UserDatabaseProvider {
        override val database = MutableStateFlow<FinanceDatabase?>(this@RecurringRepositoryTest.database)
    }
    private var syncRequests = 0
    private val scheduler = object : SyncScheduler {
        override fun schedulePeriodic() = Unit
        override fun requestImmediate() { syncRequests++ }
        override fun cancelAll() = Unit
    }
    private val ids = FakeUuidGenerator()
    private val clock = TestClock(Instant.parse("2026-11-10T12:00:00Z"))
    private val today = LocalDate(2026, 11, 10)
    private val codec = FakeRecurringCodec()

    private fun TestScope.repository(): RecurringRepositoryImpl {
        val dispatchers = testDispatcherProvider()
        val outbox = OutboxWriter(ids, clock)
        return RecurringRepositoryImpl(
            RecurringStore(databases, scheduler, dispatchers),
            outbox,
            codec,
            ledgerOccurrenceWriter(outbox, NoopLedgerCodec),
            ids,
            clock,
            dispatchers,
        )
    }

    private fun account(id: String = "wallet", currency: String = "VND", archived: Boolean = false) =
        database.accountQueries.upsert(id, "Ví", "CASH", currency, 0, 0, if (archived) 1 else 0, 0)

    private fun category(id: String = "rent", kind: String = "EXPENSE") =
        database.categoryQueries.upsert(id, kind, id, null, null, null, null, 0, 0)

    private fun draft(start: LocalDate, day: Int = 5, backfill: Boolean = true, autoConfirm: Boolean = false) = RecurringDraft(
        name = "Tiền nhà", type = RecurringType.EXPENSE, accountId = "wallet", categoryId = "rent", currency = "VND",
        amountMinor = 3_000_000, note = null, rule = RecurrenceRule.monthly(day), startDate = start, endDate = null,
        remindDaysBefore = 2, autoConfirm = autoConfirm, backfill = backfill,
    )

    private suspend fun RecurringRepositoryImpl.created(draft: RecurringDraft): Recurring =
        (create(draft) as AppResult.Success).value

    private fun outboxActions() = database.outboxQueries.selectAll().executeAsList().map { "${it.entity}:${it.action}" }

    private fun transactions() = database.ledgerTransactionQueries.selectRecent(100).executeAsList()

    @Test
    fun `tạo khoản - op UPSERT gửi revision 0 và revision local thành 1 - có ref tới ví và danh mục`() = runTest {
        account(); category()
        val recurring = repository().created(draft(LocalDate(2026, 11, 5)))

        assertEquals(1, recurring.revision)
        assertEquals(0, codec.payloads.single().revision)
        val op = database.outboxQueries.selectAll().executeAsList().single()
        assertEquals(SyncEntity.RECURRING to "UPSERT", op.entity to op.action)
        val refs = database.outboxQueries.selectRefs(op.seq).executeAsList().map { it.entity }
        assertEquals(listOf(SyncEntity.ACCOUNT, SyncEntity.CATEGORY), refs.sorted())
    }

    @Test
    fun `hai lần sửa offline liên tiếp - hai op riêng với revision 1 rồi 2 - không gộp`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))

        repo.updateDetails(recurring.id, "Nhà trọ", 1)
        repo.updateDetails(recurring.id, "Nhà trọ quận 10", 1)

        assertEquals(listOf(0L, 1L, 2L), codec.payloads.map { it.revision })
        assertEquals(3, database.outboxQueries.selectAll().executeAsList().size)
    }

    @Test
    fun `sinh kỳ không ghi gì thì không hẹn sync - tránh vòng sync rồi sinh kỳ rồi sync không dừng`() = runTest {
        account(); category()
        val repo = repository()
        repo.created(draft(LocalDate(2026, 11, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)
        assertEquals(1, generate().written)

        syncRequests = 0
        assertEquals(0, generate().written)

        assertEquals(0, syncRequests)
    }

    @Test
    fun `sinh kỳ - đúng một giao dịch nháp id UUIDv5 + op CREATE - chạy lại không trùng`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)

        assertEquals(1, generate().written)
        assertEquals(0, generate().written)

        val tx = transactions().single()
        assertEquals(OccurrenceIds.of(recurring.id, LocalDate(2026, 11, 5)), tx.id)
        assertEquals("DRAFT", tx.status)
        assertEquals("Tiền nhà", tx.payee)
        assertTrue("${SyncEntity.TRANSACTION}:CREATE" in outboxActions())
        val createSeq = database.outboxQueries.selectAll().executeAsList().first { it.action == "CREATE" }.seq
        assertTrue(database.outboxQueries.selectRefs(createSeq).executeAsList().any { it.entity == SyncEntity.RECURRING })
    }

    @Test
    fun `bắt đầu quá khứ - bù tối đa 90 ngày và kỳ cũ luôn là nháp kể cả tự xác nhận`() = runTest {
        account(); category()
        val repo = repository()
        repo.created(draft(LocalDate(2025, 1, 10), day = 10, autoConfirm = true))

        GenerateDueOccurrencesUseCase(repo, clock)()

        val dates = transactions().map { it.occurred_on }.sorted()
        // 90 ngày tính cả hôm nay: từ 13/08, nên 10/08 nằm ngoài.
        assertEquals(listOf("2026-09-10", "2026-10-10", "2026-11-10"), dates)
        val byDate = transactions().associate { it.occurred_on to it.status }
        assertEquals("DRAFT", byDate["2026-10-10"])
        // Kỳ hôm nay theo lịch chưa được server nhận (còn op chờ) vẫn là nháp (R9).
        assertEquals("DRAFT", byDate["2026-11-10"])
    }

    @Test
    fun `chọn chỉ từ hôm nay - không bù kỳ cũ`() = runTest {
        account(); category()
        val repo = repository()
        repo.created(draft(LocalDate(2025, 1, 10), day = 10, backfill = false))

        GenerateDueOccurrencesUseCase(repo, clock)()

        assertEquals(listOf("2026-11-10"), transactions().map { it.occurred_on })
    }

    @Test
    fun `kỳ đã bỏ qua không bao giờ được sinh lại`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)
        generate()
        database.ledgerTransactionQueries.markDeleted(OccurrenceIds.of(recurring.id, LocalDate(2026, 11, 5)))

        assertEquals(0, generate().written)
        assertEquals(1, database.ledgerTransactionQueries.selectById(OccurrenceIds.of(recurring.id, LocalDate(2026, 11, 5))).executeAsList().size)
    }

    @Test
    fun `kỳ đã bỏ qua không quay lại kể cả khi tombstone local mất sau 410 cursor_expired`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)
        generate()
        val skipped = OccurrenceIds.of(recurring.id, LocalDate(2026, 11, 5))
        database.ledgerTransactionQueries.remove(skipped) // dòng ledger + tombstone đều mất khi tải lại sổ

        assertEquals(0, generate().written)
        assertTrue(database.ledgerTransactionQueries.selectById(skipped).executeAsList().isEmpty())
    }

    @Test
    fun `ví đã lưu trữ - không sinh và gắn chặn - gỡ chặn bằng bỏ qua thì không bù`() = runTest {
        account(archived = true); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 10, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)

        assertEquals(1, generate().blocked)
        assertEquals(LocalDate(2026, 10, 5), repo.snapshot().single().blockedFrom)
        assertEquals(0, generate().written)

        account(archived = false)
        repo.skipBlocked(recurring.id)
        assertEquals(0, generate().written)
        assertTrue(transactions().isEmpty())
    }

    @Test
    fun `gỡ chặn bằng ghi bù - các kỳ bị chặn được sinh thành nháp`() = runTest {
        account(archived = true); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 10, 5)))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)
        generate()

        account(archived = false)
        repo.backfillBlocked(recurring.id)

        assertEquals(2, generate().written)
        assertTrue(transactions().all { it.status == "DRAFT" })
    }

    @Test
    fun `đổi giá từ tháng sau - giữ anchor - phiên bản tương lai bị thay khi sửa tiếp`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 1, 1)))
        val change = RevisionChange(LocalDate(2026, 12, 1), "wallet", "rent", 3_200_000, null, RecurrenceRule.monthly(5), false)

        val raised = (repo.revise(recurring.id, change) as AppResult.Success).value
        val again = (repo.revise(recurring.id, change.copy(amountMinor = 3_300_000)) as AppResult.Success).value

        assertEquals(LocalDate(2026, 1, 1), raised.revisions.last().anchorDate)
        assertEquals(2, again.revisions.size)
        assertEquals(3_300_000, again.revisions.last().amountMinor)
    }

    @Test
    fun `phiên bản mới trong quá khứ hoặc kéo dài khoản đã kết thúc - bị chặn ở máy`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 1, 1)))
        val past = RevisionChange(LocalDate(2026, 11, 1), "wallet", "rent", 1, null, RecurrenceRule.monthly(5), false)

        val backdated = repo.revise(recurring.id, past)
        repo.end(recurring.id, LocalDate(2026, 12, 31))
        val extended = repo.end(recurring.id, LocalDate(2027, 6, 30))

        assertEquals(RecurringErrorCode.INVALID_FIELD, ((backdated as AppResult.Failure).error as AppError.Validation).fieldErrors.single().message)
        assertEquals(RecurringErrorCode.IMMUTABLE_FIELD, ((extended as AppResult.Failure).error as AppError.Validation).fieldErrors.single().message)
    }

    @Test
    fun `applier - nhận bản server đặt revision local theo server và giữ trạng thái chặn của máy`() = runTest {
        account(archived = true); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 10, 5)))
        GenerateDueOccurrencesUseCase(repo, clock)()
        val applier = RecurringSyncApplier(codec)
        val serverVersion = codec.payloads.last().copy(revision = 7, name = "Từ máy khác")

        applier.apply(database, RemoteChange(SyncEntity.RECURRING, recurring.id, 9, false, codec.encode(serverVersion)))

        val local = repo.snapshot().single()
        assertEquals(7, local.revision)
        assertEquals("Từ máy khác", local.name)
        assertEquals(LocalDate(2026, 10, 5), local.blockedFrom)
        assertTrue(applier.dropPendingOnConflict(RecurringErrorCode.REVISION_CONFLICT))
    }

    @Test
    fun `xoá khoản - giao dịch đã sinh vẫn còn`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))
        GenerateDueOccurrencesUseCase(repo, clock)()

        repo.delete(recurring.id)

        assertTrue(repo.snapshot().isEmpty())
        assertEquals(1, transactions().size)
        assertNull(database.recurringQueries.selectAll().executeAsOneOrNull())
    }

    @Test
    fun `revision đổi giữa lúc tính - kỳ không được ghi theo lịch cũ`() = runTest {
        account(); category()
        val repo = repository()
        val recurring = repo.created(draft(LocalDate(2026, 11, 5)))
        val stale = repo.snapshot().single()
        repo.updateDetails(recurring.id, "Đổi tên", 2)
        val occurrence = com.uit.finance.core.recurring.domain.engine.RecurrenceEngine.occurrences(stale, today, today)
        val first = com.uit.finance.core.recurring.domain.engine.RecurrenceEngine.occurrences(stale, LocalDate(2026, 11, 1), today).single()

        val result = repo.recordOccurrence(stale, first, com.uit.finance.core.recurring.domain.model.OccurrenceStatus.DRAFT)

        assertTrue(occurrence.isEmpty())
        assertEquals(com.uit.finance.core.recurring.domain.model.OccurrenceResult.STALE, result)
        assertTrue(transactions().isEmpty())
    }

    @Test
    fun `đồng hồ sang ngày mới - sinh thêm đúng kỳ mới`() = runTest {
        account(); category()
        val repo = repository()
        repo.created(draft(LocalDate(2026, 11, 5), day = 11))
        val generate = GenerateDueOccurrencesUseCase(repo, clock)
        assertEquals(0, generate().written)

        clock.advanceBy(1.days)

        assertEquals(1, generate().written)
    }
}

/** Codec giả: giữ payload thật trong bộ nhớ, JSON chỉ mang số tham chiếu. */
private class FakeRecurringCodec : RecurringPayloadCodec {
    val payloads = mutableListOf<RecurringPayload>()

    override fun encode(payload: RecurringPayload): JsonObject {
        payloads += payload
        return buildJsonObject { put("ref", JsonPrimitive(payloads.lastIndex)) }
    }

    override fun decodeRecurring(data: JsonObject): RecurringPayload = payloads[data.getValue("ref").jsonPrimitive.int]
}

private object NoopLedgerCodec : SyncPayloadCodec {
    override fun encode(payload: AccountPayload): JsonObject = JsonObject(emptyMap())
    override fun encode(payload: CategoryPayload): JsonObject = JsonObject(emptyMap())
    override fun encode(payload: TransactionPayload): JsonObject = JsonObject(emptyMap())
    override fun decodeAccount(data: JsonObject): AccountPayload = error("unused")
    override fun decodeCategory(data: JsonObject): CategoryPayload = error("unused")
    override fun decodeTransaction(data: JsonObject): TransactionPayload = error("unused")
}
