@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.reminder

import com.uit.finance.core.recurring.domain.engine.RecurrenceEngine
import com.uit.finance.core.recurring.domain.model.Recurring
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Một thông báo nhắc hẹn trước (ADR-008 R15): **chỉ tên khoản và ngày đến hạn**, không có số tiền, vì màn hình khoá
 * ai cũng thấy. [id] cố định theo (khoản, ngày) để hẹn lại không bị trùng.
 */
data class ReminderNotification(
    val id: String,
    val recurringName: String,
    val dueDate: LocalDate,
    val fireAt: Instant,
)

/** Hẹn/huỷ thông báo cục bộ của nền tảng (Android WorkManager + NotificationManager, iOS UNUserNotificationCenter). */
interface ReminderScheduler {
    /** Thay toàn bộ thông báo đang chờ của feature bằng [reminders]. */
    fun replaceAll(reminders: List<ReminderNotification>)

    /** Huỷ thông báo đang chờ **và đang hiện** (đăng xuất, đổi user). */
    fun cancelAll()
}

/** Quyền hiện thông báo. Từ chối không chặn việc tạo khoản (chỉ không nhắc được). */
interface NotificationPermissionGate {
    fun isGranted(): Boolean

    /** Hỏi quyền một lần; đã có hoặc nền tảng không cần thì trả `true` ngay. */
    suspend fun request(): Boolean
}

/** Tính danh sách thông báo cần hẹn. Kotlin thuần để test được. */
object ReminderPlanner {
    /** Giờ nhắc mặc định theo giờ máy. */
    val REMIND_AT: LocalTime = LocalTime(9, 0)

    /** iOS chỉ giữ 64 thông báo chờ cho cả app; feature này dùng tối đa 30 gần nhất. */
    const val MAX_PENDING = 30

    private const val HORIZON_DAYS = 45

    /**
     * Kỳ sắp tới của các khoản bật nhắc, giờ nhắc = (ngày đến hạn − số ngày nhắc trước) lúc 09:00, chỉ lấy giờ nhắc
     * còn ở tương lai, bỏ kỳ đã có giao dịch ([alreadyRecorded], tính bằng id kỳ), sắp theo giờ nhắc, tối đa [MAX_PENDING].
     */
    fun plan(
        recurrings: List<Recurring>,
        now: Instant,
        timeZone: TimeZone,
        today: LocalDate,
        alreadyRecorded: Set<String> = emptySet(),
    ): List<ReminderNotification> = recurrings
        .filter { it.remindDaysBefore != null && !it.isEnded(today) }
        .flatMap { recurring ->
            val days = recurring.remindDaysBefore ?: 0
            RecurrenceEngine.occurrences(recurring, today, today.plus(DatePeriod(days = HORIZON_DAYS)))
                .filter { it.transactionId !in alreadyRecorded }
                .map { occurrence ->
                    val fireDate = occurrence.date.minus(DatePeriod(days = days))
                    ReminderNotification(
                        id = occurrence.transactionId,
                        recurringName = recurring.name,
                        dueDate = occurrence.date,
                        fireAt = fireDate.atTime(REMIND_AT).toInstant(timeZone),
                    )
                }
        }
        .filter { it.fireAt > now }
        .sortedBy { it.fireAt }
        .take(MAX_PENDING)

    /** Nội dung hiển thị: tên + ngày, không bao giờ có số tiền (R15). */
    fun body(reminder: ReminderNotification): String =
        "${reminder.recurringName} đến hạn ngày ${reminder.dueDate.day.pad()}/${(reminder.dueDate.month.ordinal + 1).pad()}"

    const val TITLE = "Khoản định kỳ sắp đến hạn"

    private fun Int.pad(): String = toString().padStart(2, '0')
}
