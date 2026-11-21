@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.recurring.presentation

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSyncKit
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.recurring.domain.model.OccurrenceIds
import com.uit.finance.core.recurring.testing.FakeRecurringRepository
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.recurring.domain.usecase.ConfirmOccurrenceUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringOverviewUseCase
import com.uit.finance.feature.recurring.domain.usecase.ResolveBlockedRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.SkipOccurrenceUseCase
import com.uit.finance.feature.recurring.draft
import com.uit.finance.feature.recurring.presentation.list.RecurringListIntent
import com.uit.finance.feature.recurring.presentation.list.RecurringListViewModel
import com.uit.finance.feature.recurring.rent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class RecurringListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recurrings = FakeRecurringRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val sync = FakeSyncKit()
    private val due = OccurrenceIds.of("rent", LocalDate(2023, 11, 5))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts.accounts.value = listOf(account("cash"))
        categories.categories.value = listOf(category("home", "Nhà ở"))
        recurrings.recurrings.value = listOf(rent())
        transactions.transactions.value = listOf(draft(due, LocalDate(2023, 11, 5)))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.withVm(block: suspend TestScope.(RecurringListViewModel) -> Unit) {
        val vm = RecurringListViewModel(
            observeOverview = ObserveRecurringOverviewUseCase(recurrings, transactions, accounts, categories, TestClock(NOON_2023_11_14)),
            observeSync = sync.observeOverview,
            confirmOccurrence = ConfirmOccurrenceUseCase(transactions),
            skipOccurrence = SkipOccurrenceUseCase(transactions),
            resolveBlocked = ResolveBlockedRecurringUseCase(recurrings),
            syncNow = sync.syncNow,
            acknowledgeSyncIssues = sync.acknowledgeIssues,
            money = MoneyFormatter(),
        )
        try {
            runCurrent()
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `xác nhận một kỳ nháp - giao dịch thành đã xác nhận và rời khỏi cần xác nhận`() = runTest(dispatcher) {
        withVm { vm ->
            assertEquals(listOf(due), vm.state.value.toConfirm.map { it.transactionId })

            vm.onIntent(RecurringListIntent.ConfirmClicked(due))
            runCurrent()

            assertEquals(TransactionStatus.CONFIRMED, transactions.transactions.value.single().status)
            assertEquals(emptyList(), vm.state.value.toConfirm)
        }
    }

    @Test
    fun `bỏ qua cần xác nhận trước - đồng ý thì xoá giao dịch nháp`() = runTest(dispatcher) {
        withVm { vm ->
            vm.onIntent(RecurringListIntent.SkipClicked(due))
            assertEquals(due, vm.state.value.skipConfirm?.transactionId)

            vm.onIntent(RecurringListIntent.SkipConfirmed)
            runCurrent()

            assertNull(vm.state.value.skipConfirm)
            assertEquals(listOf(due), transactions.deletedIds)
        }
    }

    @Test
    fun `danh sách khoản có mô tả lịch và lần tới`() = runTest(dispatcher) {
        withVm { vm ->
            val item = vm.state.value.recurrings.single()
            assertEquals("Ngày 5 hằng tháng", item.schedule)
            assertEquals("Lần tới T3 05/12", item.nextLabel)
        }
    }
}
