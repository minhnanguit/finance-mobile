package com.uit.finance.feature.home.presentation.home

import com.uit.finance.feature.home.presentation.navigation.HomeTarget

/**
 * `overview == null` nghĩa là chưa có nguồn số liệu: màn hình hiện trạng thái trống.
 * Khi `core/ledger` xong (LEDGER-PLAN Phase 4), ViewModel observe số dư/thu chi rồi điền vào đây;
 * Screen không phải sửa.
 */
data class HomeState(
    val overview: HomeOverview? = null,
)

/** Số liệu đã format sẵn để hiển thị. Format tiền là việc của ViewModel, không phải Screen. */
data class HomeOverview(
    val totalBalance: String,
    val monthIncome: String,
    val monthExpense: String,
)

sealed interface HomeIntent {
    data class Open(val target: HomeTarget) : HomeIntent
}

sealed interface HomeEffect {
    data class Navigate(val target: HomeTarget) : HomeEffect
}
