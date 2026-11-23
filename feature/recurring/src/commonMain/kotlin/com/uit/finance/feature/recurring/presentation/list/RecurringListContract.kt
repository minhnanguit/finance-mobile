package com.uit.finance.feature.recurring.presentation.list

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview

data class RecurringListState(
    val isLoading: Boolean = true,
    val attention: List<AttentionUi> = emptyList(),
    val toConfirm: List<OccurrenceUi> = emptyList(),
    val toReview: List<OccurrenceUi> = emptyList(),
    /** Nhóm theo ngày, tăng dần. */
    val upcoming: List<UpcomingDayUi> = emptyList(),
    /** Mỗi tiền tệ một dòng: "Chi VND 3.059.000 · Thu VND 15.000.000". */
    val upcomingTotals: List<String> = emptyList(),
    val recurrings: List<RecurringItemUi> = emptyList(),
    val sync: SyncOverview = SyncOverview.IDLE,
    val isRefreshing: Boolean = false,
    /** Giao dịch đang xử lý (xác nhận / bỏ qua): khoá nút của dòng đó. */
    val busyIds: Set<String> = emptySet(),
    val skipConfirm: OccurrenceUi? = null,
    val message: UiText? = null,
) {
    val isEmpty: Boolean get() = recurrings.isEmpty() && toConfirm.isEmpty() && toReview.isEmpty()
}

data class MoneyUi(val currency: String, val amount: String, val isIncome: Boolean)

data class OccurrenceUi(
    val transactionId: String,
    val name: String,
    val dateLabel: String,
    val amount: MoneyUi,
)

data class UpcomingDayUi(val dateLabel: String, val items: List<UpcomingUi>)

data class UpcomingUi(val key: String, val recurringId: String, val name: String, val amount: MoneyUi)

data class AttentionUi(
    val recurringId: String,
    val name: String,
    val reason: String,
    /** Đã bị chặn thật (R13): cho chọn ghi bù hay bỏ qua. Chỉ thiếu tham chiếu thì chỉ cho mở form sửa. */
    val canResolve: Boolean,
)

data class RecurringItemUi(
    val id: String,
    val name: String,
    val schedule: String,
    val nextLabel: String,
    val amount: MoneyUi,
    val isEnded: Boolean,
    val needsAttention: Boolean,
)

sealed interface RecurringListIntent {
    data object AddClicked : RecurringListIntent
    data class RecurringClicked(val recurringId: String) : RecurringListIntent
    data class OccurrenceClicked(val transactionId: String) : RecurringListIntent
    data class ConfirmClicked(val transactionId: String) : RecurringListIntent
    data class SkipClicked(val transactionId: String) : RecurringListIntent
    data object SkipConfirmed : RecurringListIntent
    data object SkipDismissed : RecurringListIntent
    data class BackfillClicked(val recurringId: String) : RecurringListIntent
    data class SkipBlockedClicked(val recurringId: String) : RecurringListIntent
    data object SyncNow : RecurringListIntent
    data object AcknowledgeSyncIssues : RecurringListIntent
    data object MessageShown : RecurringListIntent
    data object BackClicked : RecurringListIntent
}

sealed interface RecurringListEffect {
    data class OpenEditor(val recurringId: String?) : RecurringListEffect
    data class OpenTransaction(val transactionId: String) : RecurringListEffect
    data object Close : RecurringListEffect
}
