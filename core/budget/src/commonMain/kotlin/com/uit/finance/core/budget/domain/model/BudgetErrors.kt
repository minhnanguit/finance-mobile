package com.uit.finance.core.budget.domain.model

/**
 * Mã lỗi dùng chung với backend (ADR-007 §3), đặt trong `FieldError.message` của `AppError.Validation`.
 * UI map một lần cho cả lỗi kiểm ở máy lẫn lỗi server trả về qua sync.
 */
object BudgetErrorCode {
    const val INVALID_FIELD = "budget.invalid_field"
    const val DUPLICATE = "budget.duplicate"
    const val LIMIT_REACHED = "budget.limit_reached"
    const val NOT_FOUND = "budget.not_found"

    // Chỉ server trả (qua sync): máy không tự sinh, nhưng UI vẫn phải hiển thị được.
    const val INVALID_CATEGORY = "budget.invalid_category"
    const val IMMUTABLE_FIELD = "budget.immutable_field"
    const val REFERENCE_PENDING = "budget.reference_pending"
    const val DELETED = "budget.deleted"
}
