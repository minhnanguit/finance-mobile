package com.uit.finance.core.recurring.domain.validation

import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Giới hạn khớp ADR-008 §7. */
object RecurringLimits {
    const val MAX_NAME_LENGTH = 50
    const val MAX_AMOUNT_MINOR: Long = 1_000_000_000_000_000L
    const val MAX_NOTE_LENGTH = 500
    const val MAX_INTERVAL = 12
    const val MAX_REMIND_DAYS = 7
    const val MAX_REVISIONS = 50
    const val MAX_ACTIVE = 200

    /** Bù kỳ lỡ tối đa 90 ngày kể cả hôm nay (R7). */
    const val CATCH_UP_DAYS = 90
    val EARLIEST_DATE = LocalDate(2000, 1, 1)
    const val MAX_DAYS_AHEAD = 366
}

/** Kiểm ở máy trước khi ghi, cùng luật với server; server vẫn là người quyết định cuối. */
object RecurringRules {

    fun name(value: String): List<FieldError> =
        if (value.trim().length in 1..RecurringLimits.MAX_NAME_LENGTH) emptyList() else invalid("name")

    fun amount(value: Long): List<FieldError> =
        if (value in 1..RecurringLimits.MAX_AMOUNT_MINOR) emptyList() else invalid("amountMinor")

    fun note(value: String?): List<FieldError> =
        if (value == null || value.length <= RecurringLimits.MAX_NOTE_LENGTH) emptyList() else invalid("note")

    fun remind(value: Int?): List<FieldError> =
        if (value == null || value in 0..RecurringLimits.MAX_REMIND_DAYS) emptyList() else invalid("remindDaysBefore")

    fun rule(rule: RecurrenceRule): List<FieldError> {
        val ok = rule.interval in 1..RecurringLimits.MAX_INTERVAL && when (rule.frequency) {
            Frequency.WEEKLY -> rule.weekday in 1..7 && rule.month == null && rule.dayOfMonth == null
            Frequency.MONTHLY -> rule.weekday == null && rule.month == null && rule.dayOfMonth in 1..31
            Frequency.YEARLY -> rule.weekday == null && rule.month in 1..12 && rule.dayOfMonth in 1..31
        }
        return if (ok) emptyList() else invalid("rule")
    }

    fun startDate(value: LocalDate, today: LocalDate): List<FieldError> =
        if (value >= RecurringLimits.EARLIEST_DATE && value <= today.plus(DatePeriod(days = RecurringLimits.MAX_DAYS_AHEAD))) {
            emptyList()
        } else {
            invalid("startDate")
        }

    /** Phiên bản mới chỉ áp từ hôm nay trở đi (R10). */
    fun effectiveFrom(value: LocalDate, today: LocalDate): List<FieldError> =
        if (value >= today && value <= today.plus(DatePeriod(days = RecurringLimits.MAX_DAYS_AHEAD))) {
            emptyList()
        } else {
            invalid("effectiveFrom")
        }

    private fun invalid(field: String) = listOf(FieldError(field, RecurringErrorCode.INVALID_FIELD))
}
