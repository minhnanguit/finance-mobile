package com.uit.finance.core.ledger.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Kỳ báo cáo (docs/REPORTS-PLAN.md R1): một tháng hoặc một năm theo `occurredOn`. Thêm kỳ mới (tuần, tuỳ chọn) =
 * thêm biến thể.
 */
sealed interface ReportPeriod {
    /** Ngày đầu, gồm cả ngày đó. */
    val start: LocalDate

    /** Ngày cuối, gồm cả ngày đó. */
    val end: LocalDate

    fun previous(): ReportPeriod
    fun next(): ReportPeriod

    /** Kỳ này chứa [date]. */
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end

    /** Kỳ còn ở tương lai so với [today]: không cho xem (R1). */
    fun isAfter(today: LocalDate): Boolean = start > today

    data class Month(val month: YearMonth) : ReportPeriod {
        override val start: LocalDate get() = month.firstDay
        override val end: LocalDate get() = month.lastDay
        override fun previous() = Month(month.minus(1, DateTimeUnit.MONTH))
        override fun next() = Month(month.plus(1, DateTimeUnit.MONTH))
    }

    data class Year(val year: Int) : ReportPeriod {
        override val start: LocalDate get() = LocalDate(year, 1, 1)
        override val end: LocalDate get() = LocalDate(year, 12, 31)
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }

    companion object {
        fun monthOf(date: LocalDate): ReportPeriod = Month(date.yearMonth)
        fun yearOf(date: LocalDate): ReportPeriod = Year(date.year)
    }
}
