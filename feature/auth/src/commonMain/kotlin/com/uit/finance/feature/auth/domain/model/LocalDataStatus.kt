package com.uit.finance.feature.auth.domain.model

/**
 * Sổ trên máy của phiên đang đăng nhập đã dùng được chưa (S6). Lần đầu đăng nhập phải hỏi server `userId` mới mở
 * được sổ, nên mất mạng lúc đó là chưa vào app được; các lần sau `userId` đã lưu, offline vẫn dùng bình thường.
 */
enum class LocalDataStatus {
    /** Chưa đăng nhập hoặc đang mở sổ. */
    Preparing,
    Ready,

    /** Lần đầu đăng nhập mà không có mạng: cần mạng một lần để tải sổ. */
    NeedsNetwork,

    /** Không mở được sổ vì lý do khác (máy chủ lỗi, bộ nhớ máy lỗi): thử lại được. */
    Unavailable,
}
