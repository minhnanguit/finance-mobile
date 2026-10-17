@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.uit.finance.core.common.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ObserveTodayTest {

    /** Đồng hồ chạy theo thời gian ảo của test: advanceTimeBy vừa đẩy delay vừa đẩy giờ. */
    private fun TestScope.clockStartingAt(start: Instant) = object : Clock {
        override fun now(): Instant = start + testScheduler.currentTime.milliseconds
    }

    private fun TestScope.collect(clock: Clock, timeZone: TimeZone): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        backgroundScope.launch { observeToday(clock, timeZone).toList(days) }
        runCurrent()
        return days
    }

    @Test
    fun `phát ngay ngày hôm nay rồi phát lại đúng lúc qua 00h - sang cả tháng mới`() = runTest {
        val days = collect(clockStartingAt(Instant.parse("2026-10-31T23:59:00Z")), TimeZone.UTC)
        assertEquals(listOf(LocalDate(2026, 10, 31)), days)

        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 10, 31)), days)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)), days)
    }

    @Test
    fun `nửa đêm theo lịch của máy - không phải theo UTC`() = runTest {
        // 16h59 UTC = 23h59 ở UTC+7.
        val days = collect(clockStartingAt(Instant.parse("2026-10-31T16:59:00Z")), TimeZone.of("Asia/Ho_Chi_Minh"))
        assertEquals(listOf(LocalDate(2026, 10, 31)), days)

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)), days)
    }

    @Test
    fun `để mở nhiều ngày thì phát mỗi ngày đúng một lần`() = runTest {
        val days = collect(clockStartingAt(Instant.parse("2026-10-30T12:00:00Z")), TimeZone.UTC)

        advanceTimeBy((3 * 24 * 60).minutes)
        runCurrent()

        assertEquals(
            listOf(LocalDate(2026, 10, 30), LocalDate(2026, 10, 31), LocalDate(2026, 11, 1), LocalDate(2026, 11, 2)),
            days,
        )
    }
}
