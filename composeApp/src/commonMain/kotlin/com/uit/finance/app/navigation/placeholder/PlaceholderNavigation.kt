package com.uit.finance.app.navigation.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import kotlinx.serialization.Serializable

/*
 * Chỗ giữ tạm cho các feature chưa có module (ARCHITECTURE §6.1, LEDGER-PLAN Phase 5).
 * Nằm ở composeApp vì destination thật sẽ thuộc feature/transactions, accounts, budgets, reports;
 * không feature nào được giữ destination của feature khác. Khi feature thật ra đời: xoá destination
 * tương ứng ở đây và nối `<feature>NavGraph` vào FinanceNavHost.
 */
@Serializable internal data object TransactionsDestination
@Serializable internal data object BudgetsDestination
@Serializable internal data object AccountsDestination
@Serializable internal data object ReportsDestination
@Serializable internal data object BillsDestination
@Serializable internal data object AddTransactionDestination

/** `onBack` chỉ áp dụng cho màn con; màn là tab (Giao dịch, Ngân sách) không có nút quay lại. */
internal fun NavGraphBuilder.placeholderNavGraph(onBack: () -> Unit) {
    composable<TransactionsDestination> {
        PlaceholderScreen("Giao dịch", "Lịch sử, tìm kiếm và duyệt giao dịch sẽ nằm ở đây.")
    }
    composable<BudgetsDestination> {
        PlaceholderScreen("Ngân sách", "Theo dõi hạn mức tháng, số đã chi và phần còn lại.")
    }
    composable<AccountsDestination> {
        PlaceholderScreen("Ví của bạn", "Quản lý tiền mặt, tài khoản ngân hàng và chuyển tiền giữa các ví.", onBack)
    }
    composable<ReportsDestination> {
        PlaceholderScreen("Báo cáo", "Xem thu chi theo thời gian, danh mục và so sánh với kỳ trước.", onBack)
    }
    composable<BillsDestination> {
        PlaceholderScreen("Khoản định kỳ", "Theo dõi hóa đơn, gói đăng ký và các khoản sắp đến hạn.", onBack)
    }
    composable<AddTransactionDestination> {
        PlaceholderScreen("Thêm giao dịch", "Bạn sẽ có thể nhập khoản thu hoặc chi tại đây.", onBack)
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String, onBack: (() -> Unit)? = null) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Icon(FinanceIcons.ChevronLeft, contentDescription = null)
                Text("Quay lại")
            }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Tính năng đang hoàn thiện", style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
