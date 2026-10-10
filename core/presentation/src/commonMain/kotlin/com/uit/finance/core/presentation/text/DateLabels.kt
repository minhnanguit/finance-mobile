package com.uit.finance.core.presentation.text

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number

/** Nhãn ngày ngắn cho danh sách: "Hôm nay", "Hôm qua", "9 thg 10", khác năm thì "9 thg 10, 2025". */
fun LocalDate.toRelativeLabel(today: LocalDate): String = when (this) {
    today -> "Hôm nay"
    today.minus(1, DateTimeUnit.DAY) -> "Hôm qua"
    else -> if (year == today.year) "$day thg ${month.number}" else "$day thg ${month.number}, $year"
}

/** "Thứ Năm, 9 tháng 10" — dòng ngày dưới lời chào. */
fun LocalDate.toLongLabel(): String = "${dayOfWeek.vietnamese()}, $day tháng ${month.number}"

private fun DayOfWeek.vietnamese(): String = when (this) {
    DayOfWeek.MONDAY -> "Thứ Hai"
    DayOfWeek.TUESDAY -> "Thứ Ba"
    DayOfWeek.WEDNESDAY -> "Thứ Tư"
    DayOfWeek.THURSDAY -> "Thứ Năm"
    DayOfWeek.FRIDAY -> "Thứ Sáu"
    DayOfWeek.SATURDAY -> "Thứ Bảy"
    DayOfWeek.SUNDAY -> "Chủ Nhật"
}
