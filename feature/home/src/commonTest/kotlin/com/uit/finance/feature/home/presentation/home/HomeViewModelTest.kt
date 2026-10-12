@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.home.presentation.home

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeBalanceRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSyncKit
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.balance
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.ui.sync.toSyncMessage
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.home.domain.usecase.ObserveHomeDashboardUseCase
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val balances = FakeBalanceRepository()
    private val transactions = FakeTransactionRepository()
    private val categories = FakeCategoryRepository()
    private val clock = TestClock(NOON_2023_11_14) // thứ Ba
    private val today = LocalDate(2023, 11, 14)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val sync = FakeSyncKit()

    private fun viewModel() = HomeViewModel(
        observeDashboard = ObserveHomeDashboardUseCase(accounts, balances, transactions, categories),
        observeSync = sync.observeOverview,
        syncNow = sync.syncNow,
        acknowledgeSyncIssues = sync.acknowledgeIssues,
        mapper = HomeUiMapper(MoneyFormatter(), TransactionItemFormatter(MoneyFormatter()), clock),
        clock = clock,
    )

    @Test
    fun `đang chờ lần phát đầu tiên thì isLoading - sau đó hiện trạng thái trống`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.isLoading)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isLoading)
        assertTrue(state.accounts.isEmpty())
        assertTrue(state.recentTransactions.isEmpty())
        assertEquals("Thứ Ba, 14 tháng 11", state.todayLabel)
    }

    @Test
    fun `format số dư - thu chi theo tiền tệ và dòng giao dịch`() = runTest(dispatcher) {
        accounts.accounts.value = listOf(account("a", name = "Tiền mặt"), account("c", name = "USD", currency = "USD"))
        balances.balances.value = BalanceSummary(listOf(balance("a", 1_250_000), balance("c", 9_530_050, "USD")), emptyList())
        balances.cashFlow.value = CashFlow(income = listOf(Money(50_000, "VND")), expense = listOf(Money(30_000, "VND")))
        categories.categories.value = listOf(category("food", "Ăn uống", icon = "utensils"))
        transactions.transactions.value = listOf(
            transaction("t1", TransactionType.EXPENSE, "a", 30_000, today, categoryId = "food", payee = "Phở Hoà"),
            transaction("t2", TransactionType.INCOME, "a", 50_000, today.minus(1, DateTimeUnit.DAY)),
            transaction("t3", TransactionType.TRANSFER, "a", 20_000, LocalDate(2023, 10, 2)),
        )

        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(listOf("1.250.000", "95.300,50"), state.accounts.map { it.balance })
        assertEquals(MonthSummaryUi("VND", income = "50.000", expense = "30.000"), state.monthByCurrency["VND"])
        assertEquals(MonthSummaryUi("USD", income = "0,00", expense = "0,00"), state.monthByCurrency["USD"])

        val (expense, income, transfer) = state.recentTransactions
        assertEquals("Phở Hoà", expense.title)
        assertEquals("Ăn uống · Tiền mặt · Hôm nay", expense.subtitle)
        assertEquals("-", expense.sign)
        assertEquals("30.000", expense.amount)
        assertEquals("utensils", expense.iconKey)

        assertEquals("Thu nhập", income.title)
        assertEquals("Tiền mặt · Hôm qua", income.subtitle)
        assertEquals("+", income.sign)

        assertEquals("Chuyển tiền", transfer.title)
        assertEquals("Tiền mặt · 2 thg 10", transfer.subtitle)
        assertEquals("", transfer.sign)
    }

    @Test
    fun `giao dịch mới từ DB là state đổi theo`() = runTest(dispatcher) {
        accounts.accounts.value = listOf(account("a"))
        val vm = viewModel()

        vm.state.test {
            assertTrue(awaitItem().isLoading)
            assertTrue(awaitItem().recentTransactions.isEmpty())
            transactions.transactions.value = listOf(transaction("t1", TransactionType.EXPENSE, "a", 1_000, today))
            assertEquals(listOf("t1"), awaitItem().recentTransactions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume cùng ngày không query lại - sang tháng mới thì hỏi kỳ mới và nhãn ngày mới`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(LocalDate(2023, 11, 1) to LocalDate(2023, 11, 30), balances.requestedRange)
        assertEquals(1, transactions.requestedLimits.size)

        clock.advanceBy(3.hours) // vẫn 14/11
        vm.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()
        assertEquals(1, transactions.requestedLimits.size)

        clock.advanceBy(18.days) // 2023-12-02
        vm.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()
        assertEquals(2, transactions.requestedLimits.size)
        assertEquals(LocalDate(2023, 12, 1) to LocalDate(2023, 12, 31), balances.requestedRange)
        assertEquals("Thứ Bảy, 2 tháng 12", vm.state.value.todayLabel)
    }

    @Test
    fun `badge sync gom hàng chờ và thay đổi bị từ chối - kéo để làm mới gọi sync`() = runTest(dispatcher) {
        sync.outbox.unsent.value = 3
        sync.issues.unacknowledged.value = 1
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(SyncOverview(pending = 3, rejected = 1), vm.state.value.sync)

        vm.onIntent(HomeIntent.SyncNow)
        advanceUntilIdle()
        assertEquals(1, sync.engine.syncCalls)
        assertEquals(1, sync.outbox.retryAllNowCalls)
        assertEquals(false, vm.state.value.isRefreshing)

        vm.onIntent(HomeIntent.AcknowledgeSyncIssues)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.sync.rejected)
    }

    @Test
    fun `mất mạng khi làm mới thì báo một lần rồi xoá thông báo`() = runTest(dispatcher) {
        sync.engine.result = AppResult.Failure(AppError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.SyncNow)
        advanceUntilIdle()
        assertEquals(AppError.Network().toSyncMessage(), vm.state.value.message)

        vm.onIntent(HomeIntent.MessageShown)
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun `mọi HomeTarget đều thành đúng một effect điều hướng`() = runTest(dispatcher) {
        val vm = viewModel()
        val targets = listOf(
            HomeTarget.AddTransaction, HomeTarget.EditTransaction("t1"), HomeTarget.Transactions, HomeTarget.Accounts,
            HomeTarget.CreateAccount, HomeTarget.Budgets, HomeTarget.Reports, HomeTarget.Bills,
        )

        vm.effects.test {
            targets.forEach { target ->
                vm.onIntent(HomeIntent.Open(target))
                assertEquals(HomeEffect.Navigate(target), awaitItem())
            }
            expectNoEvents()
        }
    }
}
