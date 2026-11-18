package com.uit.finance.core.reminder.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

data class ReminderSettings(val enabled: Boolean, val time: LocalTime) {
    companion object { val DEFAULT = ReminderSettings(enabled = false, time = LocalTime(21, 0)) }
}

data class PlannedReminder(val date: LocalDate, val fireAt: Instant)
