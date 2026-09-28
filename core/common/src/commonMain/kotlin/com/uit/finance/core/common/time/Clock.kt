@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.common.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Time source abstraction. Production uses the system clock; tests use a fixed/advancing clock.
 * Domain code depends on this interface, never on `kotlin.time.Clock.System` directly.
 */
interface Clock {
    fun now(): Instant

    fun today(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        now().toLocalDateTime(timeZone).date
}

object SystemClock : Clock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}
