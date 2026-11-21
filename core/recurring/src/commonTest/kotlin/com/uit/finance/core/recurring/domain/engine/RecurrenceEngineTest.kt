package com.uit.finance.core.recurring.domain.engine

import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

class RecurrenceEngineTest {

    private fun rev(from: LocalDate, rule: RecurrenceRule, anchor: LocalDate = from, id: String = "r-$from") =
        RecurrenceRevision(id, from, anchor, "a", "c", 1_000, null, rule, false)

    private fun dates(revisions: List<RecurrenceRevision>, from: LocalDate, to: LocalDate) =
        RecurrenceEngine.occurrences(revisions, from, to).map { it.first }

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `ngày 31 hằng tháng lùi về cuối tháng rồi quay lại ngày 31`() {
        val rule = RecurrenceRule.monthly(31)

        assertEquals(
            listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")),
            dates(listOf(rev(d("2026-01-01"), rule)), d("2026-01-01"), d("2026-04-30")),
        )
    }

    @Test
    fun `29-02 hằng năm - năm thường là 28-02 và năm nhuận là 29-02`() {
        val rule = RecurrenceRule.yearly(month = 2, dayOfMonth = 29)

        assertEquals(
            listOf(d("2027-02-28"), d("2028-02-29")),
            dates(listOf(rev(d("2027-01-01"), rule)), d("2027-01-01"), d("2028-12-31")),
        )
    }

    @Test
    fun `mỗi 2 tháng tính từ tháng chứa anchor`() {
        val rule = RecurrenceRule.monthly(5, interval = 2)

        assertEquals(
            listOf(d("2026-01-05"), d("2026-03-05"), d("2026-05-05")),
            dates(listOf(rev(d("2026-01-01"), rule)), d("2026-01-01"), d("2026-06-30")),
        )
    }

    @Test
    fun `hằng tuần qua năm mới - tuần ISO bắt đầu thứ Hai`() {
        val rule = RecurrenceRule.weekly(weekday = 5, interval = 2) // thứ Sáu mỗi 2 tuần

        assertEquals(
            listOf(d("2025-12-26"), d("2026-01-09"), d("2026-01-23")),
            dates(listOf(rev(d("2025-12-22"), rule)), d("2025-12-22"), d("2026-01-31")),
        )
    }

    @Test
    fun `đổi giá giữ anchor cũ nên nhịp mỗi 2 tháng không lệch`() {
        val rule = RecurrenceRule.monthly(5, interval = 2)
        val first = rev(d("2026-01-01"), rule)
        val raise = rev(d("2026-04-01"), rule, anchor = d("2026-01-01"), id = "raise")

        val result = RecurrenceEngine.occurrences(listOf(first, raise), d("2026-01-01"), d("2026-08-31"))

        assertEquals(listOf("2026-01-05", "2026-03-05", "2026-05-05", "2026-07-05"), result.map { it.first.toString() })
        assertEquals(listOf("r-2026-01-01", "r-2026-01-01", "raise", "raise"), result.map { it.second.id })
    }

    @Test
    fun `đổi lịch ngày 5 sang ngày 10 từ đầu tháng sau - không có ngày 10 tháng này`() {
        val first = rev(d("2026-01-01"), RecurrenceRule.monthly(5))
        val moved = rev(d("2026-12-01"), RecurrenceRule.monthly(10), id = "moved")

        assertEquals(
            listOf(d("2026-11-05"), d("2026-12-10")),
            dates(listOf(first, moved), d("2026-11-01"), d("2026-12-31")),
        )
    }

    @Test
    fun `ngày kết thúc chặn kỳ - kỳ đúng ngày kết thúc vẫn tính`() {
        val recurring = Recurring(
            id = "k", name = "Tiền nhà", type = RecurringType.EXPENSE, currency = "VND",
            generationNotBefore = d("2026-01-01"), generationEndDate = d("2026-03-05"), remindDaysBefore = null,
            revision = 1, revisions = listOf(rev(d("2026-01-01"), RecurrenceRule.monthly(5))),
        )

        assertEquals(
            listOf("2026-01-05", "2026-02-05", "2026-03-05"),
            RecurrenceEngine.occurrences(recurring, d("2026-01-01"), d("2026-12-31")).map { it.date.toString() },
        )
        assertEquals(null, RecurrenceEngine.next(recurring, d("2026-03-05")))
        assertEquals(d("2026-02-05"), RecurrenceEngine.next(recurring, d("2026-01-05"))?.date)
    }

    /**
     * 10.000 timeline ngẫu nhiên (seed cố định) trên khoảng 10 năm, so với bản brute-force duyệt từng ngày.
     * Bất biến: tăng dần, không trùng, nằm trong khoảng hỏi.
     */
    @Test
    fun `property - khớp brute-force trên 10000 lịch ngẫu nhiên`() {
        val random = Random(20261110)
        val origin = d("2020-01-01")
        repeat(10_000) {
            val revisions = randomTimeline(random, origin)
            val from = origin.plus(DatePeriod(days = random.nextInt(0, 3_000)))
            val to = from.plus(DatePeriod(days = random.nextInt(0, 400)))

            val actual = dates(revisions, from, to)
            val expected = bruteForce(revisions, from, to)

            assertEquals(expected, actual, "timeline=$revisions from=$from to=$to")
            assertTrue(actual.zipWithNext().all { (a, b) -> a < b })
            assertTrue(actual.all { it in from..to })
        }
    }

    private fun randomTimeline(random: Random, origin: LocalDate): List<RecurrenceRevision> {
        var effective = origin.plus(DatePeriod(days = random.nextInt(0, 1_500)))
        return List(random.nextInt(1, 4)) { i ->
            val rule = when (Frequency.entries[random.nextInt(3)]) {
                Frequency.WEEKLY -> RecurrenceRule.weekly(random.nextInt(1, 8), random.nextInt(1, 13))
                Frequency.MONTHLY -> RecurrenceRule.monthly(random.nextInt(1, 32), random.nextInt(1, 13))
                Frequency.YEARLY -> RecurrenceRule.yearly(random.nextInt(1, 13), random.nextInt(1, 32), random.nextInt(1, 4))
            }
            val anchor = effective.plus(DatePeriod(days = -random.nextInt(0, 400)))
            rev(effective, rule, anchor = anchor, id = "r$i").also {
                effective = effective.plus(DatePeriod(days = random.nextInt(1, 500)))
            }
        }
    }

    /** Duyệt từng ngày, kiểm điều kiện trực tiếp theo định nghĩa của R2–R5. */
    private fun bruteForce(revisions: List<RecurrenceRevision>, from: LocalDate, to: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var day = from
        while (day <= to) {
            val revision = revisions.lastOrNull { it.effectiveFrom <= day }
            if (revision != null && matches(revision, day)) result += day
            day = day.plus(DatePeriod(days = 1))
        }
        return result
    }

    private fun matches(revision: RecurrenceRevision, day: LocalDate): Boolean {
        val rule = revision.rule
        val anchor = revision.anchorDate
        return when (rule.frequency) {
            Frequency.WEEKLY -> {
                val weeks = mondayOf(anchor).daysUntil(mondayOf(day)) / 7
                day.dayOfWeek.ordinal + 1 == rule.weekday && weeks >= 0 && weeks % rule.interval == 0
            }
            Frequency.MONTHLY -> {
                val months = (day.year - anchor.year) * 12 + (day.month.ordinal - anchor.month.ordinal)
                val target = minOf(rule.dayOfMonth!!, day.yearMonth.lastDay.day)
                months >= 0 && months % rule.interval == 0 && day.day == target
            }
            Frequency.YEARLY -> {
                val years = day.year - anchor.year
                val target = minOf(rule.dayOfMonth!!, day.yearMonth.lastDay.day)
                years >= 0 && years % rule.interval == 0 && day.month.ordinal + 1 == rule.month && day.day == target
            }
        }
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.plus(DatePeriod(days = -date.dayOfWeek.ordinal))
}
