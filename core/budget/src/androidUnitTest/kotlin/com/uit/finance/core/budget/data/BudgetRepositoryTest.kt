package com.uit.finance.core.budget.data

import app.cash.turbine.test
import com.uit.finance.core.budget.data.local.BudgetStore
import com.uit.finance.core.budget.data.repository.BudgetRepositoryImpl
import com.uit.finance.core.budget.data.sync.BudgetSyncApplier
import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.model.BudgetErrorCode
import com.uit.finance.core.budget.domain.validation.BudgetLimits
import com.uit.finance.core.budget.support.FakeBudgetCodec
import com.uit.finance.core.budget.support.FakeDatabases
import com.uit.finance.core.budget.support.FakeScheduler
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.network.api.model.BudgetPayload
import com.uit.finance.core.network.api.model.SyncEntity
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.JsonObject

/** core/budget trên schema thật: bản ghi + outbox + outbox_ref cùng transaction, luật G3, giới hạn, applier. */
class BudgetRepositoryTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val scheduler = FakeScheduler()
    private val codec = FakeBudgetCodec()
    private val ids = FakeUuidGenerator()
    private val clock = TestClock()
    private val october = YearMonth(2026, 10)
    private val applier = BudgetSyncApplier(codec)

    private fun TestScope.repository(): BudgetRepositoryImpl {
        val dispatchers = testDispatcherProvider()
        return BudgetRepositoryImpl(
            BudgetStore(databases, scheduler, dispatchers),
            OutboxWriter(ids, clock),
            codec,
            ids,
            dispatchers,
        )
    }

    private fun draft(categoryId: String = "food", currency: String = "VND", amount: Long = 3_000_000) =
        BudgetDraft(categoryId, currency, amount, october)

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).value

    private fun AppResult<*>.fieldErrors(): List<FieldError> = ((this as AppResult.Failure).error as AppError.Validation).fieldErrors

    private fun outboxActions() = database.outboxQueries.selectAll().executeAsList().map { "${it.entity}:${it.action}" }

    private fun lastPayload(): BudgetPayload = codec.payloads.last()

    // ---- Ghi cùng outbox ----

    @Test
    fun `tạo ngân sách ghi cả dòng budget, op UPSERT và tham chiếu tới danh mục, rồi hẹn sync`() = runTest {
        val budget = repository().create(draft()).value()

        val row = database.budgetQueries.selectById(budget.id).executeAsOne()
        assertEquals(listOf("food", "VND", 3_000_000L, "2026-10-01"), listOf(row.category_id, row.currency, row.amount_minor, row.start_month))
        assertEquals(listOf("budget:UPSERT"), outboxActions())
        val seq = database.outboxQueries.selectAll().executeAsOne().seq
        assertEquals(listOf(SyncEntity.CATEGORY to "food"), database.outboxQueries.selectRefs(seq).executeAsList().map { it.entity to it.entity_id })
        assertEquals(BudgetPayload("food", "VND", 3_000_000, LocalDate(2026, 10, 1)), lastPayload())
        assertEquals(1, scheduler.immediateRequests)
    }

    @Test
    fun `cùng danh mục - cùng tiền tệ là duplicate, khác tiền tệ thì được (G3)`() = runTest {
        val repository = repository()
        repository.create(draft()).value()

        val duplicate = repository.create(draft(amount = 9_000_000))
        val usd = repository.create(draft(currency = "USD", amount = 100_00))

        assertEquals(listOf(FieldError("categoryId", BudgetErrorCode.DUPLICATE)), duplicate.fieldErrors())
        assertEquals("USD", usd.value().currency)
        assertEquals(listOf("budget:UPSERT", "budget:UPSERT"), outboxActions())
    }

    @Test
    fun `xoá rồi tạo lại cho cùng cặp thì được, với id mới`() = runTest {
        val repository = repository()
        val first = repository.create(draft()).value()
        repository.delete(first.id).value()

        val second = repository.create(draft()).value()

        assertEquals(false, first.id == second.id)
    }

    @Test
    fun `tối đa 100 ngân sách chưa xoá`() = runTest {
        val repository = repository()
        repeat(BudgetLimits.MAX_BUDGETS) { repository.create(draft(categoryId = "c$it")).value() }

        val overLimit = repository.create(draft(categoryId = "extra"))

        assertEquals(listOf(FieldError("budget", BudgetErrorCode.LIMIT_REACHED)), overLimit.fieldErrors())
        assertEquals(BudgetLimits.MAX_BUDGETS.toLong(), database.budgetQueries.countActive().executeAsOne())
    }

    @Test
    fun `ngân sách người gọi loại khỏi điều kiện đếm thì không chiếm chỗ giới hạn (GS8)`() = runTest {
        val repository = repository()
        repeat(BudgetLimits.MAX_BUDGETS) { repository.create(draft(categoryId = "c$it")).value() }

        val created = repository.create(draft(categoryId = "extra")) { it.categoryId != "c0" }

        assertEquals("extra", created.value().categoryId)
    }

    @Test
    fun `field sai bị chặn trước khi ghi - không có op nào`() = runTest {
        val repository = repository()

        val zero = repository.create(draft(amount = 0))
        val tooBig = repository.create(draft(amount = BudgetLimits.MAX_AMOUNT_MINOR + 1))
        val lowercase = repository.create(draft(currency = "vnd"))
        val tooOld = repository.create(draft().copy(startMonth = YearMonth(1999, 12)))

        assertEquals("amountMinor", zero.fieldErrors().single().field)
        assertEquals("amountMinor", tooBig.fieldErrors().single().field)
        assertEquals("currency", lowercase.fieldErrors().single().field)
        assertEquals("startMonth", tooOld.fieldErrors().single().field)
        assertEquals(emptyList(), outboxActions())
        assertEquals(0, scheduler.immediateRequests)
    }

    // ---- Sửa, xoá ----

    @Test
    fun `sửa số tiền gộp vào op cuối hàng - gửi y nguyên không sinh op`() = runTest {
        val repository = repository()
        val budget = repository.create(draft()).value()

        repository.updateAmount(budget.id, 5_000_000).value()
        repository.updateAmount(budget.id, 5_000_000).value()

        assertEquals(5_000_000L, database.budgetQueries.selectById(budget.id).executeAsOne().amount_minor)
        assertEquals(listOf("budget:UPSERT"), outboxActions())
        assertEquals(5_000_000L, lastPayload().amountMinor)
    }

    @Test
    fun `xoá để lại tombstone và op DELETE - xoá hoặc sửa lần nữa là not_found`() = runTest {
        val repository = repository()
        val budget = repository.create(draft()).value()

        repository.delete(budget.id).value()

        assertEquals(1L, database.budgetQueries.selectById(budget.id).executeAsOne().deleted)
        assertEquals(listOf("budget:UPSERT", "budget:DELETE"), outboxActions())
        assertEquals(BudgetErrorCode.NOT_FOUND, repository.delete(budget.id).fieldErrors().single().message)
        assertEquals(BudgetErrorCode.NOT_FOUND, repository.updateAmount(budget.id, 1).fieldErrors().single().message)
    }

    @Test
    fun `observe chỉ phát ngân sách chưa xoá và tự cập nhật`() = runTest {
        val repository = repository()

        repository.observeBudgets().test {
            assertEquals(emptyList(), awaitItem())
            val budget = repository.create(draft()).value()
            assertEquals(listOf(budget), awaitItem())
            repository.delete(budget.id).value()
            assertEquals(emptyList(), awaitItem())
        }
    }

    // ---- Thứ tự gửi (GS5) ----

    @Test
    fun `op đổi danh mục phải đợi op ngân sách đang trỏ tới nó`() = runTest {
        val outbox = OutboxWriter(ids, clock)
        database.transaction { outbox.upsert(database, SyncEntity.CATEGORY, "food", JsonObject(emptyMap())) }
        repository().create(draft()).value()
        database.transaction { outbox.upsert(database, SyncEntity.CATEGORY, "food", JsonObject(emptyMap())) }
        // Op tạo danh mục đã được server nhận.
        database.outboxQueries.deleteByOpId(database.outboxQueries.selectAll().executeAsList().first().op_id)

        val eligible = database.outboxQueries.selectEligible(now = Long.MAX_VALUE, limit = 10).executeAsList()

        assertEquals(listOf(SyncEntity.BUDGET), eligible.map { it.entity })
    }

    // ---- Áp thay đổi từ server ----

    private fun remote(id: String, seq: Long, payload: BudgetPayload?, deleted: Boolean = false) = RemoteChange(
        entity = SyncEntity.BUDGET,
        id = id,
        changeSeq = seq,
        deleted = deleted,
        data = payload?.let { codec.encode(it) },
    )

    @Test
    fun `applier ghi bản của server, áp tombstone`() = runTest {
        val repository = repository()
        val payload = BudgetPayload("food", "VND", 2_000_000, LocalDate(2026, 9, 1))

        database.transaction { applier.apply(database, remote("b-server", 1, payload)) }
        repository.observeBudgets().test {
            assertEquals(listOf(Budget("b-server", "food", Money(2_000_000, "VND"), YearMonth(2026, 9))), awaitItem())
            database.transaction { applier.apply(database, remote("b-server", 2, null, deleted = true)) }
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `2 máy cùng tạo cho một cặp - bản bị REJECTED duplicate bị bỏ, bản của máy kia về qua pull (GS4)`() = runTest {
        val repository = repository()
        val mine = repository.create(draft()).value()

        // Engine gọi discard khi op tạo bị REJECTED mà server trả current = null.
        database.transaction { applier.discard(database, mine.id) }
        database.transaction {
            applier.apply(database, remote("b-other-device", 7, BudgetPayload("food", "VND", 4_000_000, LocalDate(2026, 10, 1))))
        }

        assertNull(database.budgetQueries.selectById(mine.id).executeAsOneOrNull())
        assertEquals(listOf("b-other-device"), database.budgetQueries.selectActive().executeAsList().map { it.id })
    }
}
