package com.uit.finance.feature.home.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.home.presentation.home.HomeRoute
import com.uit.finance.feature.home.presentation.home.HomeTarget
import com.uit.finance.feature.home.presentation.section.PlannedFeatureScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeDestination
@Serializable data object TransactionsDestination
@Serializable data object WalletsDestination
@Serializable data object BudgetsDestination
@Serializable data object ReportsDestination
@Serializable data object BillsDestination
@Serializable data object AddTransactionDestination

/** App owns navigation; this feature only exposes the screens and user actions. */
fun NavGraphBuilder.homeNavGraph(
    onNavigate: (HomeTarget) -> Unit,
    onBack: () -> Unit,
) {
    composable<HomeDestination> { HomeRoute(onNavigate = onNavigate) }
    composable<TransactionsDestination> {
        PlannedFeatureScreen(
            title = "Giao dịch",
            description = "Lịch sử, tìm kiếm và duyệt giao dịch sẽ nằm ở đây.",
            onBack = onBack,
        )
    }
    composable<WalletsDestination> {
        PlannedFeatureScreen(
            title = "Ví của bạn",
            description = "Quản lý tiền mặt, tài khoản ngân hàng và chuyển tiền giữa các ví.",
            onBack = onBack,
        )
    }
    composable<BudgetsDestination> {
        PlannedFeatureScreen(
            title = "Ngân sách",
            description = "Theo dõi hạn mức tháng, số đã chi và phần còn lại.",
            onBack = onBack,
        )
    }
    composable<ReportsDestination> {
        PlannedFeatureScreen(
            title = "Báo cáo",
            description = "Xem thu chi theo thời gian, danh mục và so sánh với kỳ trước.",
            onBack = onBack,
        )
    }
    composable<BillsDestination> {
        PlannedFeatureScreen(
            title = "Khoản định kỳ",
            description = "Theo dõi hóa đơn, gói đăng ký và các khoản sắp đến hạn.",
            onBack = onBack,
        )
    }
    composable<AddTransactionDestination> {
        PlannedFeatureScreen(
            title = "Thêm giao dịch",
            description = "Bạn sẽ có thể nhập khoản thu hoặc chi tại đây.",
            onBack = onBack,
        )
    }
}
