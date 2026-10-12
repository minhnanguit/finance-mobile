package com.uit.finance.core.ui.ledger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.icon.categoryIcon
import com.uit.finance.core.designsystem.theme.CategoryPalette
import com.uit.finance.core.designsystem.theme.MoneyDirection
import com.uit.finance.core.designsystem.theme.tileContainer
import com.uit.finance.core.designsystem.theme.tileContent
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType

/* Một chỗ đổi model ledger ra nhãn, icon, chiều tiền. Màn hình nào cũng gọi các hàm này, không tự `when`. */

fun AccountType.label(): String = when (this) {
    AccountType.CASH -> "Tiền mặt"
    AccountType.BANK -> "Ngân hàng"
    AccountType.EWALLET -> "Ví điện tử"
    AccountType.OTHER -> "Khác"
}

fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CASH -> FinanceIcons.Cash
    AccountType.BANK -> FinanceIcons.Bank
    AccountType.EWALLET -> FinanceIcons.EWallet
    AccountType.OTHER -> FinanceIcons.Wallet
}

/** Tên đầy đủ, dùng làm tiêu đề khi giao dịch không có người nhận / danh mục. */
fun TransactionType.label(): String = when (this) {
    TransactionType.INCOME -> "Thu nhập"
    TransactionType.EXPENSE -> "Chi tiêu"
    TransactionType.TRANSFER -> "Chuyển tiền"
}

/** Nhãn ngắn cho nút chọn loại (Chi / Thu / Chuyển). */
fun TransactionType.shortLabel(): String = when (this) {
    TransactionType.INCOME -> "Thu"
    TransactionType.EXPENSE -> "Chi"
    TransactionType.TRANSFER -> "Chuyển"
}

fun TransactionType.direction(): MoneyDirection = when (this) {
    TransactionType.INCOME -> MoneyDirection.In
    TransactionType.EXPENSE -> MoneyDirection.Out
    TransactionType.TRANSFER -> MoneyDirection.Neutral
}

/** `+` thu, `-` chi, không dấu khi chuyển giữa các ví của chính mình. */
fun TransactionType.sign(): String = when (this) {
    TransactionType.INCOME -> "+"
    TransactionType.EXPENSE -> "-"
    TransactionType.TRANSFER -> ""
}

fun TransactionType.fallbackIcon(): ImageVector = when (this) {
    TransactionType.INCOME -> FinanceIcons.Income
    TransactionType.EXPENSE -> FinanceIcons.Expense
    TransactionType.TRANSFER -> FinanceIcons.Transfer
}

fun CategoryKind.label(): String = when (this) {
    CategoryKind.INCOME -> "Thu"
    CategoryKind.EXPENSE -> "Chi"
}

fun CategoryKind.direction(): MoneyDirection = when (this) {
    CategoryKind.INCOME -> MoneyDirection.In
    CategoryKind.EXPENSE -> MoneyDirection.Out
}

/** Icon của danh mục theo khoá đã lưu; khoá lạ (danh mục từ bản app mới hơn) thì theo loại thu/chi. */
fun categoryIconOrDefault(iconKey: String?, kind: CategoryKind): ImageVector =
    categoryIcon(iconKey) ?: if (kind == CategoryKind.INCOME) FinanceIcons.Income else FinanceIcons.Expense

/** Nền + màu icon cho ô icon. */
@Immutable
data class TileColors(val container: Color, val content: Color)

/** Màu danh mục user chọn nếu hợp lệ, không thì màu theo chiều tiền của loại danh mục. */
@Composable
fun categoryTileColors(colorHex: String?, kind: CategoryKind): TileColors {
    val custom = CategoryPalette.color(colorHex)
    return if (custom != null) {
        TileColors(container = CategoryPalette.container(custom), content = custom)
    } else {
        val direction = kind.direction()
        TileColors(container = direction.tileContainer, content = direction.tileContent)
    }
}
