package com.uit.finance.feature.home.domain.usecase

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeBalanceRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.balance
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class ObserveHomeDashboardUseCaseTest {

    private val accounts = FakeAccountRepository()
    private val balances = FakeBalanceRepository()
    private val transactions = FakeTransactionRepository()
    private val categories = FakeCategoryRepository()
    private val today = LocalDate(2023, 11, 14)

    private val useCase = ObserveHomeDashboardUseCase(accounts, balances, transactions, categories)

    @Test
    fun `hỏi thu chi đúng tháng của ngày truyền vào và 5 giao dịch gần nhất`() = runTest {
        useCase(today).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(LocalDate(2023, 11, 1) to LocalDate(2023, 11, 30), balances.requestedRange)
        assertEquals(5, transactions.requestedLimit)

        useCase(LocalDate(2024, 2, 10)).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(LocalDate(2024, 2, 1) to LocalDate(2024, 2, 29), balances.requestedRange)
    }

    @Test
    fun `ghép số dư vào ví - bỏ ví archive - ghép ví và danh mục vào giao dịch`() = runTest {
        accounts.accounts.value = listOf(account("a"), account("b", archived = true), account("c", currency = "USD"))
        balances.balances.value = BalanceSummary(
            accounts = listOf(balance("a", 120_000), balance("b", 5), balance("c", 9_900, "USD")),
            totals = emptyList(),
        )
        balances.cashFlow.value = CashFlow(income = listOf(Money(50_000, "VND")), expense = emptyList())
        categories.categories.value = listOf(category("food", "Ăn uống", icon = "utensils"))
        transactions.transactions.value = listOf(
            transaction("t1", TransactionType.EXPENSE, "a", 30_000, today, categoryId = "food"),
            transaction("t2", TransactionType.INCOME, "zz", 1, today, categoryId = "missing"),
        )

        useCase(today).test {
            val dashboard = awaitItem()
            assertEquals(listOf("a", "c"), dashboard.accounts.map { it.account.id })
            assertEquals(Money(120_000, "VND"), dashboard.accounts[0].balance)
            assertEquals(Money(9_900, "USD"), dashboard.accounts[1].balance)
            assertEquals(Money(50_000, "VND"), dashboard.monthCashFlow.income("VND"))

            val (first, second) = dashboard.recentTransactions
            assertEquals("Ăn uống", first.category?.name)
            assertEquals("Ví a", first.account?.name)
            assertNull(second.category)
            assertNull(second.account)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ví chưa có dòng số dư thì dùng số dư ban đầu`() = runTest {
        accounts.accounts.value = listOf(account("a").copy(openingBalance = Money(7_000, "VND")))

        useCase(today).test {
            assertEquals(Money(7_000, "VND"), awaitItem().accounts.single().balance)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
