package com.uit.finance.feature.transactions.domain.model

import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Lựa chọn khoảng ngày của bộ lọc lịch sử (docs/SEARCH-PLAN.md §1). Mọi preset tính theo "hôm nay". */
enum class DatePreset { ALL, THIS_MONTH, LAST_MONTH, LAST_30_DAYS, THIS_YEAR, CUSTOM }

/**
 * Các điều kiện của sheet lọc lịch sử. Từ khoá không nằm ở đây: nó đi riêng (gõ xong 300 ms mới tìm) và không bao
 * giờ được lưu hay truyền qua navigation (F11).
 *
 * Giữ preset chứ không giữ ngày cụ thể, để "Tháng này" tự đổi khi qua tháng mới. Tập rỗng = không lọc theo điều kiện đó.
 */
data class HistoryFilter(
    val datePreset: DatePreset = DatePreset.ALL,
    /** Chỉ dùng khi [datePreset] là [DatePreset.CUSTOM]; một đầu `null` = không giới hạn đầu đó. */
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val types: Set<TransactionType> = emptySet(),
    /** Gồm được cả ví đã lưu trữ; chuyển tiền khớp cả ví gửi lẫn ví nhận. */
    val accountIds: Set<String> = emptySet(),
    /** Chọn danh mục cha là gồm luôn các danh mục con. */
    val categoryIds: Set<String> = emptySet(),
    val draftsOnly: Boolean = false,
) {
    /** "Tuỳ chọn" mà chưa chọn ngày nào thì chưa phải điều kiện. */
    val hasDateRange: Boolean
        get() = datePreset != DatePreset.ALL && !(datePreset == DatePreset.CUSTOM && customFrom == null && customTo == null)

    /** Số điều kiện đang bật: số hiện trên nút lọc, cũng là số chip dưới ô tìm kiếm. */
    val activeCount: Int
        get() = listOf(hasDateRange, types.isNotEmpty(), accountIds.isNotEmpty(), categoryIds.isNotEmpty(), draftsOnly)
            .count { it }

    /** Khoảng ngày (gồm cả hai đầu) ứng với [today]; `null` ở đầu nào là không giới hạn đầu đó. */
    fun dateRange(today: LocalDate): Pair<LocalDate?, LocalDate?> = when (datePreset) {
        DatePreset.ALL -> null to null
        // Cả tháng, giống thẻ "Tháng này" ở Trang chủ: hai số phải bằng nhau.
        DatePreset.THIS_MONTH -> today.monthStart().let { it to it.monthEnd() }
        DatePreset.LAST_MONTH -> today.monthStart().minus(1, DateTimeUnit.MONTH).let { it to it.monthEnd() }
        DatePreset.LAST_30_DAYS -> today.minus(LAST_DAYS - 1, DateTimeUnit.DAY) to today
        DatePreset.THIS_YEAR -> LocalDate(today.year, 1, 1) to LocalDate(today.year, 12, 31)
        DatePreset.CUSTOM -> customFrom to customTo
    }

    /** Điều kiện cho repository, ghép thêm từ khoá [query]. */
    fun toTransactionFilter(query: String, today: LocalDate): TransactionFilter {
        val (from, to) = dateRange(today)
        return TransactionFilter(
            query = query,
            from = from,
            to = to,
            types = types,
            accountIds = accountIds,
            categoryIds = categoryIds,
            status = if (draftsOnly) TransactionStatus.DRAFT else null,
        ).normalized()
    }

    companion object {
        private const val LAST_DAYS = 30

        /** Bộ lọc điền sẵn khi mở lịch sử từ màn khác (F11): khoảng ngày cố định, không trượt theo hôm nay. */
        fun prefilled(
            from: LocalDate? = null,
            to: LocalDate? = null,
            categoryId: String? = null,
            accountId: String? = null,
            type: TransactionType? = null,
        ) = HistoryFilter(
            datePreset = if (from == null && to == null) DatePreset.ALL else DatePreset.CUSTOM,
            customFrom = from,
            customTo = to,
            types = setOfNotNull(type),
            accountIds = setOfNotNull(accountId),
            categoryIds = setOfNotNull(categoryId),
        )
    }
}

private fun LocalDate.monthStart() = LocalDate(year, month, 1)

private fun LocalDate.monthEnd() = plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
