@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.transactions.presentation

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionFormDataUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveTransactionUseCase
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorArgs
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorEffect
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorIntent
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
class TransactionEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository(accounts)
    private val today = LocalDate(2023, 11, 14)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts.accounts.value = listOf(
            account("old", "Ví cũ", archived = true, sortOrder = 0),
            account("cash", "Tiền mặt", sortOrder = 1),
            account("bank", "Bank", sortOrder = 2),
            account("usd", "USD", currency = "USD", sortOrder = 3),
        )
        categories.categories.value = listOf(
            category("food", "Ăn uống"),
            category("salary", "Lương", kind = CategoryKind.INCOME),
            category("gone", "Cũ", archived = true),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(transactionId: String? = null) = TransactionEditorViewModel(
        args = TransactionEditorArgs(transactionId),
        observeFormData = ObserveTransactionFormDataUseCase(accounts, categories),
        observeTransaction = ObserveTransactionUseCase(transactions),
        saveTransaction = SaveTransactionUseCase(transactions, categories, TestClock(NOON_2023_11_14)),
        deleteTransaction = DeleteTransactionUseCase(transactions),
        clock = TestClock(NOON_2023_11_14),
    )

    @Test
    fun `thêm mới mặc định là chi từ ví đang dùng đầu tiên vào hôm nay`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("cash", state.accountId)
        assertEquals(listOf("cash", "bank", "usd"), state.accounts.map { it.id })
        assertEquals(listOf("food"), state.categories.map { it.id })
        assertEquals(today, state.occurredOn)
        assertFalse(state.canSave)
    }

    @Test
    fun `gõ số tiền - chọn danh mục - lưu là xong ba chạm`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(TransactionEditorIntent.AmountChanged("55.000"))
        vm.onIntent(TransactionEditorIntent.CategorySelected("food"))
        assertTrue(vm.state.value.canSave)

        vm.effects.test {
            vm.onIntent(TransactionEditorIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        val saved = transactions.transactions.value.single()
        assertEquals(Money(55_000, "VND"), saved.amount)
        assertEquals("food", saved.categoryId)
        assertEquals(today, saved.occurredOn)
    }

    @Test
    fun `chuyển tiền chỉ cho ví nhận khác ví đi và cùng tiền tệ`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(TransactionEditorIntent.TypeChanged(TransactionType.TRANSFER))
        assertEquals(listOf("bank"), vm.state.value.counterAccounts.map { it.id })
        assertEquals(null, vm.state.value.categoryId)

        vm.onIntent(TransactionEditorIntent.CounterAccountSelected("bank"))
        vm.onIntent(TransactionEditorIntent.AmountChanged("12,5"))
        vm.onIntent(TransactionEditorIntent.AccountSelected("usd")) // đổi sang ví USD
        val state = vm.state.value
        assertEquals("USD", state.currency)
        assertEquals(null, state.counterAccountId) // bank là VND: bỏ chọn
        assertEquals(emptyList(), state.counterAccounts)
    }

    @Test
    fun `chuyển tiền có phí mà chưa có danh mục phí thì báo dưới ô phí`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(TransactionEditorIntent.TypeChanged(TransactionType.TRANSFER))
        vm.onIntent(TransactionEditorIntent.CounterAccountSelected("bank"))
        vm.onIntent(TransactionEditorIntent.AmountChanged("1000000"))
        vm.onIntent(TransactionEditorIntent.FeeChanged("3300"))

        vm.onIntent(TransactionEditorIntent.SaveClicked)
        advanceUntilIdle()

        assertNotNull(vm.state.value.feeError)
        assertEquals(null, vm.state.value.error)
        assertFalse(vm.state.value.isSaving)
        assertEquals(emptyList(), transactions.transactions.value)
    }

    @Test
    fun `sửa giao dịch điền sẵn và vẫn giữ ví đã lưu trữ của nó`() = runTest(dispatcher) {
        transactions.transactions.value = listOf(
            transaction("t1", TransactionType.EXPENSE, "old", 25_000, LocalDate(2023, 11, 1), categoryId = "gone", payee = "Phở"),
        )
        val vm = viewModel("t1")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isNew)
        assertEquals("25000", state.amount)
        assertEquals("old", state.accountId)
        assertEquals("gone", state.categoryId)
        assertEquals("Phở", state.payee)
        assertEquals(listOf("old", "cash", "bank", "usd"), state.accounts.map { it.id })
    }
}
