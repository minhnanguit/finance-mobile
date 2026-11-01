package com.uit.finance.feature.reports.domain

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.feature.reports.domain.model.ReportOverview
import com.uit.finance.feature.reports.testing.ReportFixture
import com.uit.finance.feature.reports.testing.expenseCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class ReportUseCasesTest {

    private val fixture = ReportFixture()
    private val october = ReportPeriod.Month(YearMonth(2026, 10))
    private val oct10 = LocalDate(2026, 10, 10)

    private suspend fun overview(
        period: ReportPeriod = october,
        today: LocalDate = oct10,
        kind: CategoryKind = CategoryKind.EXPENSE,
        currency: String? = null,
    ): ReportOverview = fixture.overview(period, kind, currency, today).first()

    private fun day(month: Int, day: Int, year: Int = 2026) = LocalDate(year, month, day)

    @Test
    fun `thu chi bằng đúng nguồn của Trang chủ - tổng các nhóm bằng tổng chi`() = runTest {
        fixture.categories(expenseCategory("food"), expenseCategory("coffee", parentId = "food"))
        fixture.spend("food", 1_000, day(10, 1))
        fixture.spend("coffee", 200, day(10, 2))
        fixture.spend(null, 30, day(10, 3))
        fixture.spend("not-yet-pulled", 4, day(10, 4))
        fixture.earn(null, 9_000, day(10, 5))

        val result = overview()

        assertEquals(Money(1_234, "VND"), result.expense)
        assertEquals(Money(9_000, "VND"), result.income)
        assertEquals(result.expense.amountMinor, result.shares.sumOf { it.total.amountMinor })
        assertEquals(listOf("food", null), result.shares.map { it.categoryId })
        assertEquals(Money(34, "VND"), result.shares.last().total) // không danh mục + danh mục chưa về máy
        assertTrue(result.shares.first().hasChildren)
    }

    @Test
    fun `top 5 cộng Khác - 6 nhóm thì hiện đủ không gộp`() = runTest {
        val ids = (1..7).map { "c$it" }
        fixture.categories(*ids.map { expenseCategory(it) }.toTypedArray())
        ids.forEachIndexed { index, id -> fixture.spend(id, (100 - index * 10).toLong(), day(10, 1)) }

        val seven = overview()
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), seven.top.map { it.categoryId })
        assertEquals(listOf("c6", "c7"), seven.others.map { it.categoryId })
        assertEquals(Money(90, "VND"), seven.othersTotal)

        fixture.spending.transactions.value = fixture.spending.transactions.value.filterNot { it.categoryId == "c7" }
        val six = overview()
        assertEquals(6, six.top.size)
        assertTrue(six.others.isEmpty())
    }

    @Test
    fun `phần trăm làm tròn xuống - tổng không vượt 100`() = runTest {
        fixture.categories(expenseCategory("a"), expenseCategory("b"), expenseCategory("c"))
        listOf("a", "b", "c").forEach { fixture.spend(it, 1, day(10, 1)) }

        val result = overview()

        assertEquals(listOf(33.3, 33.3, 33.3), result.shares.map { it.percent })
        assertTrue(result.shares.sumOf { it.percent } <= 100.0)
    }

    @Test
    fun `phần trăm vẫn không vượt 100 khi tổng tiền vượt biên Long`() = runTest {
        fixture.categories(expenseCategory("a"), expenseCategory("b"))
        fixture.spend("a", 6_000_000_000_000_000_000, day(10, 1))
        fixture.spend("b", 6_000_000_000_000_000_000, day(10, 2))

        val result = overview()

        assertEquals(listOf(50.0, 50.0), result.shares.map { it.percent })
    }

    @Test
    fun `nhiều tiền tệ thì kèm ví của tiền tệ đang xem - một tiền tệ thì không lọc ví`() = runTest {
        assertNull(overview().accountIds)

        fixture.accounts.accounts.value = listOf(
            account("vnd"),
            account("usd", currency = "USD", sortOrder = 1),
            account("usd-old", currency = "USD", archived = true, sortOrder = 2),
        )
        assertEquals(setOf("vnd"), overview().accountIds)
        assertEquals(setOf("usd", "usd-old"), overview(currency = "USD").accountIds)
    }

    @Test
    fun `kỳ đang chạy so cùng số ngày của kỳ trước`() = runTest {
        fixture.spend(null, 100, day(9, 10))
        fixture.spend(null, 500, day(9, 11)) // sau ngày 10 của tháng trước: không tính
        fixture.spend(null, 150, day(10, 5))
        fixture.spend(null, 999, day(10, 25)) // khoản hẹn trước sau hôm nay: vào tổng tháng, không vào so sánh

        val result = overview()
        val comparison = result.comparison!!

        assertTrue(comparison.sameRange)
        assertEquals(Money(150, "VND"), comparison.current)
        assertEquals(Money(100, "VND"), comparison.previous)
        assertEquals(50.0, comparison.changePercent)
        assertEquals(Money(1_149, "VND"), result.expense)
    }

    @Test
    fun `kỳ đã hết so cả kỳ - kỳ trước bằng 0 thì không so`() = runTest {
        fixture.spend(null, 200, day(8, 31))
        fixture.spend(null, 100, day(9, 30))

        val september = overview(period = ReportPeriod.Month(YearMonth(2026, 9)))
        assertFalse(september.comparison!!.sameRange)
        assertEquals(-50.0, september.comparison!!.changePercent)

        assertNull(overview(period = ReportPeriod.Month(YearMonth(2026, 8))).comparison)
    }

    @Test
    fun `31-3 so với cả tháng 2 - kẹp ở cuối tháng trước`() = runTest {
        fixture.spend(null, 100, day(2, 28))
        fixture.spend(null, 100, day(3, 31))

        val comparison = overview(period = ReportPeriod.Month(YearMonth(2026, 3)), today = day(3, 31)).comparison!!

        assertEquals(Money(100, "VND"), comparison.previous)
        assertEquals(0.0, comparison.changePercent)
    }

    @Test
    fun `kỳ tương lai thì về kỳ hiện tại - không có kỳ sau`() = runTest {
        val result = overview(period = ReportPeriod.Month(YearMonth(2026, 12)))

        assertEquals(october, result.period)
        assertFalse(result.canGoNext)
        assertTrue(overview(period = ReportPeriod.Month(YearMonth(2026, 8))).canGoNext)
    }

    @Test
    fun `xu hướng 6 tháng kết thúc ở tháng đang xem - tháng trống là 0 - tháng này chưa hết`() = runTest {
        fixture.spend(null, 70, day(5, 1))
        fixture.spend(null, 30, day(10, 1))
        fixture.spend(null, 1, day(4, 30)) // ngoài 6 tháng

        val trend = overview().trend

        assertEquals((5..10).map { YearMonth(2026, it) }, trend.map { it.month })
        assertEquals(listOf(70L, 0, 0, 0, 0, 30), trend.map { it.total.amountMinor })
        assertEquals(listOf(false, false, false, false, false, true), trend.map { it.isPartial })
    }

    @Test
    fun `chế độ năm có 12 tháng - tháng sau hôm nay là tương lai`() = runTest {
        val trend = overview(period = ReportPeriod.Year(2026)).trend

        assertEquals(12, trend.size)
        assertEquals(listOf(11, 12), trend.filter { it.isFuture }.map { it.month.month.ordinal + 1 })
    }

    @Test
    fun `tiền tệ mặc định là ví đang dùng đầu tiên - chọn được tiền tệ khác - không cộng chung`() = runTest {
        fixture.accounts.accounts.value = listOf(
            account("old", currency = "EUR", archived = true, sortOrder = 0),
            account("usd", currency = "USD", sortOrder = 1),
            account("vnd", currency = "VND", sortOrder = 2),
        )
        fixture.spend(null, 500, day(10, 1), currency = "VND")
        fixture.spend(null, 7, day(10, 1), currency = "USD")
        fixture.spend(null, 3, day(10, 1), currency = "JPY")

        val byDefault = overview()
        assertEquals("USD", byDefault.currency)
        assertEquals(Money(7, "USD"), byDefault.expense)
        assertEquals(listOf("USD", "VND", "EUR", "JPY"), byDefault.currencies)

        assertEquals(Money(500, "VND"), overview(currency = "VND").expense)
        assertEquals("USD", overview(currency = "GBP").currency)
    }

    @Test
    fun `danh mục con - tách từng con và phần ghi thẳng vào cha - tổng bằng nhóm ở tổng quan`() = runTest {
        fixture.categories(expenseCategory("food"), expenseCategory("coffee", parentId = "food"), expenseCategory("rice", parentId = "food"))
        fixture.spend("food", 100, day(10, 1))
        fixture.spend("coffee", 300, day(10, 1))
        fixture.spend("rice", 600, day(10, 1))
        fixture.spend("rice", 9, day(9, 1))

        val breakdown = fixture.breakdown("food", october, CategoryKind.EXPENSE, "VND").first()

        assertEquals(listOf("rice", "coffee", "food"), breakdown.shares.map { it.categoryId })
        assertEquals(listOf(60.0, 30.0, 10.0), breakdown.shares.map { it.percent })
        assertEquals(overview().shares.single().total, breakdown.total)
        assertEquals("food", breakdown.parent?.id)
    }
}
