package com.uit.finance.core.ui.ledger

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText

/**
 * Mã lỗi `ledger.*` (máy tự kiểm hoặc server trả về, ADR-005 §3) → câu người dùng đọc được. Chỗ duy nhất
 * map mã lỗi ledger; màn hình không bao giờ hiện nguyên mã.
 */
fun AppError.toLedgerUiText(): UiText = when (this) {
    is AppError.Validation -> fieldErrors.firstOrNull()?.toLedgerUiText() ?: toUiText()
    is AppError.Api -> code?.let { FieldError(field = "", message = it).toLedgerUiText() } ?: toUiText()
    else -> toUiText()
}

/** Lỗi của đúng một trường (hiện dưới ô nhập). */
fun FieldError.toLedgerUiText(): UiText = when (message) {
    LedgerErrorCode.INVALID_FIELD -> invalidField(field)
    LedgerErrorCode.CATEGORY_KIND_MISMATCH -> key("category_kind_mismatch", "Danh mục không khớp loại giao dịch.")
    LedgerErrorCode.INVALID_TRANSFER -> key("invalid_transfer", "Chuyển tiền cần hai ví khác nhau và cùng tiền tệ.")
    LedgerErrorCode.CURRENCY_LOCKED -> key("currency_locked", "Ví đã có giao dịch nên không đổi được tiền tệ.")
    LedgerErrorCode.IN_USE -> key("in_use", "Đã có giao dịch dùng tới nên không xoá được. Hãy lưu trữ thay vì xoá.")
    LedgerErrorCode.KIND_IMMUTABLE -> key("kind_immutable", "Không đổi được loại thu/chi của danh mục.")
    LedgerErrorCode.INVALID_PARENT -> key("invalid_parent", "Danh mục cha không hợp lệ.")
    LedgerErrorCode.LIMIT_EXCEEDED -> when (field) {
        "category" -> key("limit.category", "Đã đủ 300 danh mục, không tạo thêm được.")
        else -> key("limit.account", "Đã đủ 50 ví, không tạo thêm được.")
    }
    LedgerErrorCode.NOT_FOUND -> key("not_found", "Không còn dữ liệu này (có thể đã bị xoá trên máy khác).")
    else -> if (message.startsWith("ledger.")) key("unknown", "Dữ liệu chưa hợp lệ.") else UiText.Dynamic(message)
}

/** Lỗi đầu tiên của [field] trong danh sách, để gắn dưới đúng ô nhập. */
fun List<FieldError>.messageFor(vararg field: String): UiText? = firstOrNull { it.field in field }?.toLedgerUiText()

private fun invalidField(field: String): UiText = when (field) {
    "amountMinor" -> key("field.amount", "Số tiền phải lớn hơn 0 và không quá 1 triệu tỷ.")
    "openingBalanceMinor" -> key("field.opening_balance", "Số dư ban đầu vượt giới hạn cho phép.")
    "name" -> key("field.name", "Tên cần từ 1 đến 50 ký tự.")
    "currency" -> key("field.currency", "Mã tiền tệ không hợp lệ.")
    "payee" -> key("field.payee", "Người nhận tối đa 100 ký tự.")
    "note" -> key("field.note", "Ghi chú tối đa 500 ký tự.")
    "occurredOn" -> key("field.date", "Ngày phải từ 01/01/2000 tới một năm sau hôm nay.")
    "categoryId" -> key("field.category", "Hãy chọn danh mục.")
    "counterAccountId" -> key("field.counter_account", "Hãy chọn ví nhận.")
    "icon" -> key("field.icon", "Icon không hợp lệ.")
    "color" -> key("field.color", "Màu không hợp lệ.")
    else -> key("field.invalid", "Thông tin chưa hợp lệ.")
}

private fun key(suffix: String, fallback: String) = UiText.Key("ledger.$suffix", fallback)
