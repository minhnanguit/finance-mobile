package com.uit.finance.core.ledger.data

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.SpendingRepositoryImpl
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth

/** "Đã chi" cho ngân sách (ADR-007 §1) trên schema thật: con cộng vào cha, chỉ chi đã xác nhận, biên tháng, biên Long. */
class SpendingRepositoryTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val october = YearMonth(2026, 10)
    private var nextId = 0

    private fun TestScope.spending() = testDispatcherProvider().let { dispatchers ->
        SpendingRepositoryImpl(ledgerStore(databases, FakeScheduler(), dispatchers), dispatchers)
    }

    private fun category(id: String, parentId: String? = null, kind: String = "EXPENSE") =
        database.categoryQueries.upsert(id, kind, id, parentId, null, null, null, 0, 0)

    private fun tx(
        categoryId: String?,
        amount: Long,
        on: String = "2026-10-15",
        type: String = "EXPENSE",
        status: String = "CONFIRMED",
        currency: String = "VND",
        deleted: Long = 0,
        counterAccountId: String? = null,
    ) = database.ledgerTransactionQueries.upsert(
        "t${++nextId}", type, status, "wallet", counterAccountId, amount, currency, categoryId, on, null, null, null, deleted,
    )

    private suspend fun TestScope.octoberSpend(): List<CategorySpend> {
        var result = emptyList<CategorySpend>()
        spending().observeMonthlyExpense(october).test {
            result = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return result
    }

    @Test
    fun `chi của danh mục con cộng vào danh mục cha`() = runTest {
        category("food")
        category("coffee", parentId = "food")
        tx("food", 2_000_000)
        tx("coffee", 550_000)

        assertEquals(listOf(CategorySpend("food", Money(2_550_000, "VND"))), octoberSpend())
    }

    @Test
    fun `bỏ thu - chuyển tiền - nháp - khoản đã xoá - giữ phí và ví đã archive`() = runTest {
        category("food")
        category("salary", kind = "INCOME")
        category("fee")
        tx("food", 100)
        tx("salary", 9_999, type = "INCOME")
        tx(null, 8_888, type = "TRANSFER", counterAccountId = "other")
        tx("food", 7_777, status = "DRAFT")
        tx("food", 6_666, deleted = 1)
        tx("fee", 5)

        assertEquals(
            listOf(CategorySpend("fee", Money(5, "VND")), CategorySpend("food", Money(100, "VND"))),
            octoberSpend(),
        )
    }

    @Test
    fun `tách theo tiền tệ - không cộng VND với USD`() = runTest {
        category("food")
        tx("food", 2_550_000)
        tx("food", 20_00, currency = "USD")

        assertEquals(
            listOf(CategorySpend("food", Money(20_00, "USD")), CategorySpend("food", Money(2_550_000, "VND"))),
            octoberSpend(),
        )
    }

    @Test
    fun `biên tháng - tính ngày đầu và ngày cuối - bỏ ngày liền trước và liền sau`() = runTest {
        category("food")
        tx("food", 1, on = "2026-09-30")
        tx("food", 10, on = "2026-10-01")
        tx("food", 100, on = "2026-10-31")
        tx("food", 1_000, on = "2026-11-01")

        assertEquals(listOf(CategorySpend("food", Money(110, "VND"))), octoberSpend())
    }

    @Test
    fun `danh mục chưa sync tới thì coi chính nó là gốc`() = runTest {
        tx("not-yet-pulled", 42)

        assertEquals(listOf(CategorySpend("not-yet-pulled", Money(42, "VND"))), octoberSpend())
    }

    @Test
    fun `chính xác gần biên Long và kẹt ở biên khi vượt - không làm chết Flow`() = runTest {
        category("near")
        category("over")
        database.transaction {
            repeat(9_000) { tx("near", LedgerLimits.MAX_AMOUNT_MINOR) } // 9 × 10¹⁸
            repeat(3) { tx("near", 999_999_999) }
            repeat(10_000) { tx("over", LedgerLimits.MAX_AMOUNT_MINOR) } // 10¹⁹ > Long.MAX
        }

        val byCategory = octoberSpend().associate { it.categoryId to it.spent.amountMinor }

        assertEquals(9_000 * LedgerLimits.MAX_AMOUNT_MINOR + 3 * 999_999_999L, byCategory["near"])
        assertEquals(Long.MAX_VALUE, byCategory["over"])
    }

    @Test
    fun `tự cập nhật khi ghi giao dịch mới`() = runTest {
        category("food")

        spending().observeMonthlyExpense(october).test {
            assertEquals(emptyList(), awaitItem())
            tx("food", 300)
            assertEquals(listOf(CategorySpend("food", Money(300, "VND"))), awaitItem())
        }
    }
}
