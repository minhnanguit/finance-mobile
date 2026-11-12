package com.uit.finance.feature.budgets.presentation.list

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.feature.budgets.domain.model.BudgetTone

data class BudgetListState(
    val isLoading: Boolean = true,
    /** "Tháng 10/2026". */
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    /** Không xem được tháng tương lai. */
    val canGoNext: Boolean = false,
    /** Mỗi tiền tệ một dòng. */
    val totals: List<BudgetTotalUi> = emptyList(),
    /** Gần vượt nhất lên đầu. */
    val items: List<BudgetItemUi> = emptyList(),
    val sync: SyncOverview = SyncOverview.IDLE,
    val isRefreshing: Boolean = false,
    val message: UiText? = null,
)

data class MoneyUi(val currency: String, val amount: String, val isNegative: Boolean)

data class BudgetTotalUi(val budgeted: MoneyUi, val spent: MoneyUi, val remaining: MoneyUi)

data class BudgetItemUi(
    val id: String,
    /** `null` khi danh mục chưa sync tới hoặc vừa bị xoá ở máy khác. */
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val categoryArchived: Boolean,
    val budgeted: MoneyUi,
    val spent: MoneyUi,
    /** Âm khi đã vượt. */
    val remaining: MoneyUi,
    /** "85%", tối đa "999%". */
    val percentLabel: String,
    /** 0..1 cho thanh tiến độ. */
    val progress: Float,
    val tone: BudgetTone,
    /** Mỗi ngày còn được chi; chỉ ở tháng hiện tại khi còn lại > 0. */
    val dailyAllowance: MoneyUi?,
)

sealed interface BudgetListIntent {
    data object PreviousMonth : BudgetListIntent
    data object NextMonth : BudgetListIntent
    data object AddClicked : BudgetListIntent
    data class ItemClicked(val budgetId: String) : BudgetListIntent
    data object SyncNow : BudgetListIntent
    data object AcknowledgeSyncIssues : BudgetListIntent
    data object MessageShown : BudgetListIntent

    /** App quay lại foreground: đọc lại "hôm nay" ngay (G12). */
    data object Resumed : BudgetListIntent
}

sealed interface BudgetListEffect {
    /** `budgetId == null` là tạo ngân sách mới. */
    data class OpenEditor(val budgetId: String?) : BudgetListEffect
}
