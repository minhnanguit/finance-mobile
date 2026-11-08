package com.uit.finance.feature.reports.domain.model

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Một nhóm trong biểu đồ tròn / danh sách (docs/REPORTS-PLAN.md R3, R5).
 *
 * @param category `null` = "Chưa phân loại": giao dịch không có danh mục, hoặc danh mục chưa sync về máy
 * @param percent 0..100, một chữ số lẻ, làm tròn xuống để tổng các % không bao giờ vượt 100
 * @param hasChildren danh mục có danh mục con: chạm vào thì mở màn danh mục con, không thì mở thẳng giao dịch
 */
data class CategoryShare(
    val category: Category?,
    val total: Money,
    val percent: Double,
    val hasChildren: Boolean = false,
) {
    val categoryId: String? get() = category?.id
}

/**
 * So với kỳ trước (R6). Kỳ đang chạy thì so **cùng số ngày** (1–10/10 với 1–10/9), kỳ đã hết thì so cả kỳ.
 *
 * @param sameRange true = kỳ đang chạy, đã so cùng kỳ
 * @param changePercent (hiện tại − trước) / trước × 100; kỳ trước luôn > 0 (bằng 0 thì không có so sánh)
 */
data class ReportComparison(
    val previousPeriod: ReportPeriod,
    val current: Money,
    val previous: Money,
    val changePercent: Double,
    val sameRange: Boolean,
)

/** Một cột xu hướng (R7, V5). [isPartial] = tháng chưa hết; tháng tương lai trong chế độ Năm có [isFuture]. */
data class TrendPoint(
    val month: YearMonth,
    val total: Money,
    val isPartial: Boolean,
    val isFuture: Boolean,
)

/** Mọi số của màn Báo cáo cho một kỳ, một loại (chi/thu), một tiền tệ. */
data class ReportOverview(
    val period: ReportPeriod,
    val kind: CategoryKind,
    val currency: String,
    /** Mọi tiền tệ chọn được: tiền tệ của ví (theo thứ tự ví) rồi tới tiền tệ có phát sinh trong kỳ. */
    val currencies: List<String>,
    val today: LocalDate,
    /** Thu, chi của cả kỳ: bằng đúng số "Tháng này" ở Trang chủ. */
    val income: Money,
    val expense: Money,
    val comparison: ReportComparison?,
    /** Mọi nhóm, lớn trước. Biểu đồ lấy [TOP_COUNT] nhóm đầu, còn lại gộp vào "Khác". */
    val shares: List<CategoryShare>,
    val trend: List<TrendPoint>,
    /** Ví của [currency] để lịch sử giao dịch lọc đúng tiền tệ đang xem; `null` = mọi ví cùng tiền tệ, không cần lọc. */
    val accountIds: Set<String>? = null,
) {
    /** Số chính của màn: chi hoặc thu tuỳ [kind]. */
    val total: Money get() = if (kind == CategoryKind.EXPENSE) expense else income

    val top: List<CategoryShare> get() = if (shares.size <= TOP_COUNT + 1) shares else shares.take(TOP_COUNT)

    /** Nhóm gộp vào "Khác"; rỗng khi không quá [TOP_COUNT] + 1 nhóm (gộp 1 nhóm vào "Khác" không có ích). */
    val others: List<CategoryShare> get() = if (shares.size <= TOP_COUNT + 1) emptyList() else shares.drop(TOP_COUNT)

    val othersTotal: Money get() = Money(others.fold(0L) { sum, it -> sum.saturatingPlus(it.total.amountMinor) }, currency)

    val othersPercent: Double get() = floorTenth(others.sumOf { it.percent }.coerceAtMost(MAX_PERCENT))

    val isOngoing: Boolean get() = today in period

    /** Đang ở kỳ hiện tại: không có kỳ sau để xem (R1). */
    val canGoNext: Boolean get() = !period.next().isAfter(today)

    val isEmpty: Boolean get() = income.amountMinor == 0L && expense.amountMinor == 0L

    companion object {
        const val TOP_COUNT = 5
    }
}

/** Màn danh mục con: tổng của danh mục cha và từng con (gồm phần ghi thẳng vào cha). */
data class CategoryBreakdown(
    val parent: Category?,
    val total: Money,
    val shares: List<CategoryShare>,
    /** Như [ReportOverview.accountIds]. */
    val accountIds: Set<String>? = null,
)

internal const val MAX_PERCENT = 100.0

/** Làm tròn xuống một chữ số lẻ. */
internal fun floorTenth(value: Double): Double = kotlin.math.floor(value * 10.0 + 1e-9) / 10.0

/** Cộng Long kẹt ở biên thay vì tràn (giống SQL: tổng vượt Long kẹt ở biên). */
internal fun Long.saturatingPlus(other: Long): Long {
    val sum = this + other
    return when {
        this > 0 && other > 0 && sum < 0 -> Long.MAX_VALUE
        this < 0 && other < 0 && sum >= 0 -> Long.MIN_VALUE
        else -> sum
    }
}

internal fun Long.saturatingMinus(other: Long): Long =
    if (other == Long.MIN_VALUE) (if (this >= 0) Long.MAX_VALUE else this - other) else saturatingPlus(-other)
