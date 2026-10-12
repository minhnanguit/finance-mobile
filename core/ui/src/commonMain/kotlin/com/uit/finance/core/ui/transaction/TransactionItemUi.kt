package com.uit.finance.core.ui.transaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uit.finance.core.designsystem.component.MoneyListItem
import com.uit.finance.core.designsystem.icon.categoryIcon
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ui.ledger.direction
import com.uit.finance.core.ui.ledger.fallbackIcon

/** Một dòng giao dịch đã format sẵn (trang chủ, lịch sử). Tạo bằng [TransactionItemFormatter]. */
data class TransactionItemUi(
    val id: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val currency: String,
    /** Đã format, không dấu: `1.250.000`. */
    val amount: String,
    /** `+` thu, `-` chi, rỗng khi chuyển giữa các ví. */
    val sign: String,
    /** Khoá icon của danh mục; `null` (chuyển tiền, danh mục chưa sync về) thì dùng icon theo loại. */
    val iconKey: String?,
)

/** Dòng giao dịch dùng chung: giống hệt nhau ở mọi màn có danh sách giao dịch. */
@Composable
fun TransactionListItem(
    item: TransactionItemUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    MoneyListItem(
        title = item.title,
        subtitle = item.subtitle,
        icon = categoryIcon(item.iconKey) ?: item.type.fallbackIcon(),
        direction = item.type.direction(),
        amount = item.amount,
        currency = item.currency,
        sign = item.sign,
        onClick = onClick,
        modifier = modifier,
    )
}
