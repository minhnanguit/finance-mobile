package com.uit.finance.feature.transactions.domain.model

import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionType
import kotlinx.datetime.LocalDate

/** Giao dịch kèm ví, ví nhận và danh mục đã tra sẵn. `null` khi bản ghi đó chưa sync về hoặc đã bị xoá. */
data class TransactionDetails(
    val transaction: Transaction,
    val account: Account?,
    val counterAccount: Account?,
    val category: Category?,
)

/** Lựa chọn cho form giao dịch: mọi ví và danh mục chưa xoá (form tự lọc bản đã lưu trữ khi chọn mới). */
data class TransactionFormData(
    val accounts: List<Account>,
    val categories: List<Category>,
)

/**
 * Dữ liệu form thêm/sửa giao dịch. `id == null` là thêm mới. [feeMinor] > 0 chỉ có nghĩa với chuyển tiền mới:
 * sinh thêm một khoản chi "Phí giao dịch" từ ví đi (D3).
 */
data class TransactionInput(
    val id: String?,
    val type: TransactionType,
    val accountId: String,
    val counterAccountId: String?,
    val categoryId: String?,
    val amountMinor: Long,
    val feeMinor: Long,
    val occurredOn: LocalDate,
    val payee: String?,
    val note: String?,
)

/** Dữ liệu form tạo/sửa danh mục. `id == null` là tạo mới; loại thu/chi không đổi được sau khi tạo. */
data class CategoryInput(
    val id: String?,
    val kind: CategoryKind,
    val name: String,
    val icon: String?,
    val color: String?,
)

/** Mã lỗi riêng của feature (ngoài `ledger.*`). */
object TransactionErrorCode {
    /** Chuyển tiền có phí nhưng chưa có danh mục "Phí giao dịch" (server tạo ở lần sync đầu, ADR-005 §6). */
    const val FEE_CATEGORY_MISSING = "transactions.fee_category_missing"
}

/** `template_key` của danh mục phí do server seed (ADR-005 §6). */
const val FEE_TEMPLATE_KEY = "fee"
