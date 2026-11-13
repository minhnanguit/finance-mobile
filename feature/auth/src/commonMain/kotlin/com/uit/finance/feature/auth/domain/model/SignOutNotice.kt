package com.uit.finance.feature.auth.domain.model

/** Lý do phiên vừa kết thúc mà user không tự bấm đăng xuất; màn đăng nhập báo một lần. */
enum class SignOutNotice {
    /** Tài khoản đã bị xoá (trên máy này hoặc máy khác); dữ liệu trên máy đã được xoá theo (ADR-008 A9). */
    AccountDeleted,
}
