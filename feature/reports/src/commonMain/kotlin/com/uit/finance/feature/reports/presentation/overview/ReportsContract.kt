package com.uit.finance.feature.reports.presentation.overview

import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.feature.reports.presentation.common.ShareRowUi
import com.uit.finance.feature.reports.presentation.common.SliceUi
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import com.uit.finance.feature.reports.presentation.navigation.CategoryBreakdownDestination

enum class ReportMode { Month, Year }

enum class ChangeDirection { Up, Down, Flat }

/** Dòng "▲ 12% so với cùng kỳ tháng 9". [isGood]: chi giảm hoặc thu tăng (tô màu thu), ngược lại tô màu chi. */
data class ComparisonUi(val text: String, val direction: ChangeDirection, val isGood: Boolean)

/** [actionLabel]: nhãn hành động "chọn cột này" cho trình đọc màn hình; `null` với cột đang xem và tháng tương lai. */
data class TrendBarUi(
    val label: String,
    val value: Double,
    val isSelected: Boolean,
    val isPartial: Boolean,
    val isFuture: Boolean,
    val actionLabel: String? = null,
)

/**
 * Màn Báo cáo (docs/REPORTS-PLAN.md mục 1). Một số chính ([total]) theo [kind]; trên màn chỉ có ‹ kỳ › và Chi/Thu,
 * Tháng/Năm và Tiền tệ nằm trong menu ⋯ (V1, V2).
 */
data class ReportsState(
    val isLoading: Boolean = true,
    val mode: ReportMode = ReportMode.Month,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val periodLabel: String = "",
    val canGoNext: Boolean = false,
    val currency: String = "",
    val currencyOptions: List<String> = emptyList(),
    val total: String = "",
    val totalCompact: String = "",
    val comparison: ComparisonUi? = null,
    /** "Thu 25tr" khi đang xem chi, "Chi 18,4tr" khi đang xem thu. */
    val counterpartLabel: String = "",
    val remainingLabel: String = "",
    val remainingNegative: Boolean = false,
    val slices: List<SliceUi> = emptyList(),
    val rows: List<ShareRowUi> = emptyList(),
    /** Các nhóm gộp trong "Khác", hiện khi [showOthers]. */
    val otherRows: List<ShareRowUi> = emptyList(),
    val showOthers: Boolean = false,
    val donutDescription: String = "",
    val trendTitle: String = "",
    val trend: List<TrendBarUi> = emptyList(),
    val trendDescription: String = "",
    /** Kỳ không có giao dịch thu/chi nào: hiện EmptyState thay cho biểu đồ. */
    val isEmpty: Boolean = false,
    /** Không có chi (hoặc thu) nào trong kỳ, dù có loại kia: hiện thông báo ngắn thay cho donut. */
    val isKindEmpty: Boolean = false,
    val showCurrencySheet: Boolean = false,
    /** Đổi khi đổi kỳ / loại / tiền tệ: biểu đồ chạy animation lại; sync về thì giữ nguyên (V7). */
    val animationKey: String = "",
) {
    val showCurrencyOption: Boolean get() = currencyOptions.size > 1
}

sealed interface ReportsIntent {
    data object Previous : ReportsIntent
    data object Next : ReportsIntent
    data class SelectKind(val kind: CategoryKind) : ReportsIntent
    data class SelectMode(val mode: ReportMode) : ReportsIntent
    data object OpenCurrencyPicker : ReportsIntent
    data object DismissCurrencyPicker : ReportsIntent
    data class SelectCurrency(val currency: String) : ReportsIntent
    data class SelectTrendBar(val index: Int) : ReportsIntent
    data class OpenRow(val row: ShareRowUi) : ReportsIntent
    data object AddTransaction : ReportsIntent
    data object Back : ReportsIntent

    /** Màn quay lại foreground: đọc lại "hôm nay" ngay (đồng hồ máy có thể đã bị chỉnh). */
    data object Resumed : ReportsIntent
}

sealed interface ReportsEffect {
    data class OpenBreakdown(val destination: CategoryBreakdownDestination) : ReportsEffect
    data class OpenTransactions(val query: ReportTransactionsQuery) : ReportsEffect
    data object AddTransaction : ReportsEffect
    data object NavigateBack : ReportsEffect
}
