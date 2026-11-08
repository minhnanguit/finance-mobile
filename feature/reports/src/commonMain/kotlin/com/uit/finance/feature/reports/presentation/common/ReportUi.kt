package com.uit.finance.feature.reports.presentation.common

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.designsystem.theme.CategoryPalette
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.reports.domain.model.CategoryShare
import kotlin.math.abs
import kotlin.math.roundToLong

/** Cách tô một nhóm: màu danh mục, xám đậm cho "Chưa phân loại", xám nhạt theo theme cho "Khác" (V3, V4). */
enum class ShareStyle { Category, Uncategorized, Other }

/** Chạm vào một dòng thì làm gì. */
enum class ShareAction { None, OpenBreakdown, OpenTransactions, ToggleOthers }

/** Một dòng dưới biểu đồ tròn; cũng là chú thích của biểu đồ nên [colorHex] = màu lát. */
data class ShareRowUi(
    val key: String,
    val categoryId: String?,
    val name: String,
    val amount: String,
    val currency: String,
    val percentLabel: String,
    /** 0..1: độ dài thanh tỷ lệ. */
    val fraction: Float,
    val colorHex: String?,
    val iconKey: String?,
    val kind: CategoryKind,
    val style: ShareStyle,
    val action: ShareAction,
    val subtitle: String? = null,
)

data class SliceUi(val key: String, val weight: Double, val colorHex: String?, val style: ShareStyle)

/**
 * Format chung của hai màn báo cáo. Chuỗi tiền đi qua [MoneyFormatter]; màu danh mục chưa chọn màu lấy theo vị trí
 * trong bảng màu, cố định theo id để không đổi màu giữa các lần mở.
 */
class ReportFormatter(private val money: MoneyFormatter) {

    fun full(value: Money): String = money.format(value).amount

    fun compact(value: Money): String = money.formatCompact(value)

    fun periodLabel(period: ReportPeriod): String = when (period) {
        is ReportPeriod.Month -> "Tháng ${period.month.month.ordinal + 1}/${period.month.year}"
        is ReportPeriod.Year -> "Năm ${period.year}"
    }

    /** "tháng 9", "tháng 12/2025" (khác năm với [relativeTo]), "năm 2025". */
    fun shortPeriodLabel(period: ReportPeriod, relativeTo: ReportPeriod): String = when (period) {
        is ReportPeriod.Month -> {
            val sameYear = relativeTo is ReportPeriod.Month && relativeTo.month.year == period.month.year
            val number = period.month.month.ordinal + 1
            if (sameYear) "tháng $number" else "tháng $number/${period.month.year}"
        }
        is ReportPeriod.Year -> "năm ${period.year}"
    }

    fun percentLabel(percent: Double): String {
        val tenths = (percent * 10).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10}%" else "${tenths / 10},${abs(tenths % 10)}%"
    }

    fun rowOf(share: CategoryShare, kind: CategoryKind, action: ShareAction, subtitle: String? = null): ShareRowUi {
        val category = share.category
        return ShareRowUi(
            key = category?.id ?: UNCATEGORIZED_KEY,
            categoryId = category?.id,
            name = category?.name ?: "Chưa phân loại",
            amount = full(share.total),
            currency = share.total.currency,
            percentLabel = percentLabel(share.percent),
            fraction = (share.percent / 100.0).toFloat(),
            colorHex = if (category == null) UNCATEGORIZED_HEX else colorOf(category.id, category.color),
            iconKey = category?.icon,
            kind = kind,
            style = if (category == null) ShareStyle.Uncategorized else ShareStyle.Category,
            action = if (category == null) ShareAction.None else action,
            subtitle = subtitle,
        )
    }

    fun sliceOf(row: ShareRowUi, weight: Double) = SliceUi(row.key, weight, row.colorHex, row.style)

    /** Màu user chọn nếu hợp lệ; không thì một màu trong bảng, cố định theo id. */
    private fun colorOf(id: String, hex: String?): String =
        hex?.takeIf { CategoryPalette.color(it) != null }
            ?: CategoryPalette.hexes[(id.hashCode() and Int.MAX_VALUE) % CategoryPalette.hexes.size]

    companion object {
        const val UNCATEGORIZED_KEY = "uncategorized"
        const val OTHERS_KEY = "others"

        /** Xám đá trong bảng màu danh mục: đậm hơn "Khác" để hai nhóm không lẫn nhau. */
        const val UNCATEGORIZED_HEX = "#64748B"
    }
}
