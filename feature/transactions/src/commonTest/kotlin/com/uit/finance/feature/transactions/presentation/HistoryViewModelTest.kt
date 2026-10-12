@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.transactions.presentation

import app.cash.turbine.test
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSyncKit
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionHistoryUseCase
import com.uit.finance.feature.transactions.presentation.history.HistoryEffect
import com.uit.finance.feature.transactions.presentation.history.HistoryIntent
import com.uit.finance.feature.transactions.presentation.history.HistoryViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val sync = FakeSyncKit()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HistoryViewModel(
        observeHistory = ObserveTransactionHistoryUseCase(transactions, accounts, categories),
        observeSync = sync.observeOverview,
        deleteTransaction = DeleteTransactionUseCase(transactions),
        syncNow = sync.syncNow,
        acknowledgeSyncIssues = sync.acknowledgeIssues,
        formatter = TransactionItemFormatter(MoneyFormatter()),
        clock = TestClock(NOON_2023_11_14),
    )

    private fun seed(count: Int) {
        accounts.accounts.value = listOf(account("a", "Ví"))
        transactions.transactions.value = List(count) { index ->
            transaction("t$index", TransactionType.EXPENSE, "a", 1_000L + index, LocalDate(2023, 11, 14 - index % 2))
        }
    }

    @Test
    fun `gom theo ngày với nhãn tương đối`() = runTest(dispatcher) {
        seed(3)
        val vm = viewModel()
        advanceUntilIdle()

        val sections = vm.state.value.sections
        assertEquals(listOf("Hôm nay", "Hôm qua"), sections.map { it.label })
        assertEquals(listOf("t0", "t2"), sections[0].items.map { it.id })
        assertEquals(false, vm.state.value.canLoadMore)
    }

    @Test
    fun `chạm gần cuối thì nới cửa sổ thêm một trang`() = runTest(dispatcher) {
        seed(50)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.canLoadMore)

        vm.onIntent(HistoryIntent.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf(50, 100), transactions.requestedLimits)
        assertEquals(false, vm.state.value.canLoadMore)
    }

    @Test
    fun `vuốt xoá ẩn ngay - hoàn tác thì hiện lại và không xoá thật`() = runTest(dispatcher) {
        seed(2)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.DeleteSwiped("t0"))
        assertEquals(listOf("t1"), vm.state.value.sections.flatMap { s -> s.items.map { it.id } })

        vm.onIntent(HistoryIntent.UndoDelete)
        advanceUntilIdle()
        assertEquals(listOf("t0", "t1"), vm.state.value.sections.flatMap { s -> s.items.map { it.id } })
        assertEquals(emptyList(), transactions.deletedIds)
    }

    @Test
    fun `hết giờ hoàn tác hoặc vuốt dòng khác thì xoá thật dòng trước`() = runTest(dispatcher) {
        seed(3)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HistoryIntent.DeleteSwiped("t0"))
        vm.onIntent(HistoryIntent.DeleteSwiped("t1"))
        advanceUntilIdle()
        assertEquals(listOf("t0"), transactions.deletedIds)
        assertEquals("t1", vm.state.value.pendingDeleteId)

        vm.onIntent(HistoryIntent.CommitPendingDelete)
        advanceUntilIdle()
        assertEquals(listOf("t0", "t1"), transactions.deletedIds)
        assertEquals(listOf("t2"), vm.state.value.sections.flatMap { s -> s.items.map { it.id } })
    }

    @Test
    fun `chạm dòng mở form sửa và nút thêm mở form mới`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(HistoryIntent.ItemClicked("t9"))
            assertEquals(HistoryEffect.OpenEditor("t9"), awaitItem())
            vm.onIntent(HistoryIntent.AddClicked)
            assertEquals(HistoryEffect.OpenEditor(null), awaitItem())
        }
    }

    @Test
    fun `kéo để làm mới chạy một vòng sync`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(HistoryIntent.SyncNow)
        advanceUntilIdle()
        assertEquals(1, sync.engine.syncCalls)
        assertEquals(false, vm.state.value.isRefreshing)
    }
}
