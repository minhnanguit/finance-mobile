@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.common.time

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * "Hôm nay" theo lịch của máy, tự cập nhật (ADR-007 G12): phát ngay, rồi phát lại lúc 00:00 kế tiếp ở
 * [timeZone]. Màn hình để mở qua nửa đêm không bị kẹt số của hôm trước.
 *
 * Đồng hồ máy bị chỉnh lùi thì lần phát kế tiếp đến muộn; màn hình nên đăng ký lại khi app quay về
 * foreground để đọc lại ngay.
 */
fun observeToday(clock: Clock, timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        emit(today)
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        delay((nextMidnight - now).coerceAtLeast(MIN_WAIT))
    }
}.distinctUntilChanged()

/** Chặn vòng lặp bận khi đồng hồ đứng đúng nửa đêm hoặc bị chỉnh giữa chừng. */
private val MIN_WAIT = 1.milliseconds
