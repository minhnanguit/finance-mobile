package com.uit.finance.core.reminder.domain

import com.uit.finance.core.reminder.domain.model.ReminderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class ReminderPlannerTest {
    private val planner = ReminderPlanner()
    private val zone = TimeZone.of("Asia/Ho_Chi_Minh")

    @Test
    fun `disabled settings produce no reminders`() {
        val plan = planner.plan(ReminderSettings.DEFAULT, Instant.parse("2026-11-16T10:00:00Z"), zone, false)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `enabled settings produce fourteen future reminders`() {
        val plan = planner.plan(enabledAt(21, 0), Instant.parse("2026-11-16T10:00:00Z"), zone, false)
        assertEquals(14, plan.size)
        assertEquals("2026-11-16", plan.first().date.toString())
    }

    @Test
    fun `past time skips today`() {
        val plan = planner.plan(enabledAt(21, 0), Instant.parse("2026-11-16T15:00:00Z"), zone, false)
        assertEquals(13, plan.size)
        assertEquals("2026-11-17", plan.first().date.toString())
    }

    @Test
    fun `logged today skips todays reminder`() {
        val plan = planner.plan(enabledAt(21, 0), Instant.parse("2026-11-16T10:00:00Z"), zone, true)
        assertEquals(13, plan.size)
        assertEquals("2026-11-17", plan.first().date.toString())
    }

    @Test
    fun `dst gap still yields valid instants`() {
        val berlin = TimeZone.of("Europe/Berlin")
        val plan = planner.plan(enabledAt(2, 30), Instant.parse("2027-03-27T10:00:00Z"), berlin, false)
        assertTrue(plan.isNotEmpty())
        assertEquals(plan.size, plan.map { it.fireAt }.distinct().size)
    }

    private fun enabledAt(hour: Int, minute: Int) = ReminderSettings(true, LocalTime(hour, minute))
}
