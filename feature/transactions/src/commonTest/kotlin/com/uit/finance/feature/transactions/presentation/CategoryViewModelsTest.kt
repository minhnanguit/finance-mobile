@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.transactions.presentation

import app.cash.turbine.test
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.feature.transactions.domain.usecase.ObserveCategoriesUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveCategoryUseCase
import com.uit.finance.feature.transactions.domain.usecase.SetCategoryArchivedUseCase
import com.uit.finance.feature.transactions.presentation.categories.CategoriesArgs
import com.uit.finance.feature.transactions.presentation.categories.CategoriesEffect
import com.uit.finance.feature.transactions.presentation.categories.CategoriesIntent
import com.uit.finance.feature.transactions.presentation.categories.CategoriesViewModel
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorArgs
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorEffect
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorIntent
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class CategoryViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categories.categories.value = listOf(
            category("b", "Bún", templateKey = "food"),
            category("a", "Ăn vặt"),
            category("x", "Cũ", archived = true),
            category("s", "Lương", kind = CategoryKind.INCOME),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `danh sách theo loại - sắp tên - tách lưu trữ - đánh dấu mặc định`() = runTest(dispatcher) {
        val vm = CategoriesViewModel(CategoriesArgs(CategoryKind.EXPENSE), ObserveCategoriesUseCase(categories), SetCategoryArchivedUseCase(categories))
        advanceUntilIdle()

        assertEquals(listOf("Bún", "Ăn vặt").sortedBy { it.lowercase() }, vm.state.value.active.map { it.name })
        assertEquals(listOf("x"), vm.state.value.archived.map { it.id })
        assertEquals(true, vm.state.value.active.first { it.id == "b" }.isDefault)

        vm.onIntent(CategoriesIntent.KindChanged(CategoryKind.INCOME))
        assertEquals(listOf("s"), vm.state.value.active.map { it.id })

        vm.effects.test {
            vm.onIntent(CategoriesIntent.AddClicked)
            assertEquals(CategoriesEffect.OpenEditor(null, CategoryKind.INCOME), awaitItem())
        }
    }

    @Test
    fun `tạo danh mục với icon và màu rồi đóng`() = runTest(dispatcher) {
        val vm = CategoryEditorViewModel(CategoryEditorArgs(null, CategoryKind.INCOME), ObserveCategoriesUseCase(categories), SaveCategoryUseCase(categories))
        vm.onIntent(CategoryEditorIntent.NameChanged("Freelance"))
        vm.onIntent(CategoryEditorIntent.IconSelected("payments"))
        vm.onIntent(CategoryEditorIntent.ColorSelected("#22C55E"))

        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(CategoryEditorEffect.Close, awaitItem())
        }
        val created = categories.categories.value.last()
        assertEquals(CategoryKind.INCOME, created.kind)
        assertEquals("payments", created.icon)
        assertEquals("#22C55E", created.color)
    }

    @Test
    fun `sửa danh mục không đổi được loại thu chi`() = runTest(dispatcher) {
        val vm = CategoryEditorViewModel(CategoryEditorArgs("b", CategoryKind.EXPENSE), ObserveCategoriesUseCase(categories), SaveCategoryUseCase(categories))
        advanceUntilIdle()
        assertEquals("Bún", vm.state.value.name)

        vm.onIntent(CategoryEditorIntent.KindChanged(CategoryKind.INCOME))
        assertEquals(CategoryKind.EXPENSE, vm.state.value.kind)
    }
}
