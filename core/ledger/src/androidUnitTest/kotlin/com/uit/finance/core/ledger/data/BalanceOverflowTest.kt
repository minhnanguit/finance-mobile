package com.uit.finance.core.ledger.data

import app.cash.turbine.test
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Review Phase 4 (Codex #8): mỗi giao dịch được tới 10¹⁵, nên ~9.300 khoản tối đa đã vượt Long. `SUM` của
 * SQLite khi đó ném "integer overflow" và làm chết cả Flow số dư. Câu số dư mới không bao giờ ném lỗi.
 */
class BalanceOverflowTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!

    private fun TestScope.balances() = testDispatcherProvider().let { dispatchers ->
        BalanceRepositoryImpl(ledgerStore(databases, FakeScheduler(), dispatchers), dispatchers)
    }

    private fun account(id: String, opening: Long = 0) =
        database.accountQueries.upsert(id, id, "CASH", "VND", opening, 0, 0, 0)

    private fun transactions(accountId: String, type: String, amount: Long, count: Int, counterAccountId: String? = null) =
        database.transaction {
            repeat(count) { index ->
                database.ledgerTransactionQueries.upsert(
                    "$accountId-$type-$index", type, "CONFIRMED", accountId, counterAccountId, amount, "VND",
                    null, "2023-11-14", null, null, null, 0,
                )
            }
        }

    @Test
    fun `số dư vẫn chính xác tới từng đồng khi gần biên Long`() = runTest {
        account("big", opening = -7)
        transactions("big", "INCOME", LedgerLimits.MAX_AMOUNT_MINOR, count = 9_000) // 9 × 10¹⁸
        transactions("big", "EXPENSE", 999_999_999, count = 3)

        balances().observeBalances().test {
            val expected = -7 + 9_000 * LedgerLimits.MAX_AMOUNT_MINOR - 3 * 999_999_999L
            assertEquals(expected, awaitItem().accounts.single().balance.amountMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `thu chi trong kỳ chính xác gần biên Long và kẹt ở biên khi vượt`() = runTest {
        account("a")
        account("b") // id giao dịch sinh theo (ví, loại, thứ tự): dùng ví khác để 3 khoản nhỏ không đè lên khoản lớn
        transactions("a", "EXPENSE", LedgerLimits.MAX_AMOUNT_MINOR, count = 9_000)
        transactions("b", "EXPENSE", 999_999_999, count = 3)
        transactions("a", "INCOME", LedgerLimits.MAX_AMOUNT_MINOR, count = 10_000) // 10¹⁹ > Long.MAX
        val day = LocalDate(2023, 11, 14)

        balances().observeCashFlow(day, day).test {
            val cashFlow = awaitItem()
            assertEquals(9_000 * LedgerLimits.MAX_AMOUNT_MINOR + 3 * 999_999_999L, cashFlow.expense("VND").amountMinor)
            assertEquals(Long.MAX_VALUE, cashFlow.income("VND").amountMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tổng vượt Long thì kẹt ở biên, không ném lỗi`() = runTest {
        account("a")
        account("b")
        transactions("a", "INCOME", LedgerLimits.MAX_AMOUNT_MINOR, count = 10_000) // 10¹⁹ > Long.MAX
        transactions("b", "INCOME", LedgerLimits.MAX_AMOUNT_MINOR, count = 9_000)
        transactions("b", "TRANSFER", LedgerLimits.MAX_AMOUNT_MINOR, count = 1, counterAccountId = "a")

        balances().observeBalances().test {
            val summary = awaitItem()
            assertEquals(Long.MAX_VALUE, summary.accounts.first { it.accountId == "a" }.balance.amountMinor)
            assertEquals(Long.MAX_VALUE, summary.totals.single().amountMinor, "tổng nhiều ví cũng không ném lỗi")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
