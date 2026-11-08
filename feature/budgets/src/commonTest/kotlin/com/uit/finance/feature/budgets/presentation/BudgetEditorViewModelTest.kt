@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.budgets.presentation

import app.cash.turbine.test
import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.model.BudgetErrorCode
import com.uit.finance.core.budget.testing.FakeBudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.ui.budget.toBudgetUiText
import com.uit.finance.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetFormDataUseCase
import com.uit.finance.feature.budgets.domain.usecase.SaveBudgetUseCase
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorArgs
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorEffect
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorIntent
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class BudgetEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val accounts = FakeAccountRepository()
    private val clock = TestClock(NOON_2023_11_14)
    private val november2023 = YearMonth(2023, 11)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts.accounts.value = listOf(
            account("cash", currency = "VND", sortOrder = 0),
            account("card", currency = "USD", sortOrder = 1),
            account("old", currency = "EUR", sortOrder = 2, archived = true),
        )
        categories.categories.value = listOf(
            category("food", "Ăn uống"),
            category("shop", "Mua sắm"),
            category("food", "Ăn uống").copy(id = "coffee", name = "Cà phê", parentId = "food"),
            category("salary", "Lương", kind = CategoryKind.INCOME),
            category("hobby", "Sở thích", archived = true),
        )
        budgets.budgets.value = listOf(
            Budget("b-food", "food", Money(3_000_000, "VND"), november2023),
            Budget("b-shop-usd", "shop", Money(100_00, "USD"), november2023),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(budgetId: String? = null) = BudgetEditorViewModel(
        args = BudgetEditorArgs(budgetId),
        observeFormData = ObserveBudgetFormDataUseCase(budgets, categories, accounts),
        saveBudget = SaveBudgetUseCase(budgets, categories),
        deleteBudget = DeleteBudgetUseCase(budgets),
        clock = clock,
    )

    @Test
    fun `tạo mới - tiền tệ theo ví đầu tiên - chỉ hiện danh mục chi cấp 1 chưa có ngân sách`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("VND", state.currency)
        assertEquals(listOf("VND", "USD"), state.currencyOptions)
        assertTrue(state.canChooseCurrency)
        // food đã có ngân sách VND, coffee là danh mục con, salary là thu, hobby đã lưu trữ.
        assertEquals(listOf("shop"), state.categoryOptions.map { it.id })
    }

    @Test
    fun `đổi tiền tệ thì danh mục đã có ngân sách ở tiền tệ mới bị bỏ chọn`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(BudgetEditorIntent.CategorySelected("shop"))

        vm.onIntent(BudgetEditorIntent.CurrencySelected("USD"))

        val state = vm.state.value
        assertEquals(listOf("food"), state.categoryOptions.map { it.id })
        assertNull(state.categoryId)
        assertFalse(state.canSave)
    }

    @Test
    fun `lưu ngân sách mới áp dụng từ tháng này rồi đóng form`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.CategorySelected("shop"))
            vm.onIntent(BudgetEditorIntent.AmountChanged("2000000"))
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(BudgetEditorEffect.Close, awaitItem())
        }
        assertEquals(listOf(BudgetDraft("shop", "VND", 2_000_000, november2023)), budgets.created)
    }

    @Test
    fun `sửa chỉ đổi được số tiền - danh mục và tiền tệ bị khoá`() = runTest(dispatcher) {
        val vm = viewModel("b-food")
        advanceUntilIdle()
        val loaded = vm.state.value
        assertFalse(loaded.isNew)
        assertEquals("Ăn uống", loaded.categoryName)
        assertEquals("VND", loaded.currency)
        assertFalse(loaded.canChooseCurrency)

        vm.onIntent(BudgetEditorIntent.CategoryClicked)
        vm.onIntent(BudgetEditorIntent.CurrencyClicked)
        assertFalse(vm.state.value.categoryPickerVisible)
        assertFalse(vm.state.value.currencyPickerVisible)

        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.AmountChanged("5000000"))
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(BudgetEditorEffect.Close, awaitItem())
        }
        assertEquals(Money(5_000_000, "VND"), budgets.budgets.value.first { it.id == "b-food" }.amount)
    }

    @Test
    fun `lỗi trùng hiện dưới ô danh mục và form không đóng`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val duplicate = FieldError("categoryId", BudgetErrorCode.DUPLICATE)
        budgets.failNext = AppError.Validation(listOf(duplicate))

        vm.onIntent(BudgetEditorIntent.CategorySelected("shop"))
        vm.onIntent(BudgetEditorIntent.AmountChanged("1000"))
        vm.onIntent(BudgetEditorIntent.SaveClicked)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(duplicate.toBudgetUiText(), state.categoryError)
        assertNull(state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun `xoá phải xác nhận rồi mới xoá và đóng form`() = runTest(dispatcher) {
        val vm = viewModel("b-food")
        advanceUntilIdle()

        vm.onIntent(BudgetEditorIntent.DeleteClicked)
        assertTrue(vm.state.value.deleteConfirmVisible)
        assertEquals(emptyList(), budgets.deleted)

        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.DeleteConfirmed)
            advanceUntilIdle()
            assertEquals(BudgetEditorEffect.Close, awaitItem())
        }
        assertEquals(listOf("b-food"), budgets.deleted)
    }

    @Test
    fun `ngân sách không còn thì đóng form`() = runTest(dispatcher) {
        val vm = viewModel("ghost")

        vm.effects.test {
            advanceUntilIdle()
            assertEquals(BudgetEditorEffect.Close, awaitItem())
        }
    }

    @Test
    fun `đang sửa mà ngân sách bị xoá ở máy khác thì báo ngay và khoá lưu - giữ số đang gõ`() = runTest(dispatcher) {
        val vm = viewModel("b-food")
        advanceUntilIdle()
        vm.onIntent(BudgetEditorIntent.AmountChanged("4500000"))

        budgets.budgets.value = budgets.budgets.value.filterNot { it.id == "b-food" }
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isDeletedElsewhere)
        assertFalse(state.canSave)
        assertEquals(FieldError("budget", BudgetErrorCode.DELETED).toBudgetUiText(), state.error)
        assertEquals("4500000", state.amount)
        vm.onIntent(BudgetEditorIntent.DeleteClicked)
        assertFalse(vm.state.value.deleteConfirmVisible)
    }

    @Test
    fun `đổi tên danh mục ở máy khác thì form sửa cập nhật tên - không đè số đang gõ`() = runTest(dispatcher) {
        val vm = viewModel("b-food")
        advanceUntilIdle()
        vm.onIntent(BudgetEditorIntent.AmountChanged("4500000"))

        categories.categories.value = categories.categories.value.map { if (it.id == "food") it.copy(name = "Ăn ngoài") else it }
        advanceUntilIdle()

        assertEquals("Ăn ngoài", vm.state.value.categoryName)
        assertEquals("4500000", vm.state.value.amount)
    }
}
