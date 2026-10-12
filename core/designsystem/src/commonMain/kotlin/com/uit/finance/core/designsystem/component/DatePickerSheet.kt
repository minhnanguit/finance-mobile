@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.designsystem.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Chọn một ngày theo lịch (không giờ, D5). Material3 làm việc với millis UTC nên đổi qua lại ở đây, nơi gọi chỉ
 * thấy `LocalDate`. Ngày ngoài [range] bị khoá.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    selected: LocalDate,
    range: ClosedRange<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val bounds = remember(range) { range.start.toUtcMillis()..range.endInclusive.toUtcMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        yearRange = range.start.year..range.endInclusive.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in bounds
            override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(it.toUtcDate()) }
                onDismiss()
            }) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
