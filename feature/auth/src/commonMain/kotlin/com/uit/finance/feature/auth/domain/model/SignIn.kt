package com.uit.finance.feature.auth.domain.model

enum class SignInMode {
    SignIn,

    /** Mở thẳng form đăng ký của Keycloak. */
    SignUp,
}

sealed interface SignInResult {
    data class SignedIn(val session: Session) : SignInResult

    /** User tự đóng trang đăng nhập — không phải lỗi, không hiển thị gì. */
    data object Cancelled : SignInResult
}
