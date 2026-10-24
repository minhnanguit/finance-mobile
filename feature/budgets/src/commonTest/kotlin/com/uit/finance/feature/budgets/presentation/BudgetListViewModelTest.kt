@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.uit.finance.feature.budgets.presentation

import app.cash.turbine.test
import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.testing.FakeBudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSpendingRepository
import com.uit.finance.core.ledger.testing.FakeSyncKit
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.budgets.domain.model.BudgetTone
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetProgressUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.budgets.presentation.list.BudgetListEffect
import com.uit.finance.feature.budgets.presentation.list.BudgetListIntent
import com.uit.finance.feature.budgets.presentation.list.BudgetListViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import androidx.lifecycle.viewModelScope
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class BudgetListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val budgets = FakeBudgetRepository()
    private val spending = FakeSpendingRepository()
    private val categories = FakeCategoryRepository()
    private val sync = FakeSyncKit()
    private val october = YearMonth(2026, 10)
    private val november = YearMonth(2026, 11)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Đồng hồ chạy theo thời gian ảo của test: advanceTimeBy vừa đẩy delay vừa đẩy giờ. */
    private fun clockAt(start: String) = object : Clock {
        override fun now(): Instant = Instant.parse(start) + dispatcher.scheduler.currentTime.milliseconds
    }

    private val created = mutableListOf<BudgetListViewModel>()

    private fun viewModel(start: String) = BudgetListViewModel(
        observeProgress = ObserveBudgetProgressUseCase(budgets, spending, categories),
        observeToday = ObserveTodayUseCase(clockAt(start), TimeZone.UTC),
        observeSync = sync.observeOverview,
        syncNow = sync.syncNow,
        acknowledgeSyncIssues = sync.acknowledgeIssues,
        money = MoneyFormatter(),
    ).also { created += it }

    /**
     * ViewModel giữ luồng "hôm nay" chạy mãi (mỗi nửa đêm một lần). Huỷ scope của nó ngay trong thân test, trước khi
     * runTest dọn dẹp; không thì runTest cứ đẩy thời gian ảo qua từng ngày và không bao giờ xong.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun budget(id: String, categoryId: String, amount: Long) = Budget(id, categoryId, Money(amount, "VND"), october)

    private fun spend(month: YearMonth, vararg pairs: Pair<String, Long>) {
        spending.spending.value = spending.spending.value + (month to pairs.map { (id, amount) -> CategorySpend(id, Money(amount, "VND")) })
    }

    @Test
    fun `màu theo ngưỡng 80 và 100 phần trăm - phần trăm kẹp 999 - gần vượt nhất lên đầu`() = runVmTest {
        categories.categories.value = listOf(category("food", "Ăn uống"), category("shop", "Mua sắm"), category("move", "Đi lại"), category("tip", "Tiền tip"))
        budgets.budgets.value = listOf(
            budget("b-food", "food", 3_000_000),
            budget("b-shop", "shop", 2_000_000),
            budget("b-move", "move", 1_000_000),
            budget("b-tip", "tip", 1),
        )
        spend(october, "food" to 2_550_000, "shop" to 2_300_000, "move" to 400_000, "tip" to 50_000)
        val vm = viewModel("2026-10-17T08:00:00Z")
        runCurrent()

        val items = vm.state.value.items
        assertEquals(listOf("b-tip", "b-shop", "b-food", "b-move"), items.map { it.id })
        assertEquals(listOf("999%", "115%", "85%", "40%"), items.map { it.percentLabel })
        assertEquals(listOf(BudgetTone.Over, BudgetTone.Over, BudgetTone.Warning, BudgetTone.Normal), items.map { it.tone })
        val shop = items[1]
        assertTrue(shop.remaining.isNegative)
        assertEquals("300.000", shop.remaining.amount)
        assertNull(shop.dailyAllowance)
        assertEquals(1f, shop.progress)
        // 450.000 còn lại chia 15 ngày (17 tới 31).
        assertEquals("30.000", items[2].dailyAllowance?.amount)
        assertEquals("Tháng 10/2026", vm.state.value.monthLabel)
    }

    @Test
    fun `số mỗi ngày còn được chi đổi khi qua nửa đêm`() = runVmTest {
        budgets.budgets.value = listOf(budget("b-food", "food", 3_000_000))
        spend(october, "food" to 2_550_000)
        val vm = viewModel("2026-10-16T23:59:00Z")
        runCurrent()
        // 450.000 chia 16 ngày (16 tới 31).
        assertEquals("28.125", vm.state.value.items.single().dailyAllowance?.amount)

        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals("30.000", vm.state.value.items.single().dailyAllowance?.amount)
    }

    @Test
    fun `để màn mở qua nửa đêm cuối tháng thì tự sang tháng mới`() = runVmTest {
        budgets.budgets.value = listOf(budget("b-food", "food", 3_000_000))
        spend(october, "food" to 2_550_000)
        val vm = viewModel("2026-10-31T23:59:00Z")
        runCurrent()
        assertEquals("Tháng 10/2026", vm.state.value.monthLabel)
        assertEquals("450.000", vm.state.value.items.single().dailyAllowance?.amount)

        advanceTimeBy(60.seconds)
        runCurrent()

        val state = vm.state.value
        assertEquals("Tháng 11/2026", state.monthLabel)
        assertEquals("0", state.items.single().spent.amount)
        // Cả 3.000.000 chia 30 ngày của tháng 11.
        assertEquals("100.000", state.items.single().dailyAllowance?.amount)
        assertEquals(listOf(october, november), spending.requestedMonths.distinct())
    }

    @Test
    fun `xem tháng trước rồi quay lại - không đi được tới tháng tương lai`() = runVmTest {
        val vm = viewModel("2026-10-17T08:00:00Z")
        runCurrent()
        assertFalse(vm.state.value.canGoNext)

        vm.onIntent(BudgetListIntent.NextMonth)
        runCurrent()
        assertEquals("Tháng 10/2026", vm.state.value.monthLabel)

        vm.onIntent(BudgetListIntent.PreviousMonth)
        runCurrent()
        assertEquals("Tháng 9/2026", vm.state.value.monthLabel)
        assertTrue(vm.state.value.canGoNext)
        assertFalse(vm.state.value.isCurrentMonth)

        vm.onIntent(BudgetListIntent.NextMonth)
        runCurrent()
        assertEquals("Tháng 10/2026", vm.state.value.monthLabel)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun `tháng trước tháng bắt đầu thì không hiện ngân sách`() = runVmTest {
        budgets.budgets.value = listOf(budget("b-food", "food", 3_000_000))
        val vm = viewModel("2026-10-17T08:00:00Z")
        runCurrent()
        assertEquals(1, vm.state.value.items.size)

        vm.onIntent(BudgetListIntent.PreviousMonth)
        runCurrent()

        assertEquals(emptyList(), vm.state.value.items)
    }

    @Test
    fun `bấm thêm và bấm một dòng thì mở form`() = runVmTest {
        val vm = viewModel("2026-10-17T08:00:00Z")
        runCurrent()

        vm.effects.test {
            vm.onIntent(BudgetListIntent.AddClicked)
            assertEquals(BudgetListEffect.OpenEditor(null), awaitItem())
            vm.onIntent(BudgetListIntent.ItemClicked("b-food"))
            assertEquals(BudgetListEffect.OpenEditor("b-food"), awaitItem())
        }
    }

    @Test
    fun `kéo để làm mới gọi đồng bộ`() = runVmTest {
        val vm = viewModel("2026-10-17T08:00:00Z")
        runCurrent()

        vm.onIntent(BudgetListIntent.SyncNow)
        runCurrent()

        assertEquals(1, sync.engine.syncCalls)
        assertFalse(vm.state.value.isRefreshing)
    }
}
