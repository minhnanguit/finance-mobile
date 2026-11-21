@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.reminder

import com.uit.finance.core.recurring.domain.model.OccurrenceIds
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ReminderPlannerTest {

    private val zone = TimeZone.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate(2026, 11, 10)
    private val now = Instant.parse("2026-11-10T03:00:00Z") // 10:00 giờ VN

    private fun recurring(id: String, day: Int, remind: Int?, amount: Long = 3_000_000) = Recurring(
        id = id, name = "Tiền nhà", type = RecurringType.EXPENSE, currency = "VND",
        generationNotBefore = LocalDate(2026, 1, 1), generationEndDate = null, remindDaysBefore = remind, revision = 1,
        revisions = listOf(
            RecurrenceRevision("$id-r", LocalDate(2026, 1, 1), LocalDate(2026, 1, 1), "a", "c", amount, null, RecurrenceRule.monthly(day), false),
        ),
    )

    @Test
    fun `nhắc trước 2 ngày lúc 9 giờ - bỏ giờ nhắc đã qua - bỏ khoản tắt nhắc`() {
        val plan = ReminderPlanner.plan(listOf(recurring("k1", day = 12, remind = 2), recurring("k2", day = 15, remind = null)), now, zone, today)

        // Kỳ 12/11 nhắc 10/11 09:00, đã qua lúc 10:00: bỏ. Kỳ tiếp 12/12 nhắc 10/12 09:00.
        assertEquals(listOf(LocalDate(2026, 12, 12)), plan.map { it.dueDate })
        assertEquals(Instant.parse("2026-12-10T02:00:00Z"), plan.single().fireAt)
    }

    @Test
    fun `kỳ đã có giao dịch thì không nhắc - tối đa 30 thông báo sắp theo giờ`() {
        val many = (1..40).map { recurring("k$it", day = (it % 28) + 1, remind = 0) }
        val recorded = setOf(OccurrenceIds.of("k1", LocalDate(2026, 11, 2)))

        val plan = ReminderPlanner.plan(many, now, zone, today, recorded)

        assertEquals(ReminderPlanner.MAX_PENDING, plan.size)
        assertTrue(plan.zipWithNext().all { (a, b) -> a.fireAt <= b.fireAt })
        assertFalse(plan.any { it.id in recorded })
    }

    @Test
    fun `nội dung chỉ có tên và ngày - không có số tiền`() {
        val reminder = ReminderPlanner.plan(listOf(recurring("k1", day = 15, remind = 1, amount = 3_456_789)), now, zone, today).first()

        val body = ReminderPlanner.body(reminder)

        assertEquals("Tiền nhà đến hạn ngày 15/11", body)
        assertFalse("3456789" in body || "3.456.789" in body)
    }
}
