package com.uit.finance.core.reminder.domain

import com.uit.finance.core.reminder.domain.model.PlannedReminder
import com.uit.finance.core.reminder.domain.model.ReminderSettings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

class ReminderPlanner {
    fun plan(
        settings: ReminderSettings,
        now: Instant,
        zone: TimeZone,
        loggedToday: Boolean,
    ): List<PlannedReminder> {
        if (!settings.enabled) return emptyList()
        val today = now.toLocalDateTime(zone).date
        return (0 until WINDOW_DAYS).mapNotNull { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            val fireAt = LocalDateTime(date, settings.time).toInstant(zone)
            if (fireAt <= now || offset == 0 && loggedToday) null else PlannedReminder(date, fireAt)
        }
    }

    companion object { const val WINDOW_DAYS = 14 }
}
