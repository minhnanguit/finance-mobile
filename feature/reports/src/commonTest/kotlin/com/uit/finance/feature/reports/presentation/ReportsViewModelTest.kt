@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.uit.finance.feature.reports.presentation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.reports.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.reports.presentation.common.ReportFormatter
import com.uit.finance.feature.reports.presentation.common.ShareAction
import com.uit.finance.feature.reports.presentation.common.ShareStyle
import com.uit.finance.feature.reports.presentation.navigation.CategoryBreakdownDestination
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import com.uit.finance.feature.reports.presentation.overview.ChangeDirection
import com.uit.finance.feature.reports.presentation.overview.ReportMode
import com.uit.finance.feature.reports.presentation.overview.ReportsEffect
import com.uit.finance.feature.reports.presentation.overview.ReportsIntent
import com.uit.finance.feature.reports.presentation.overview.ReportsUiMapper
import com.uit.finance.feature.reports.presentation.overview.ReportsViewModel
import com.uit.finance.feature.reports.testing.ReportFixture
import com.uit.finance.feature.reports.testing.expenseCategory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class ReportsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixture = ReportFixture()
    private val created = mutableListOf<ReportsViewModel>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Đồng hồ chạy theo thời gian ảo của test: advanceTimeBy vừa đẩy delay vừa đẩy giờ. */
    private fun clockAt(start: String) = object : Clock {
        override fun now(): Instant = Instant.parse(start) + dispatcher.scheduler.currentTime.milliseconds
    }

    private fun viewModel(start: String = "2026-10-10T09:00:00Z") = ReportsViewModel(
        observeOverview = fixture.overview,
        observeToday = ObserveTodayUseCase(clockAt(start), TimeZone.UTC),
        mapper = ReportsUiMapper(ReportFormatter(MoneyFormatter())),
    ).also { created += it }

    /** ViewModel nghe "hôm nay" mãi mãi: huỷ scope trước khi runTest dọn dẹp, không thì thời gian ảo chạy không dừng. */
    private fun runVmTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun day(month: Int, day: Int) = LocalDate(2026, month, day)

    @Test
    fun `mở màn - tháng hiện tại - số chính - so cùng kỳ - không có kỳ sau`() = runVmTest {
        fixture.spend(null, 100_000, day(9, 3))
        fixture.spend(null, 150_000, day(10, 5))
        fixture.earn(null, 1_000_000, day(10, 1))
        val vm = viewModel()
        runCurrent()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Tháng 10/2026", state.periodLabel)
        assertFalse(state.canGoNext)
        assertEquals("150.000", state.total)
        assertEquals("150k", state.totalCompact)
        assertEquals("50% so với cùng kỳ tháng 9", state.comparison?.text)
        assertEquals(ChangeDirection.Up, state.comparison?.direction)
        assertFalse(state.comparison!!.isGood) // chi tăng
        assertEquals("Thu 1tr", state.counterpartLabel)
        assertEquals("Còn lại 850k", state.remainingLabel)
        assertFalse(state.remainingNegative)
        assertEquals(6, state.trend.size)
        assertTrue(state.trend.last().isSelected)
    }

    @Test
    fun `lùi tới kỳ trước rồi tới lại - không tới được kỳ tương lai`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        vm.onIntent(ReportsIntent.Previous)
        runCurrent()
        assertEquals("Tháng 9/2026", vm.state.value.periodLabel)
        assertTrue(vm.state.value.canGoNext)

        vm.onIntent(ReportsIntent.Next)
        runCurrent()
        vm.onIntent(ReportsIntent.Next)
        runCurrent()
        assertEquals("Tháng 10/2026", vm.state.value.periodLabel)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun `qua nửa đêm sang tháng mới thì kỳ hiện tại tự đổi - kỳ cũ đang xem thì giữ`() = runVmTest {
        val following = viewModel(start = "2026-10-31T23:00:00Z")
        val pinned = viewModel(start = "2026-10-31T23:00:00Z")
        runCurrent()
        pinned.onIntent(ReportsIntent.Previous)
        runCurrent()

        advanceTimeBy(2.hours)
        runCurrent()

        assertEquals("Tháng 11/2026", following.state.value.periodLabel)
        assertEquals("Tháng 9/2026", pinned.state.value.periodLabel)
        assertTrue(pinned.state.value.canGoNext)
    }

    @Test
    fun `đổi sang Thu - số chính là thu - dòng phụ là chi - so sánh thu tăng là tốt`() = runVmTest {
        fixture.earn(null, 100, day(9, 1))
        fixture.earn(null, 300, day(10, 1))
        fixture.spend(null, 500, day(10, 1))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(ReportsIntent.SelectKind(CategoryKind.INCOME))
        runCurrent()

        val state = vm.state.value
        assertEquals("300", state.total)
        assertEquals("Chi 500", state.counterpartLabel)
        assertTrue(state.remainingNegative)
        assertTrue(state.comparison!!.isGood)
    }

    @Test
    fun `xem theo năm - 12 cột - chạm cột tháng 5 thì về xem tháng 5`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        vm.onIntent(ReportsIntent.SelectMode(ReportMode.Year))
        runCurrent()
        assertEquals("Năm 2026", vm.state.value.periodLabel)
        assertEquals(12, vm.state.value.trend.size)

        vm.onIntent(ReportsIntent.SelectTrendBar(4))
        runCurrent()
        assertEquals(ReportMode.Month, vm.state.value.mode)
        assertEquals("Tháng 5/2026", vm.state.value.periodLabel)

        vm.onIntent(ReportsIntent.SelectTrendBar(5)) // trong 6 cột Tháng 12/2025..5/2026 cột cuối là tháng 5
        runCurrent()
        assertEquals("Tháng 5/2026", vm.state.value.periodLabel)
    }

    @Test
    fun `chạm dòng - có con thì mở danh mục con - không con thì mở giao dịch - Khác thì mở rộng`() = runVmTest {
        fixture.categories(
            expenseCategory("food"), expenseCategory("coffee", parentId = "food"),
            *(1..6).map { expenseCategory("c$it") }.toTypedArray(),
        )
        fixture.spend("coffee", 1_000, day(10, 1))
        (1..6).forEach { fixture.spend("c$it", (100 - it).toLong(), day(10, 1)) }
        fixture.spend(null, 1, day(10, 1))
        val vm = viewModel()
        runCurrent()

        val rows = vm.state.value.rows
        assertEquals(6, rows.size) // 5 nhóm + "Khác"
        val food = rows.first()
        val leaf = rows[1]
        val others = rows.last()
        assertEquals(ShareAction.OpenBreakdown, food.action)
        assertEquals(ShareAction.OpenTransactions, leaf.action)
        assertEquals(ShareStyle.Other, others.style)
        assertEquals(ShareStyle.Uncategorized, vm.state.value.otherRows.last().style)
        assertEquals(ShareAction.None, vm.state.value.otherRows.last().action)

        vm.effects.test {
            vm.onIntent(ReportsIntent.OpenRow(food))
            assertEquals(
                ReportsEffect.OpenBreakdown(CategoryBreakdownDestination("food", "2026-10", "EXPENSE", "VND")),
                awaitItem(),
            )
            vm.onIntent(ReportsIntent.OpenRow(leaf))
            assertEquals(
                ReportsEffect.OpenTransactions(ReportTransactionsQuery(day(10, 1), day(10, 31), "c1", CategoryKind.EXPENSE)),
                awaitItem(),
            )
        }

        vm.onIntent(ReportsIntent.OpenRow(others))
        assertTrue(vm.state.value.showOthers)
        assertEquals(5 + 1, vm.state.value.slices.size)
    }

    @Test
    fun `bấm lùi hai lần liền trước khi tải xong vẫn lùi đúng hai kỳ`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        vm.onIntent(ReportsIntent.Previous)
        vm.onIntent(ReportsIntent.Previous)
        vm.onIntent(ReportsIntent.Previous)
        vm.onIntent(ReportsIntent.Next)
        runCurrent()

        assertEquals("Tháng 8/2026", vm.state.value.periodLabel)
    }

    @Test
    fun `chạm dòng còn trên màn khi kỳ mới chưa tải thì mở đúng kỳ của dòng đó`() = runVmTest {
        fixture.categories(expenseCategory("c1"))
        fixture.spend("c1", 1_000, day(10, 1))
        val vm = viewModel()
        runCurrent()
        val row = vm.state.value.rows.single()

        vm.effects.test {
            vm.onIntent(ReportsIntent.Previous) // chưa runCurrent: màn vẫn là tháng 10
            vm.onIntent(ReportsIntent.OpenRow(row))
            assertEquals(
                ReportsEffect.OpenTransactions(ReportTransactionsQuery(day(10, 1), day(10, 31), "c1", CategoryKind.EXPENSE)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `nhiều tiền tệ thì mở giao dịch chỉ trong ví của tiền tệ đang xem`() = runVmTest {
        fixture.accounts.accounts.value = listOf(account("vnd"), account("usd", currency = "USD", sortOrder = 1))
        fixture.categories(expenseCategory("c1"))
        fixture.spend("c1", 5_00, day(10, 1), currency = "USD", accountId = "usd")
        val vm = viewModel()
        runCurrent()
        vm.onIntent(ReportsIntent.SelectCurrency("USD"))
        runCurrent()

        vm.effects.test {
            vm.onIntent(ReportsIntent.OpenRow(vm.state.value.rows.single()))
            assertEquals(
                ReportsEffect.OpenTransactions(
                    ReportTransactionsQuery(day(10, 1), day(10, 31), "c1", CategoryKind.EXPENSE, accountIds = setOf("usd")),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `cột xu hướng có hành động cho trình đọc màn hình - trừ cột đang xem`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        val trend = vm.state.value.trend
        assertEquals("Xem tháng 5/2026", trend.first().actionLabel)
        assertNull(trend.last().actionLabel)
    }

    @Test
    fun `chỉ một tiền tệ thì ẩn mục tiền tệ - nhiều tiền tệ thì chọn được`() = runVmTest {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.state.value.showCurrencyOption)

        fixture.accounts.accounts.value = listOf(account("vnd"), account("usd", currency = "USD", sortOrder = 1))
        fixture.spend(null, 5_00, day(10, 1), currency = "USD")
        runCurrent()
        assertTrue(vm.state.value.showCurrencyOption)
        assertEquals("VND", vm.state.value.currency)

        vm.onIntent(ReportsIntent.OpenCurrencyPicker)
        vm.onIntent(ReportsIntent.SelectCurrency("USD"))
        runCurrent()
        assertEquals("USD", vm.state.value.currency)
        assertEquals("5,00", vm.state.value.total)
        assertFalse(vm.state.value.showCurrencySheet)
    }

    @Test
    fun `kỳ trống thì hiện trạng thái trống - không so sánh`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        assertTrue(vm.state.value.isEmpty)
        assertNull(vm.state.value.comparison)
    }
}
