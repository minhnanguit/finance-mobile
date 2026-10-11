package com.uit.finance.core.sync.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Thời gian chờ trước khi gửi lại một op server chưa nhận: 15 giây, nhân đôi mỗi lần, tối đa 1 giờ.
 *
 * Không có "bỏ hẳn sau N lần": `RETRY` nghĩa là *thử lại sau* (ví/danh mục cha chưa tới, server lỗi tạm,
 * hai máy sửa cùng lúc). Dừng hẳn op thì một sự cố server vài phút cũng làm thay đổi của user không bao giờ
 * lên được server. Op thử mãi không qua chỉ tốn tối đa một request mỗi giờ, và UI thấy nó là "kẹt"
 * ([STUCK_AFTER_ATTEMPTS]) để báo user.
 */
internal object PushBackoff {
    private val BASE = 15.seconds
    private val MAX = 1.hours
    private const val MAX_DOUBLINGS = 20 // 15s × 2^20 đã vượt xa MAX; chặn để phép nhân không tràn

    /** Chờ bao lâu sau lần thất bại thứ [attempt] (bắt đầu từ 1). */
    fun delayAfter(attempt: Int): Duration {
        val doublings = (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
        return (BASE * (1L shl doublings).toDouble()).coerceAtMost(MAX)
    }
}

/** Từ lần thất bại này trở đi, op được coi là kẹt và UI nên báo cho user. */
const val STUCK_AFTER_ATTEMPTS: Int = 5
