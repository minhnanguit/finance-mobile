package com.uit.finance.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Chiều của dòng tiền trên màn hình: vào (thu), ra (chi), trung tính (chuyển giữa các ví). Design system không
 * biết model ledger; nơi gọi tự đổi loại giao dịch sang chiều tiền, còn màu của mỗi chiều chỉ khai báo ở đây.
 */
enum class MoneyDirection { In, Out, Neutral }

/** Nền ô icon đầu dòng: xanh sage (vào), cam đào (ra), xám (trung tính). */
val MoneyDirection.tileContainer: Color
    @Composable get() = when (this) {
        MoneyDirection.In -> FinanceTheme.colors.incomeContainer
        MoneyDirection.Out -> FinanceTheme.colors.expenseContainer
        MoneyDirection.Neutral -> FinanceTheme.colors.transferContainer
    }

val MoneyDirection.tileContent: Color
    @Composable get() = when (this) {
        MoneyDirection.In -> FinanceTheme.colors.income
        MoneyDirection.Out -> MaterialTheme.colorScheme.secondary
        MoneyDirection.Neutral -> FinanceTheme.colors.transfer
    }

/** Chỉ tiền vào được tô xanh; tiền ra giữ màu chữ thường để danh sách không đỏ rực. */
val MoneyDirection.amountColor: Color
    @Composable get() = when (this) {
        MoneyDirection.In -> FinanceTheme.colors.income
        MoneyDirection.Out, MoneyDirection.Neutral -> MaterialTheme.colorScheme.onSurface
    }
