package com.uit.finance.feature.transactions.domain

import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.feature.transactions.domain.model.DatePreset
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class HistoryFilterTest {

    private val today = LocalDate(2024, 3, 15)

    private fun range(preset: DatePreset) = HistoryFilter(datePreset = preset).dateRange(today)

    @Test
    fun `preset tính theo hôm nay - tháng đủ ngày kể cả năm nhuận`() {
        assertEquals(null to null, range(DatePreset.ALL))
        assertEquals(date(2024, 3, 1) to date(2024, 3, 31), range(DatePreset.THIS_MONTH))
        assertEquals(date(2024, 2, 1) to date(2024, 2, 29), range(DatePreset.LAST_MONTH))
        assertEquals(date(2024, 2, 15) to date(2024, 3, 15), range(DatePreset.LAST_30_DAYS))
        assertEquals(date(2024, 1, 1) to date(2024, 12, 31), range(DatePreset.THIS_YEAR))
    }

    @Test
    fun `tháng trước của tháng 1 là tháng 12 năm trước`() {
        assertEquals(
            date(2023, 12, 1) to date(2023, 12, 31),
            HistoryFilter(datePreset = DatePreset.LAST_MONTH).dateRange(date(2024, 1, 10)),
        )
    }

    @Test
    fun `tuỳ chọn chưa có ngày nào thì chưa phải điều kiện`() {
        assertEquals(0, HistoryFilter(datePreset = DatePreset.CUSTOM).activeCount)
        assertEquals(1, HistoryFilter(datePreset = DatePreset.CUSTOM, customTo = today).activeCount)
    }

    @Test
    fun `không bật gì thì ra đúng TransactionFilter ALL`() {
        assertEquals(TransactionFilter.ALL, HistoryFilter().toTransactionFilter(query = "  ", today = today))
    }

    @Test
    fun `đổi đủ mọi điều kiện sang TransactionFilter`() {
        val filter = HistoryFilter(
            datePreset = DatePreset.THIS_MONTH,
            types = setOf(TransactionType.EXPENSE),
            accountIds = setOf("a"),
            categoryIds = setOf("c"),
            draftsOnly = true,
        )

        assertEquals(
            TransactionFilter(
                query = "ca phe",
                from = date(2024, 3, 1),
                to = date(2024, 3, 31),
                types = setOf(TransactionType.EXPENSE),
                accountIds = setOf("a"),
                categoryIds = setOf("c"),
                status = TransactionStatus.DRAFT,
            ),
            filter.toTransactionFilter(query = " ca phe ", today = today),
        )
        assertEquals(5, filter.activeCount)
    }

    @Test
    fun `điền sẵn từ màn khác dùng khoảng ngày cố định`() {
        val prefilled = HistoryFilter.prefilled(from = date(2023, 10, 1), to = date(2023, 10, 31), accountId = "a")

        assertEquals(DatePreset.CUSTOM, prefilled.datePreset)
        assertEquals(date(2023, 10, 1) to date(2023, 10, 31), prefilled.dateRange(today))
        assertEquals(setOf("a"), prefilled.accountIds)
        assertEquals(DatePreset.ALL, HistoryFilter.prefilled(categoryId = "c").datePreset)
    }

    private fun date(year: Int, month: Int, day: Int) = LocalDate(year, month, day)
}
