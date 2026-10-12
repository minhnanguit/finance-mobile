package com.uit.finance.feature.transactions.presentation.history

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.core.ui.transaction.TransactionItemUi

data class HistoryState(
    val isLoading: Boolean = true,
    val sections: List<DaySection> = emptyList(),
    /** Đã hiện đủ số dòng của cửa sổ hiện tại: cuộn tới cuối thì nạp thêm. */
    val canLoadMore: Boolean = false,
    val sync: SyncOverview = SyncOverview.IDLE,
    val isRefreshing: Boolean = false,
    /** Giao dịch vừa vuốt xoá, đang chờ hết thời gian hoàn tác (đã ẩn khỏi danh sách). */
    val pendingDeleteId: String? = null,
    val message: UiText? = null,
)

/** Các giao dịch cùng một ngày, nhãn "Hôm nay" / "Hôm qua" / "9 thg 10". */
data class DaySection(val key: String, val label: String, val items: List<TransactionItemUi>)

sealed interface HistoryIntent {
    data object LoadMore : HistoryIntent
    data object AddClicked : HistoryIntent
    data class ItemClicked(val transactionId: String) : HistoryIntent
    data object CategoriesClicked : HistoryIntent
    data class DeleteSwiped(val transactionId: String) : HistoryIntent
    data object UndoDelete : HistoryIntent

    /** Hết thời gian hoàn tác hoặc rời màn hình: xoá thật. */
    data object CommitPendingDelete : HistoryIntent
    data object SyncNow : HistoryIntent
    data object AcknowledgeSyncIssues : HistoryIntent
    data object MessageShown : HistoryIntent

    /** Màn hình resume: nhãn "Hôm nay"/"Hôm qua" theo ngày mới. */
    data object Refresh : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenEditor(val transactionId: String?) : HistoryEffect
    data object OpenCategories : HistoryEffect
}
