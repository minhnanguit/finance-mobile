package com.uit.finance.core.recurring.domain.engine

import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.ScheduledOccurrence
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Tính các ngày đến hạn từ timeline (ADR-009 R2–R5). Kotlin thuần, không phụ thuộc nền tảng.
 *
 * Mỗi phiên bản i áp trong `[effectiveFrom_i, effectiveFrom_(i+1))`; trong khoảng đó lấy các ngày theo lịch của nó,
 * nhịp tính từ `anchorDate`. Ngày không tồn tại lùi về cuối tháng, nhưng kỳ sau luôn tính lại từ ngày mục tiêu.
 */
object RecurrenceEngine {

    /** Mọi kỳ của [recurring] trong `[from, to]`, tăng dần, có chặn bởi ngày kết thúc. */
    fun occurrences(recurring: Recurring, from: LocalDate, to: LocalDate): List<ScheduledOccurrence> {
        val end = recurring.generationEndDate?.let { minOf(it, to) } ?: to
        return occurrences(recurring.revisions, from, end).map { (date, revision) ->
            ScheduledOccurrence(recurring.id, date, revision)
        }
    }

    /** Kỳ đầu tiên sau [after] (không tính [after]), trong vòng [horizonDays] ngày; `null` nếu không có. */
    fun next(recurring: Recurring, after: LocalDate, horizonDays: Int = DEFAULT_HORIZON_DAYS): ScheduledOccurrence? {
        val from = after.plus(DatePeriod(days = 1))
        return occurrences(recurring, from, from.plus(DatePeriod(days = horizonDays))).firstOrNull()
    }

    fun occurrences(
        revisions: List<RecurrenceRevision>,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, RecurrenceRevision>> {
        if (from > to || revisions.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<LocalDate, RecurrenceRevision>>()
        revisions.forEachIndexed { i, revision ->
            val segmentEnd = revisions.getOrNull(i + 1)?.effectiveFrom?.minus(DatePeriod(days = 1))
            val start = maxOf(from, revision.effectiveFrom)
            val stop = segmentEnd?.let { minOf(it, to) } ?: to
            if (start <= stop) {
                datesOf(revision.rule, revision.anchorDate, start, stop).forEach { result += it to revision }
            }
        }
        return result
    }

    /** Ngày theo [rule] trong `[start, stop]`, kỳ 0 là tuần/tháng/năm chứa [anchor]. */
    internal fun datesOf(rule: RecurrenceRule, anchor: LocalDate, start: LocalDate, stop: LocalDate): List<LocalDate> =
        when (rule.frequency) {
            Frequency.WEEKLY -> weekly(rule, anchor, start, stop)
            Frequency.MONTHLY -> monthly(rule, anchor, start, stop)
            Frequency.YEARLY -> yearly(rule, anchor, start, stop)
        }

    private fun weekly(rule: RecurrenceRule, anchor: LocalDate, start: LocalDate, stop: LocalDate): List<LocalDate> {
        val weekday = requireNotNull(rule.weekday)
        val anchorWeek = mondayOf(anchor)
        val dates = mutableListOf<LocalDate>()
        val weeksToStart = (anchorWeek.daysUntil(mondayOf(start)) / DAYS_PER_WEEK).coerceAtLeast(0)
        var k = weeksToStart - weeksToStart % rule.interval
        while (true) {
            val date = anchorWeek.plus(DatePeriod(days = k * DAYS_PER_WEEK + weekday - 1))
            if (date > stop) break
            if (date >= start) dates += date
            k += rule.interval
        }
        return dates
    }

    private fun monthly(rule: RecurrenceRule, anchor: LocalDate, start: LocalDate, stop: LocalDate): List<LocalDate> {
        val day = requireNotNull(rule.dayOfMonth)
        val anchorIndex = monthIndex(anchor.yearMonth)
        val dates = mutableListOf<LocalDate>()
        val toStart = (monthIndex(start.yearMonth) - anchorIndex).coerceAtLeast(0)
        var k = toStart - toStart % rule.interval
        while (true) {
            val month = yearMonthOf(anchorIndex + k)
            val date = clamp(month, day)
            if (month.firstDay > stop) break
            if (date in start..stop) dates += date
            k += rule.interval
        }
        return dates
    }

    private fun yearly(rule: RecurrenceRule, anchor: LocalDate, start: LocalDate, stop: LocalDate): List<LocalDate> {
        val month = requireNotNull(rule.month)
        val day = requireNotNull(rule.dayOfMonth)
        val dates = mutableListOf<LocalDate>()
        val toStart = (start.year - anchor.year).coerceAtLeast(0)
        var k = toStart - toStart % rule.interval
        while (true) {
            val year = anchor.year + k
            if (year > stop.year) break
            val date = clamp(YearMonth(year, month), day)
            if (date in start..stop) dates += date
            k += rule.interval
        }
        return dates
    }

    private fun clamp(month: YearMonth, day: Int): LocalDate = LocalDate(month.year, month.month, minOf(day, month.lastDay.day))

    private fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.ordinal))

    private fun monthIndex(month: YearMonth): Int = month.year * MONTHS_PER_YEAR + (month.month.ordinal)

    private fun yearMonthOf(index: Int): YearMonth = YearMonth(index / MONTHS_PER_YEAR, index % MONTHS_PER_YEAR + 1)

    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12
    const val DEFAULT_HORIZON_DAYS = 800
}
