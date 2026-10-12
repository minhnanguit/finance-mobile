package com.uit.finance.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.uit.finance.core.designsystem.theme.MoneyDirection
import com.uit.finance.core.designsystem.theme.amountColor
import com.uit.finance.core.designsystem.theme.tileContainer
import com.uit.finance.core.designsystem.theme.tileContent

/**
 * Một dòng tiền (giao dịch) theo bản thiết kế: ô icon tô theo chiều tiền, tiêu đề + mô tả, số tiền bên phải.
 * Trang chủ và lịch sử dùng chung để mọi danh sách giao dịch trông giống hệt nhau.
 */
@Composable
fun MoneyListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    direction: MoneyDirection,
    amount: String,
    currency: String,
    sign: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    FinanceListItem(
        title = title,
        subtitle = subtitle,
        leading = {
            IconTile(icon = icon, containerColor = direction.tileContainer, contentColor = direction.tileContent)
        },
        trailing = {
            AmountText(amount = amount, currency = currency, sign = sign, amountColor = direction.amountColor)
        },
        onClick = onClick,
        modifier = modifier,
    )
}
