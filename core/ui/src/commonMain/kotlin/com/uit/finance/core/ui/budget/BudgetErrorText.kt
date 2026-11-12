package com.uit.finance.core.ui.budget

import com.uit.finance.core.budget.domain.model.BudgetErrorCode
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.ui.ledger.toLedgerUiText

/**
 * Mã lỗi `budget.*` (máy tự kiểm hoặc server trả qua sync, ADR-007 §3) → câu người dùng đọc được. Chỗ duy
 * nhất map mã lỗi ngân sách; mã khác (ledger.*, lỗi mạng, lưu trữ) chuyển cho `toLedgerUiText`.
 */
fun AppError.toBudgetUiText(): UiText = when (this) {
    is AppError.Validation -> fieldErrors.firstOrNull()?.toBudgetUiText() ?: toLedgerUiText()
    is AppError.Api -> code?.let { FieldError(field = "", message = it).toBudgetUiText() } ?: toLedgerUiText()
    else -> toLedgerUiText()
}

/** Lỗi của đúng một trường (hiện dưới ô nhập). */
fun FieldError.toBudgetUiText(): UiText = when (message) {
    BudgetErrorCode.INVALID_FIELD -> invalidField(field)
    BudgetErrorCode.DUPLICATE -> key("duplicate", "Danh mục này đã có ngân sách cho loại tiền này.")
    BudgetErrorCode.LIMIT_REACHED -> key("limit", "Đã đủ 100 ngân sách, không tạo thêm được.")
    BudgetErrorCode.NOT_FOUND -> key("not_found", "Không còn ngân sách này (có thể đã bị xoá trên máy khác).")
    BudgetErrorCode.INVALID_CATEGORY -> key("invalid_category", "Chỉ đặt ngân sách cho danh mục chi cấp 1 còn dùng.")
    BudgetErrorCode.IMMUTABLE_FIELD ->
        key("immutable_field", "Không đổi được danh mục, tiền tệ hay tháng bắt đầu. Hãy xoá rồi tạo mới.")
    BudgetErrorCode.REFERENCE_PENDING -> key("reference_pending", "Danh mục chưa đồng bộ xong, app sẽ tự thử lại.")
    BudgetErrorCode.DELETED -> key("deleted", "Ngân sách đã bị xoá trên máy khác.")
    else -> if (message.startsWith("budget.")) key("unknown", "Ngân sách chưa hợp lệ.") else toLedgerUiText()
}

/** Lỗi đầu tiên của [field] trong danh sách, để gắn dưới đúng ô nhập. */
fun List<FieldError>.budgetMessageFor(vararg field: String): UiText? = firstOrNull { it.field in field }?.toBudgetUiText()

private fun invalidField(field: String): UiText = when (field) {
    "amountMinor" -> key("field.amount", "Số tiền phải lớn hơn 0 và không quá 1 triệu tỷ.")
    "currency" -> key("field.currency", "Mã tiền tệ không hợp lệ.")
    "categoryId" -> key("field.category", "Hãy chọn danh mục.")
    "startMonth" -> key("field.start_month", "Tháng bắt đầu không hợp lệ.")
    else -> key("field.invalid", "Thông tin chưa hợp lệ.")
}

private fun key(suffix: String, fallback: String) = UiText.Key("budget.$suffix", fallback)
