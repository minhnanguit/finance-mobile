package com.uit.finance.core.budget.domain.model

import com.uit.finance.core.common.money.Money
import kotlinx.datetime.YearMonth

/**
 * Ngân sách của một danh mục chi cấp 1, một tiền tệ, lặp lại mỗi tháng từ [startMonth] (ADR-007 G1, G4).
 * Chỉ [amount] sửa được (G8). "Đã chi" không nằm đây: luôn tính từ giao dịch (G6).
 */
data class Budget(
    val id: String,
    val categoryId: String,
    val amount: Money,
    val startMonth: YearMonth,
) {
    val currency: String get() = amount.currency

    /** Tháng trước [startMonth] không hiện ngân sách này (G4). */
    fun appliesTo(month: YearMonth): Boolean = month >= startMonth

    /** Không in số tiền (ADR-006 B8). */
    override fun toString(): String = "Budget(id=$id, categoryId=$categoryId, currency=$currency, startMonth=$startMonth)"
}

/** Dữ liệu để tạo mới; id do client sinh khi lưu (ADR-007 GS2). */
data class BudgetDraft(
    val categoryId: String,
    val currency: String,
    val amountMinor: Long,
    val startMonth: YearMonth,
) {
    override fun toString(): String = "BudgetDraft(categoryId=$categoryId, currency=$currency, startMonth=$startMonth)"
}
