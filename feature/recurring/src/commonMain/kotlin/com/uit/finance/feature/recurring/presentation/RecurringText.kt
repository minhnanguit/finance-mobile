package com.uit.finance.feature.recurring.presentation

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import com.uit.finance.core.ui.ledger.toLedgerUiText
import kotlinx.datetime.LocalDate

/** Mô tả lịch cho người đọc: "Ngày 5 hằng tháng", "Thứ Hai mỗi 2 tuần", "Ngày cuối tháng hằng tháng". */
internal fun RecurrenceRule.describe(): String = when (frequency) {
    Frequency.WEEKLY -> "${weekdayLabel(weekday ?: 1)} ${every(interval, "tuần")}"
    Frequency.MONTHLY -> "${dayLabel(dayOfMonth ?: 1)} ${every(interval, "tháng")}"
    Frequency.YEARLY -> "Ngày ${dayOfMonth ?: 1}/${month ?: 1} ${every(interval, "năm")}"
}

internal fun weekdayLabel(isoDay: Int): String = when (isoDay) {
    1 -> "Thứ Hai"
    2 -> "Thứ Ba"
    3 -> "Thứ Tư"
    4 -> "Thứ Năm"
    5 -> "Thứ Sáu"
    6 -> "Thứ Bảy"
    else -> "Chủ Nhật"
}

/** Ngày 31 nghĩa là ngày cuối tháng (R5). */
internal fun dayLabel(day: Int): String = if (day >= 31) "Ngày cuối tháng" else "Ngày $day"

private fun every(interval: Int, unit: String) = if (interval == 1) "hằng $unit" else "mỗi $interval $unit"

/** "05/12/2026". */
internal fun LocalDate.shortLabel(): String =
    "${day.toString().padStart(2, '0')}/${(month.ordinal + 1).toString().padStart(2, '0')}/$year"

/** "05/12". */
internal fun LocalDate.dayMonthLabel(): String =
    "${day.toString().padStart(2, '0')}/${(month.ordinal + 1).toString().padStart(2, '0')}"

internal fun remindLabel(days: Int?): String = when (days) {
    null -> "Không nhắc"
    0 -> "Đúng ngày đến hạn, 9:00"
    else -> "Trước $days ngày, 9:00"
}

/** Mã lỗi `recurring.*` (máy kiểm hoặc server trả qua sync, ADR-008 §6) → câu người dùng đọc được. */
internal fun AppError.toRecurringUiText(): UiText = when (this) {
    is AppError.Validation -> fieldErrors.firstOrNull()?.toRecurringUiText() ?: toLedgerUiText()
    is AppError.Api -> code?.let { FieldError("", it).toRecurringUiText() } ?: toLedgerUiText()
    else -> toLedgerUiText()
}

internal fun FieldError.toRecurringUiText(): UiText = when (message) {
    RecurringErrorCode.INVALID_FIELD -> when (field) {
        "name" -> key("field.name", "Tên từ 1 đến 50 ký tự.")
        "amountMinor" -> key("field.amount", "Số tiền phải lớn hơn 0 và không quá 1 triệu tỷ.")
        "note" -> key("field.note", "Ghi chú tối đa 500 ký tự.")
        "startDate" -> key("field.start", "Ngày bắt đầu không hợp lệ.")
        "effectiveFrom" -> key("field.effective", "Thay đổi chỉ áp dụng từ hôm nay trở đi.")
        "endDate" -> key("field.end", "Ngày kết thúc không được trước hôm qua.")
        else -> key("field.invalid", "Thông tin chưa hợp lệ.")
    }
    RecurringErrorCode.LIMIT_REACHED -> key("limit", "Đã đủ 200 khoản định kỳ đang chạy.")
    RecurringErrorCode.NOT_FOUND -> key("not_found", "Không còn khoản định kỳ này (có thể đã bị xoá trên máy khác).")
    RecurringErrorCode.IMMUTABLE_FIELD -> key("immutable", "Khoản đã kết thúc không kéo dài được. Hãy tạo khoản mới.")
    RecurringErrorCode.REVISION_CONFLICT -> key("conflict", "Khoản này vừa được sửa ở thiết bị khác. Hãy xem lại rồi sửa tiếp.")
    RecurringErrorCode.INVALID_CATEGORY -> key("invalid_category", "Danh mục không còn dùng được hoặc khác loại thu/chi.")
    RecurringErrorCode.INVALID_ACCOUNT -> key("invalid_account", "Ví đã bị xoá, hãy chọn ví khác.")
    RecurringErrorCode.CURRENCY_MISMATCH -> key("currency", "Ví phải cùng loại tiền với khoản định kỳ.")
    RecurringErrorCode.REFERENCE_PENDING -> key("pending", "Ví hoặc danh mục chưa đồng bộ xong, app sẽ tự thử lại.")
    RecurringErrorCode.DELETED -> key("deleted", "Khoản định kỳ đã bị xoá trên máy khác.")
    else -> if (message.startsWith("recurring.")) key("unknown", "Khoản định kỳ chưa hợp lệ.") else toLedgerUiText()
}

private fun key(suffix: String, fallback: String) = UiText.Key("recurring.$suffix", fallback)
