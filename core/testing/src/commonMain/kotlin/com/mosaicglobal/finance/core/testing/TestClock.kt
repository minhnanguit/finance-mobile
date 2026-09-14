@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.testing

import com.mosaicglobal.finance.core.common.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Deterministic clock. Starts at a fixed instant and only moves when the test says so. */
class TestClock(
    private var current: Instant = Instant.fromEpochSeconds(1_700_000_000L), // 2023-11-14T22:13:20Z
) : Clock {
    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }

    fun set(instant: Instant) {
        current = instant
    }
}
