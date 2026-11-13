@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.settings.domain.model

import com.uit.finance.core.common.text.foldForSearch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Hồ sơ hiển thị ở đầu màn Cài đặt. Có ngay từ token đang giữ (dùng được khi offline), rồi được thay bằng bản
 * của server khi có mạng — lúc đó mới biết [memberSince].
 */
data class AccountProfile(
    val displayName: String,
    val email: String?,
    val memberSince: Instant?,
)

/** Kết quả của lần đổi mật khẩu mà không phải lỗi mạng/server. */
sealed interface PasswordChange {
    /** Đã đổi; phiên trên máy đã chuyển sang token mới nên không bị đăng xuất. */
    data object Changed : PasswordChange

    /** User huỷ trên form hoặc đóng trình duyệt. */
    data object Cancelled : PasswordChange

    /** Trình duyệt đang đăng nhập tài khoản khác: mật khẩu của tài khoản này giữ nguyên. */
    data object DifferentAccount : PasswordChange
}

/** Kết quả của lần thử xoá tài khoản mà không phải lỗi mạng/server. */
sealed interface AccountDeletion {
    /** Server đã nhận yêu cầu; dữ liệu trên máy đã được xoá. */
    data object Deleted : AccountDeletion

    /** User tự đóng trang đăng nhập lại: không xoá gì. */
    data object Cancelled : AccountDeletion

    /** User đăng nhập lại bằng tài khoản khác: không xoá gì, phiên của tài khoản kia đã bị huỷ. */
    data object DifferentAccount : AccountDeletion

    /** Server thấy lần đăng nhập lại đã quá 5 phút (A2): phải xác nhận lại. */
    data object ConfirmationExpired : AccountDeletion

    /**
     * Mất kết nối (hoặc server lỗi) đúng lúc gửi yêu cầu xoá, hỏi lại cũng không được: chưa rõ server đã xoá chưa. Thử lại
     * an toàn — đã xoá thì server trả `user_deleted` và app tự dọn.
     */
    data object Unconfirmed : AccountDeletion
}

/**
 * Chữ phải gõ để xác nhận xoá tài khoản (A3). Nhận cả không dấu ("XOA") và mọi kiểu gõ dấu (NFC/NFD), không phân
 * biệt hoa thường: bàn phím không có tiếng Việt vẫn xác nhận được.
 */
object DeletionConfirmation {
    const val WORD: String = "XOÁ"

    fun matches(input: String): Boolean = input.foldForSearch().trim() == WORD.foldForSearch()
}

/**
 * Thông tin về bản app, do `composeApp` cung cấp: version theo nền tảng và link chính sách quyền riêng tư — App Store
 * 5.1.1(i) và Google Play đòi xem được ngay trong app.
 */
data class SettingsAppInfo(
    val versionName: String,
    val privacyPolicyUrl: String,
)
