@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.domain.model

import com.uit.finance.core.common.id.uuidV5
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Id tính ra từ nội dung biến động (plan mục 2.3): cùng một biến động → cùng id ở mọi máy, mọi nguồn (thông báo app
 * và SMS). Ledger ghi bằng op `CREATE` (chỉ ghi khi id chưa có), nên tin đến lại hay máy thứ hai nhận cùng tin đều
 * không sinh trùng; user đã "Bỏ qua" (tombstone) cũng không hồi sinh (D5, D7).
 */
object CaptureIds {
    /** Cố định, **không bao giờ đổi**: đổi là mọi id đổi theo và sinh trùng hàng loạt (D4). */
    const val NAMESPACE = "5b1f3c7e-8a42-4d19-9c6e-2f0b7d4a1e93"

    /**
     * Có số dư sau: dùng **ngày** (hai nguồn hay ghi giờ lệch nhau, số dư đã đủ phân biệt, D2). Không có số dư: giờ
     * cắt tới phút. Không đưa ngân hàng/nguồn vào (D1). Tiền tố `v1|` để sau này đổi format có version.
     */
    fun fingerprint(event: ParsedBankEvent, occurredOn: LocalDate, occurredAtMinute: String): String = buildString {
        append("v1|").append(event.direction).append('|').append(event.amountMinor).append('|')
        if (event.balanceAfterMinor != null) {
            append(event.balanceAfterMinor).append('|').append(event.accountHint.orEmpty()).append('|').append(occurredOn)
        } else {
            append(event.accountHint.orEmpty()).append('|').append(occurredAtMinute)
        }
    }

    /** `userId` trong id: hai tài khoản trên cùng máy không đụng nhau (D3). */
    fun transactionId(userId: String, fingerprint: String): String = uuidV5(NAMESPACE, "${userId.lowercase()}:$fingerprint")

    /** Id cho tin không đọc được: theo nguyên văn + phút nhận, để tin đến lại không thành hai dòng. */
    fun unparsedId(userId: String, bank: BankId?, text: String, receivedAt: Instant, zone: TimeZone): String =
        uuidV5(NAMESPACE, "${userId.lowercase()}:raw:${bank?.name.orEmpty()}:$text:${minuteOf(receivedAt, zone)}")

    fun minuteOf(instant: Instant, zone: TimeZone): String = instant.toLocalDateTime(zone).let {
        "${it.date}T${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
    }
}
