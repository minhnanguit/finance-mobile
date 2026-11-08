package com.uit.finance.core.budget.domain.validation

import com.uit.finance.core.budget.domain.model.BudgetErrorCode
import com.uit.finance.core.common.result.FieldError
import kotlinx.datetime.YearMonth

/**
 * Bản sao phía máy của giới hạn backend (ADR-007 §4). Kiểm sớm để không phải chờ server trả `REJECTED`;
 * server vẫn là người quyết định cuối.
 */
object BudgetLimits {
    const val MAX_AMOUNT_MINOR: Long = 1_000_000_000_000_000L
    const val MAX_BUDGETS = 100
    val EARLIEST_MONTH = YearMonth(2000, 1)
}

/** Kiểm từng field, không cần DB. Trả danh sách lỗi; rỗng là hợp lệ. */
object BudgetRules {

    private val CURRENCY = Regex("[A-Z]{3}")

    fun amount(minor: Long): List<FieldError> =
        if (minor in 1..BudgetLimits.MAX_AMOUNT_MINOR) emptyList() else invalid("amountMinor")

    fun currency(value: String): List<FieldError> = if (CURRENCY.matches(value)) emptyList() else invalid("currency")

    /**
     * Tháng bắt đầu = tháng tạo theo lịch của máy (G4), nên không bao giờ quá "tháng UTC + 1" của server.
     * Máy chỉ chặn tháng quá xa trong quá khứ.
     */
    fun startMonth(month: YearMonth): List<FieldError> =
        if (month >= BudgetLimits.EARLIEST_MONTH) emptyList() else invalid("startMonth")

    private fun invalid(field: String) = listOf(FieldError(field, BudgetErrorCode.INVALID_FIELD))
}
