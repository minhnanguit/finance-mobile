package com.uit.finance.core.ui.transaction

import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.text.toRelativeLabel
import com.uit.finance.core.ui.ledger.label
import com.uit.finance.core.ui.ledger.sign
import kotlinx.datetime.LocalDate

/**
 * Giao dịch (+ ví, ví nhận, danh mục đã tra sẵn) → [TransactionItemUi]. Thuần Kotlin nên test được không cần
 * Compose; ViewModel gọi, màn hình chỉ vẽ.
 *
 * Tiêu đề: người nhận → tên danh mục → loại giao dịch. Mô tả: danh mục (nếu chưa làm tiêu đề) · ví (chuyển tiền:
 * "ví đi → ví đến") · ngày (bỏ khi [today] `null`, ví dụ danh sách đã gom theo ngày).
 */
class TransactionItemFormatter(private val money: MoneyFormatter) {

    fun format(
        transaction: Transaction,
        account: Account?,
        counterAccount: Account?,
        category: Category?,
        today: LocalDate?,
    ): TransactionItemUi {
        val categoryName = category?.name
        val title = transaction.payee?.takeIf { it.isNotBlank() } ?: categoryName ?: transaction.type.label()
        val accounts = if (transaction.type == TransactionType.TRANSFER) {
            listOfNotNull(account?.name, counterAccount?.name).joinToString(TRANSFER_ARROW).ifEmpty { null }
        } else {
            account?.name
        }
        val subtitle = listOfNotNull(
            categoryName?.takeIf { it != title },
            accounts,
            today?.let { transaction.occurredOn.toRelativeLabel(it) },
        ).joinToString(SEPARATOR)
        val formatted = money.format(transaction.amount)
        return TransactionItemUi(
            id = transaction.id,
            type = transaction.type,
            title = title,
            subtitle = subtitle,
            currency = formatted.currency,
            amount = formatted.amount,
            sign = transaction.type.sign(),
            iconKey = category?.icon,
        )
    }

    private companion object {
        const val SEPARATOR = " · "
        const val TRANSFER_ARROW = " → "
    }
}
