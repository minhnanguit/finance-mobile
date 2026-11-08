package com.uit.finance.feature.reports.presentation.breakdown

import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.feature.reports.presentation.common.ShareRowUi
import com.uit.finance.feature.reports.presentation.common.SliceUi
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery

/** Tham số mở màn (từ route). [period] `null` = route hỏng: màn hiện trống thay vì crash. */
data class CategoryBreakdownArgs(
    val categoryId: String,
    val period: ReportPeriod?,
    val kind: CategoryKind,
    val currency: String,
)

/** Một danh mục cha tách ra từng danh mục con: cùng bố cục với màn tổng quan, không có so sánh và xu hướng. */
data class CategoryBreakdownState(
    val isLoading: Boolean = true,
    val title: String = "",
    val periodLabel: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val total: String = "",
    val totalCompact: String = "",
    val currency: String = "",
    val slices: List<SliceUi> = emptyList(),
    val rows: List<ShareRowUi> = emptyList(),
    val donutDescription: String = "",
    val isEmpty: Boolean = false,
    val animationKey: String = "",
)

sealed interface CategoryBreakdownIntent {
    data class OpenRow(val row: ShareRowUi) : CategoryBreakdownIntent
    data object OpenAll : CategoryBreakdownIntent
    data object Back : CategoryBreakdownIntent
}

sealed interface CategoryBreakdownEffect {
    data class OpenTransactions(val query: ReportTransactionsQuery) : CategoryBreakdownEffect
    data object NavigateBack : CategoryBreakdownEffect
}
