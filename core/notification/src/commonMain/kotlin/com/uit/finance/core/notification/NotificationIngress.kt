@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.notification

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Nơi nhận thông báo của app **khác** (thông báo ngân hàng, plan mục 3.3). `core/notification` giữ listener của nền
 * tảng (module duy nhất được đụng API thông báo); module nghiệp vụ (`core/capture`) implement port này và đăng ký vào
 * Koin. Chưa ai đăng ký thì listener không làm gì.
 *
 * Listener gọi theo thứ tự: [accepts] (chưa đọc nội dung) → [acceptsSender] (chỉ đọc tiêu đề) → [onPosted].
 */
interface NotificationIngress {
    /** Package này có nằm trong danh sách user cho phép không. Gọi trên main thread: phải nhanh, không cần sổ mở. */
    fun accepts(packageName: String): Boolean

    /** Với app Tin nhắn: tiêu đề (tên người gửi) có phải ngân hàng đã chọn không. App ngân hàng luôn `true`. */
    fun acceptsSender(packageName: String, title: String?): Boolean

    /** Đã qua hai bước trên. Chuỗi đã copy xong; việc nặng phải chạy trên scope riêng, không chặn listener. */
    fun onPosted(packageName: String, title: String?, text: String, postedAt: Instant)
}

/** Quyền đọc thông báo của app khác. iOS không có khái niệm này ([isSupported] = `false`). */
interface NotificationAccess {
    val isSupported: Boolean
    fun isGranted(): Boolean
    fun openSettings()

    /** Nhờ hệ thống nối lại listener (hãng máy hay tắt service chạy nền, plan L10). */
    fun requestRebind()
}
