package com.uit.finance.feature.budgets.domain

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.testing.FakeBudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.feature.budgets.domain.model.BudgetInput
import com.uit.finance.feature.budgets.domain.usecase.SaveBudgetUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth

class SaveBudgetUseCaseTest {

    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val useCase = SaveBudgetUseCase(budgets, categories)
    private val october = YearMonth(2026, 10)

    private fun budgetOf(categoryId: String) = Budget("b-$categoryId", categoryId, Money(1, "VND"), october)

    @Test
    fun `chỉ ngân sách của danh mục chi cấp 1 còn trên máy được đếm vào giới hạn`() = runTest {
        categories.categories.value = listOf(
            category("food", "Ăn uống"),
            category("hidden", "Sở thích", archived = true),
            category("food", "Ăn uống").copy(id = "coffee", parentId = "food"),
            category("salary", "Lương", kind = CategoryKind.INCOME),
        )

        useCase(BudgetInput(null, "food", "VND", 1_000, october))

        val counts = budgets.lastLimitFilter!!
        assertEquals(true, counts(budgetOf("food")))
        assertEquals(true, counts(budgetOf("hidden")))
        // Danh mục đã thành con, đã bị xoá (không còn trong danh sách) hay không phải chi: không chiếm chỗ.
        assertEquals(false, counts(budgetOf("coffee")))
        assertEquals(false, counts(budgetOf("deleted-elsewhere")))
        assertEquals(false, counts(budgetOf("salary")))
    }

    @Test
    fun `sửa chỉ đổi số tiền - không đụng tới giới hạn`() = runTest {
        budgets.budgets.value = listOf(budgetOf("food"))

        useCase(BudgetInput("b-food", "food", "VND", 9_000, october))

        assertEquals(9_000, budgets.budgets.value.single().amount.amountMinor)
        assertNull(budgets.lastLimitFilter)
    }
}
