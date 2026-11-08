package com.uit.finance.core.ledger.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.data.repository.SpendingRepositoryImpl
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.CategoryTotal
import com.uit.finance.core.ledger.domain.model.MonthlyTotal
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.PlanRecordingDriver
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Query báo cáo (docs/REPORTS-PLAN.md R3, R4, R7) trên schema thật. Phần "đã chi" của ngân sách: SpendingRepositoryTest. */
class ReportQueriesTest {

    private val driver = PlanRecordingDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
    private val database = FinanceDatabase(driver).also { FinanceDatabase.Schema.create(driver) }
    private val databases = FakeDatabases(database)
    private var nextId = 0

    private val octoberStart = LocalDate(2026, 10, 1)
    private val octoberEnd = LocalDate(2026, 10, 31)

    private fun TestScope.spending() = testDispatcherProvider().let { dispatchers ->
        SpendingRepositoryImpl(ledgerStore(databases, FakeScheduler(), dispatchers), dispatchers)
    }

    private fun TestScope.balances() = testDispatcherProvider().let { dispatchers ->
        BalanceRepositoryImpl(ledgerStore(databases, FakeScheduler(), dispatchers), dispatchers)
    }

    private fun category(id: String, parentId: String? = null, kind: String = "EXPENSE", archived: Long = 0) =
        database.categoryQueries.upsert(id, kind, id, parentId, null, null, null, archived, 0)

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
        "t${++nextId}", type, status, "wallet", counterAccountId, amount, currency, categoryId, on, null, null, null, deleted, null,
    )

    private suspend fun TestScope.octoberTotals(kind: CategoryKind = CategoryKind.EXPENSE, groupByParent: Boolean = true) =
        spending().observeCategoryTotals(octoberStart, octoberEnd, kind, groupByParent).first()

    /** Dữ liệu đủ mọi trường hợp: con, danh mục chưa về máy, không danh mục, archive, nháp, xoá, chuyển, 2 tiền tệ. */
    private fun seedEverything() {
        category("food")
        category("coffee", parentId = "food")
        category("old", archived = 1)
        category("salary", kind = "INCOME")
        tx("food", 1_000)
        tx("coffee", 200)
        tx("old", 30)
        tx("not-yet-pulled", 4)
        tx(null, 5)
        tx("fee", 6)
        tx("food", 70_00, currency = "USD")
        tx("salary", 9_000, type = "INCOME")
        tx(null, 800, type = "INCOME")
        tx("food", 7_777, status = "DRAFT")
        tx("food", 6_666, deleted = 1)
        tx(null, 8_888, type = "TRANSFER", counterAccountId = "other")
        tx("food", 1, on = "2026-09-30")
        tx("food", 1, on = "2026-11-01")
    }

    @Test
    fun `bất biến - cộng mọi nhóm của từng tiền tệ bằng thu chi ở Trang chủ`() = runTest {
        seedEverything()
        val cashFlow = balances().observeCashFlow(octoberStart, octoberEnd).first()

        for (groupByParent in listOf(true, false)) {
            val expense = octoberTotals(CategoryKind.EXPENSE, groupByParent).sumByCurrency()
            val income = octoberTotals(CategoryKind.INCOME, groupByParent).sumByCurrency()

            assertEquals(cashFlow.expense.associate { it.currency to it.amountMinor }, expense)
            assertEquals(cashFlow.income.associate { it.currency to it.amountMinor }, income)
        }
    }

    @Test
    fun `gộp con vào cha - không có danh mục ra nhóm null - danh mục chưa về máy là chính nó`() = runTest {
        seedEverything()

        assertEquals(
            listOf(
                CategoryTotal(null, Money(5, "VND")),
                CategoryTotal("fee", Money(6, "VND")),
                CategoryTotal("food", Money(70_00, "USD")),
                CategoryTotal("food", Money(1_200, "VND")),
                CategoryTotal("not-yet-pulled", Money(4, "VND")),
                CategoryTotal("old", Money(30, "VND")),
            ),
            octoberTotals(),
        )
    }

    @Test
    fun `groupByParent false ra từng danh mục con`() = runTest {
        seedEverything()

        val vnd = octoberTotals(groupByParent = false).filter { it.total.currency == "VND" }.associate { it.categoryId to it.total.amountMinor }

        assertEquals(1_000L, vnd["food"])
        assertEquals(200L, vnd["coffee"])
    }

    @Test
    fun `thu - chỉ lấy INCOME`() = runTest {
        seedEverything()

        assertEquals(
            listOf(CategoryTotal(null, Money(800, "VND")), CategoryTotal("salary", Money(9_000, "VND"))),
            octoberTotals(CategoryKind.INCOME),
        )
    }

    @Test
    fun `biên Long - kẹt ở biên thay vì làm chết Flow`() = runTest {
        category("over")
        database.transaction { repeat(10_000) { tx("over", LedgerLimits.MAX_AMOUNT_MINOR) } } // 10¹⁹ > Long.MAX

        assertEquals(listOf(CategoryTotal("over", Money(Long.MAX_VALUE, "VND"))), octoberTotals())
    }

    @Test
    fun `tự cập nhật khi ghi giao dịch mới`() = runTest {
        spending().observeCategoryTotals(octoberStart, octoberEnd, CategoryKind.EXPENSE, groupByParent = true).test {
            assertEquals(emptyList(), awaitItem())
            tx(null, 300)
            assertEquals(listOf(CategoryTotal(null, Money(300, "VND"))), awaitItem())
        }
    }

    @Test
    fun `theo tháng - gom theo tháng loại tiền tệ - tháng trống không có dòng - bỏ nháp chuyển xoá`() = runTest {
        tx(null, 10, on = "2026-08-31")
        tx(null, 20, on = "2026-09-01")
        tx(null, 30, on = "2026-09-30", type = "INCOME")
        tx(null, 40, on = "2026-11-30", currency = "USD")
        tx(null, 50, on = "2026-12-01")
        tx(null, 1, on = "2026-09-15", status = "DRAFT")
        tx(null, 1, on = "2026-09-15", deleted = 1)
        tx(null, 1, on = "2026-09-15", type = "TRANSFER", counterAccountId = "other")

        val totals = spending().observeMonthlyTotals(YearMonth(2026, 9), YearMonth(2026, 11)).first()

        assertEquals(
            listOf(
                MonthlyTotal(YearMonth(2026, 9), TransactionType.EXPENSE, Money(20, "VND")),
                MonthlyTotal(YearMonth(2026, 9), TransactionType.INCOME, Money(30, "VND")),
                MonthlyTotal(YearMonth(2026, 11), TransactionType.EXPENSE, Money(40, "USD")),
            ),
            totals,
        )
    }

    @Test
    fun `theo tháng - năm nhuận có 29-2 và khoảng vắt qua năm`() = runTest {
        tx(null, 1, on = "2027-12-31")
        tx(null, 2, on = "2028-01-01")
        tx(null, 3, on = "2028-02-29")
        tx(null, 4, on = "2028-03-01")

        val totals = spending().observeMonthlyTotals(YearMonth(2027, 12), YearMonth(2028, 2)).first()

        assertEquals(listOf(1L, 2L, 3L), totals.map { it.total.amountMinor })
        assertEquals(listOf(YearMonth(2027, 12), YearMonth(2028, 1), YearMonth(2028, 2)), totals.map { it.month })
    }

    @Test
    fun `query plan - cả hai câu đi theo index ngày`() = runTest {
        driver.plans.clear()
        octoberTotals()
        spending().observeMonthlyTotals(YearMonth(2026, 5), YearMonth(2026, 10)).first()

        val plans = driver.plans.filterKeys { "ledger_transaction" in it && "GROUP BY" in it }.values
        assertEquals(2, plans.size, driver.plans.keys.toString())
        plans.forEach { plan ->
            assertTrue(plan.any { it.contains("USING INDEX ledger_transaction_occurred_idx") }, plan.toString())
            assertFalse(plan.any { it.startsWith("SCAN") && it.contains("ledger_transaction") && "INDEX" !in it }, plan.toString())
        }
    }

    private fun List<CategoryTotal>.sumByCurrency(): Map<String, Long> =
        groupBy { it.total.currency }.mapValues { (_, totals) -> totals.sumOf { it.total.amountMinor } }
}
