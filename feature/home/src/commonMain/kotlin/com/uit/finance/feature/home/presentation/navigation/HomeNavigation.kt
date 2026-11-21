package com.uit.finance.feature.home.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.home.presentation.home.HomeRoute
import kotlinx.serialization.Serializable

/** Destination type-safe của feature home. `composeApp` gắn vào NavHost. */
@Serializable data object HomeDestination

/**
 * Những nơi người dùng muốn đi từ Home. Màn đích thuộc feature khác (transactions, accounts…),
 * nên home chỉ nói *ý định*; `composeApp` map sang destination thật.
 */
sealed interface HomeTarget {
    data object AddTransaction : HomeTarget
    data class EditTransaction(val transactionId: String) : HomeTarget
    data object Transactions : HomeTarget
    data object Accounts : HomeTarget
    data object CreateAccount : HomeTarget
    data object Budgets : HomeTarget
    data object Reports : HomeTarget
    /** Khoản định kỳ (nút ở hàng thao tác nhanh). */
    data object Recurring : HomeTarget

    /** Chuông: màn Chờ xác nhận (mọi giao dịch nháp + tin ngân hàng cần xử lý). */
    data object PendingReview : HomeTarget
}

/** Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.homeNavGraph(onNavigate: (HomeTarget) -> Unit) {
    composable<HomeDestination> { HomeRoute(onNavigate = onNavigate) }
}
