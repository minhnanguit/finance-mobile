package com.uit.finance.feature.recurring.domain

import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.recurring.domain.model.LocalOccurrence
import com.uit.finance.core.recurring.domain.model.OccurrenceIds
import com.uit.finance.core.recurring.testing.FakeRecurringRepository
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.recurring.TODAY
import com.uit.finance.feature.recurring.domain.model.AttentionReason
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringOverviewUseCase
import com.uit.finance.feature.recurring.draft
import com.uit.finance.feature.recurring.rent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class ObserveRecurringOverviewUseCaseTest {

    private val recurrings = FakeRecurringRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val useCase = ObserveRecurringOverviewUseCase(recurrings, transactions, accounts, categories, TestClock(NOON_2023_11_14))

    init {
        accounts.accounts.value = listOf(account("cash"))
        categories.categories.value = listOf(category("home", "Nhà ở"))
    }

    @Test
    fun `nháp đúng kỳ vào cần xác nhận - nháp theo lịch cũ vào cần kiểm tra - nháp tay bị bỏ qua`() = runTest {
        recurrings.recurrings.value = listOf(rent())
        val due = OccurrenceIds.of("rent", LocalDate(2023, 11, 5))
        val stale = OccurrenceIds.of("rent", LocalDate(2023, 11, 3))
        transactions.transactions.value = listOf(
            draft(due, LocalDate(2023, 11, 5)),
            draft(stale, LocalDate(2023, 11, 3)),
            draft("manual", LocalDate(2023, 11, 10), payee = "Cà phê"),
        )
        recurrings.localOccurrences.value = listOf(LocalOccurrence(stale, "rent", LocalDate(2023, 11, 3), "old-rev"))

        val overview = useCase().first()

        assertEquals(listOf(due), overview.toConfirm.map { it.transactionId })
        assertEquals(listOf(stale), overview.toReview.map { it.transactionId })
    }

    @Test
    fun `sắp đến hạn từ ngày mai tới 30 ngày - tổng theo tiền tệ - kỳ hôm nay không tính`() = runTest {
        recurrings.recurrings.value = listOf(rent(day = 5), rent(id = "phone", day = 14, amount = 59_000))

        val overview = useCase().first()

        assertEquals(listOf(LocalDate(2023, 12, 5), LocalDate(2023, 12, 13)).take(1), overview.upcoming.map { it.date }.take(1))
        assertTrue(overview.upcoming.none { it.date == TODAY })
        assertEquals(3_000_000, overview.upcomingTotals.single().expense.amountMinor)
        assertEquals(LocalDate(2023, 12, 5), overview.recurrings.first { it.recurring.id == "rent" }.next)
    }

    @Test
    fun `ví đã lưu trữ hoặc khoản bị chặn thì vào cần chú ý và không có trong sắp đến hạn`() = runTest {
        accounts.accounts.value = listOf(account("cash", archived = true))
        recurrings.recurrings.value = listOf(rent(), rent(id = "blocked", accountId = "other").copy(blockedFrom = LocalDate(2023, 11, 5)))
        accounts.accounts.value = listOf(account("cash", archived = true), account("other"))

        val overview = useCase().first()

        assertEquals(
            mapOf("rent" to AttentionReason.ACCOUNT_UNAVAILABLE, "blocked" to AttentionReason.BLOCKED),
            overview.attention.associate { it.recurring.id to it.reason },
        )
        assertTrue(overview.upcoming.isEmpty())
    }
}
