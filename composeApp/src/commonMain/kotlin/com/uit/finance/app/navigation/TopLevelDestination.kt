package com.uit.finance.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.uit.finance.app.navigation.placeholder.BudgetsDestination
import com.uit.finance.app.navigation.placeholder.TransactionsDestination
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.feature.auth.presentation.navigation.ProfileDestination
import com.uit.finance.feature.home.presentation.navigation.HomeDestination

/**
 * Các tab của bottom bar. Thêm/bớt tab = sửa enum này; thanh điều hướng và logic chọn tab tự theo.
 * Khi `feature/transactions`, `feature/budgets` ra đời, chỉ đổi `route` sang destination của feature đó.
 */
internal enum class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector) {
    Home(HomeDestination, "Trang chủ", FinanceIcons.Home),
    Transactions(TransactionsDestination, "Giao dịch", FinanceIcons.Transactions),
    Budgets(BudgetsDestination, "Ngân sách", FinanceIcons.Budget),
    Profile(ProfileDestination, "Cá nhân", FinanceIcons.Profile),
}

/** Tab đang hiển thị, hoặc `null` nếu đang ở màn con / màn chưa đăng nhập (khi đó ẩn bottom bar). */
internal fun NavDestination?.currentTopLevel(): TopLevelDestination? =
    this?.let { destination ->
        TopLevelDestination.entries.firstOrNull { tab -> destination.hierarchy.any { it.hasRoute(tab.route::class) } }
    }
