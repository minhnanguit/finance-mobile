package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.core.ui.transaction.TransactionItemUi
import com.uit.finance.feature.home.presentation.navigation.HomeTarget

/**
 * Số liệu đã format sẵn để hiển thị (format tiền/ngày là việc của [HomeUiMapper], không phải Screen).
 * `isLoading` chỉ đúng cho tới lần phát đầu tiên từ `core/ledger`; sau đó danh sách rỗng = trạng thái trống.
 */
data class HomeState(
    val isLoading: Boolean = true,
    /** Tên hiển thị của người dùng; `null` khi chưa có nguồn (hiện "Xin chào!"). */
    val userName: String? = null,
    val todayLabel: String = "",
    val accounts: List<AccountCardUi> = emptyList(),
    /** Thu/chi tháng này theo từng tiền tệ; màn hình chọn theo thẻ ví đang xem. */
    val monthByCurrency: Map<String, MonthSummaryUi> = emptyMap(),
    val recentTransactions: List<TransactionItemUi> = emptyList(),
    val sync: SyncOverview = SyncOverview.IDLE,
    /** User đang kéo để làm mới và vòng sync chưa xong. */
    val isRefreshing: Boolean = false,
    /** Thông báo một lần (snackbar); màn hình báo [HomeIntent.MessageShown] khi đã hiện. */
    val message: UiText? = null,
)

data class AccountCardUi(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    /** Số dư đã format, không dấu: `1.250.000`. */
    val balance: String,
    val isNegative: Boolean,
)

data class MonthSummaryUi(
    val currency: String,
    val income: String,
    val expense: String,
)

sealed interface HomeIntent {
    data class Open(val target: HomeTarget) : HomeIntent

    /** Màn hình resume: tính lại "hôm nay" (qua đêm, qua tháng) để kỳ "tháng này" và nhãn ngày không bị cũ. */
    data object Refresh : HomeIntent

    /** Kéo xuống hoặc chạm badge: gửi ngay thay đổi chưa đồng bộ và kéo dữ liệu mới. */
    data object SyncNow : HomeIntent
    data object AcknowledgeSyncIssues : HomeIntent
    data object MessageShown : HomeIntent
}

sealed interface HomeEffect {
    data class Navigate(val target: HomeTarget) : HomeEffect
}
