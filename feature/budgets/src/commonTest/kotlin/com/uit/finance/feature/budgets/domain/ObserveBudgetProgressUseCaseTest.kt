package com.uit.finance.feature.budgets.domain

import app.cash.turbine.test
import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.testing.FakeBudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSpendingRepository
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.feature.budgets.domain.model.BudgetTone
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetProgressUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class ObserveBudgetProgressUseCaseTest {

    private val budgets = FakeBudgetRepository()
    private val spending = FakeSpendingRepository()
    private val categories = FakeCategoryRepository()
    private val useCase = ObserveBudgetProgressUseCase(budgets, spending, categories)
    private val october = YearMonth(2026, 10)
    private val today = LocalDate(2026, 10, 17)

    private fun budget(id: String, categoryId: String, amount: Long, currency: String = "VND", start: YearMonth = october) =
        Budget(id, categoryId, Money(amount, currency), start)

    @Test
    fun `ghép ngân sách với đã chi đúng danh mục và tiền tệ - gần vượt nhất lên đầu`() = runTest {
        categories.categories.value = listOf(category("food", "Ăn uống"), category("shop", "Mua sắm"), category("move", "Đi lại"))
        budgets.budgets.value = listOf(
            budget("b-food", "food", 3_000_000),
            budget("b-food-usd", "food", 100_00, currency = "USD"),
            budget("b-shop", "shop", 2_000_000),
            budget("b-move", "move", 1_000_000),
        )
        spending.spending.value = mapOf(
            october to listOf(
                CategorySpend("food", Money(2_550_000, "VND")),
                CategorySpend("food", Money(20_00, "USD")),
                CategorySpend("shop", Money(2_300_000, "VND")),
                CategorySpend("move", Money(400_000, "VND")),
            ),
        )

        useCase(october, today).test {
            val overview = awaitItem()
            assertEquals(listOf("b-shop", "b-food", "b-move", "b-food-usd"), overview.items.map { it.budget.id })
            val shop = overview.items.first()
            assertEquals(115, shop.percent)
            assertEquals(BudgetTone.Over, shop.tone)
            assertEquals(Money(-300_000, "VND"), shop.remaining)
            assertNull(shop.dailyAllowance)
            val food = overview.items[1]
            assertEquals(BudgetTone.Warning, food.tone)
            assertEquals(Money(30_000, "VND"), food.dailyAllowance)
            assertEquals(20, overview.items.last().percent)
        }
    }

    @Test
    fun `thẻ tổng mỗi tiền tệ một dòng`() = runTest {
        budgets.budgets.value = listOf(budget("a", "food", 3_000_000), budget("b", "shop", 2_000_000), budget("c", "food", 100_00, "USD"))
        spending.spending.value = mapOf(october to listOf(CategorySpend("food", Money(2_550_000, "VND"))))

        useCase(october, today).test {
            val totals = awaitItem().totals
            assertEquals(listOf("USD", "VND"), totals.map { it.currency })
            val vnd = totals.last()
            assertEquals(Money(5_000_000, "VND"), vnd.budgeted)
            assertEquals(Money(2_550_000, "VND"), vnd.spent)
            assertEquals(Money(2_450_000, "VND"), vnd.remaining)
        }
    }

    @Test
    fun `tháng trước tháng bắt đầu không hiện ngân sách đó`() = runTest {
        budgets.budgets.value = listOf(budget("old", "food", 1, start = YearMonth(2026, 9)), budget("new", "shop", 1, start = YearMonth(2026, 11)))

        useCase(october, today).test {
            assertEquals(listOf("old"), awaitItem().items.map { it.budget.id })
        }
    }

    @Test
    fun `danh mục chưa sync tới vẫn hiện ngân sách với category null`() = runTest {
        budgets.budgets.value = listOf(budget("b", "missing", 1_000))

        useCase(october, today).test {
            val item = awaitItem().items.single()
            assertNull(item.category)
            assertEquals(Money(0, "VND"), item.spent)
        }
    }

    @Test
    fun `tháng cũ không có số mỗi ngày còn được chi`() = runTest {
        val september = YearMonth(2026, 9)
        budgets.budgets.value = listOf(budget("b", "food", 1_000, start = september))

        useCase(september, today).test {
            assertNull(awaitItem().items.single().dailyAllowance)
        }
        assertEquals(listOf(september), spending.requestedMonths)
    }
}
