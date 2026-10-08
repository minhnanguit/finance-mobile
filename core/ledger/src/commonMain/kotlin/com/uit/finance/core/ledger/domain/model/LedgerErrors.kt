package com.uit.finance.core.ledger.domain.model

/**
 * Mã lỗi dùng chung với backend (ADR-005 §3), đặt trong `FieldError.message` của
 * `AppError.Validation`. UI map một lần cho cả lỗi kiểm ở máy lẫn lỗi server trả về.
 */
object LedgerErrorCode {
    const val INVALID_FIELD = "ledger.invalid_field"
    const val CATEGORY_KIND_MISMATCH = "ledger.category_kind_mismatch"
    const val INVALID_TRANSFER = "ledger.invalid_transfer"
    const val ARCHIVED = "ledger.archived"
    const val CURRENCY_LOCKED = "ledger.currency_locked"
    const val IN_USE = "ledger.in_use"
    const val KIND_IMMUTABLE = "ledger.kind_immutable"
    const val INVALID_PARENT = "ledger.invalid_parent"
    const val LIMIT_EXCEEDED = "ledger.limit_exceeded"
    const val NOT_FOUND = "ledger.not_found"
}
