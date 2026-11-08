@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.reports.presentation

import app.cash.turbine.test
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownArgs
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownEffect
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownIntent
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownViewModel
import com.uit.finance.feature.reports.presentation.common.ReportFormatter
import com.uit.finance.feature.reports.presentation.common.ShareAction
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import com.uit.finance.feature.reports.testing.ReportFixture
import com.uit.finance.feature.reports.testing.expenseCategory
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
import kotlinx.datetime.YearMonth

class CategoryBreakdownViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixture = ReportFixture()
    private val october = ReportPeriod.Month(YearMonth(2026, 10))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(period: ReportPeriod? = october) = CategoryBreakdownViewModel(
        args = CategoryBreakdownArgs("food", period, CategoryKind.EXPENSE, "VND"),
        observeBreakdown = fixture.breakdown,
        format = ReportFormatter(MoneyFormatter()),
    )

    @Test
    fun `tách từng con - phần ghi thẳng vào cha không mở riêng - chạm con mở giao dịch của con`() = runTest(dispatcher) {
        fixture.categories(expenseCategory("food"), expenseCategory("coffee", parentId = "food"))
        fixture.spend("food", 250_000, LocalDate(2026, 10, 2))
        fixture.spend("coffee", 750_000, LocalDate(2026, 10, 3))
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Food", state.title)
        assertEquals("1.000.000", state.total)
        assertEquals(listOf("coffee", "food"), state.rows.map { it.categoryId })
        assertEquals(listOf("75%", "25%"), state.rows.map { it.percentLabel })
        assertEquals(ShareAction.None, state.rows.last().action)

        vm.effects.test {
            vm.onIntent(CategoryBreakdownIntent.OpenRow(state.rows.first()))
            assertEquals(
                CategoryBreakdownEffect.OpenTransactions(
                    ReportTransactionsQuery(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), "coffee", CategoryKind.EXPENSE),
                ),
                awaitItem(),
            )
            vm.onIntent(CategoryBreakdownIntent.OpenRow(state.rows.last()))
            vm.onIntent(CategoryBreakdownIntent.OpenAll)
            assertEquals("food", (awaitItem() as CategoryBreakdownEffect.OpenTransactions).query.categoryId)
        }
    }

    @Test
    fun `route hỏng thì hiện trống thay vì crash`() = runTest(dispatcher) {
        val vm = viewModel(period = null)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }
}
