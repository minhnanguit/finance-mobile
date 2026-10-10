package com.uit.finance.feature.home.presentation.home

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.TransactionType
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
    val recentTransactions: List<TransactionRowUi> = emptyList(),
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

data class TransactionRowUi(
    val id: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val currency: String,
    val amount: String,
    /** `+` thu, `-` chi, rỗng khi chuyển tiền. */
    val sign: String,
    /** Khoá icon của danh mục (do server/user đặt); `null` thì dùng icon theo loại giao dịch. */
    val iconKey: String?,
)

sealed interface HomeIntent {
    data class Open(val target: HomeTarget) : HomeIntent

    /** Màn hình resume: tính lại "hôm nay" (qua đêm, qua tháng) để kỳ "tháng này" và nhãn ngày không bị cũ. */
    data object Refresh : HomeIntent
}

sealed interface HomeEffect {
    data class Navigate(val target: HomeTarget) : HomeEffect
}
