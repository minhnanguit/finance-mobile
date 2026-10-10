package com.uit.finance.feature.home.presentation.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.icon.categoryIcon
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.TransactionType

/* Ánh xạ model ledger → icon/màu/nhãn của design system, dùng chung cho các màn trong feature home. */

internal fun AccountType.label(): String = when (this) {
    AccountType.CASH -> "Tiền mặt"
    AccountType.BANK -> "Ngân hàng"
    AccountType.EWALLET -> "Ví điện tử"
    AccountType.OTHER -> "Khác"
}

internal fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CASH -> FinanceIcons.Cash
    AccountType.BANK -> FinanceIcons.Bank
    AccountType.EWALLET -> FinanceIcons.EWallet
    AccountType.OTHER -> FinanceIcons.Wallet
}

internal fun TransactionType.fallbackIcon(): ImageVector = when (this) {
    TransactionType.INCOME -> FinanceIcons.Income
    TransactionType.EXPENSE -> FinanceIcons.Expense
    TransactionType.TRANSFER -> FinanceIcons.Transfer
}

/** Icon của một dòng giao dịch: theo danh mục nếu có khoá hợp lệ, không thì theo chiều tiền. */
internal fun TransactionRowUi.icon(): ImageVector = categoryIcon(iconKey) ?: type.fallbackIcon()

/** Nền ô icon theo chiều tiền (xanh sage = thu, cam đào = chi, xám = chuyển). */
@Composable
internal fun TransactionType.tileContainer(): Color {
    val colors = FinanceTheme.colors
    return when (this) {
        TransactionType.INCOME -> colors.incomeContainer
        TransactionType.EXPENSE -> colors.expenseContainer
        TransactionType.TRANSFER -> colors.transferContainer
    }
}

@Composable
internal fun TransactionType.tileContent(): Color {
    val colors = FinanceTheme.colors
    return when (this) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.secondary
        TransactionType.TRANSFER -> colors.transfer
    }
}
