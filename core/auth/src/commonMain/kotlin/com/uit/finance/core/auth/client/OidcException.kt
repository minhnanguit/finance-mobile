package com.uit.finance.core.auth.client

/** Lỗi nội bộ của module; ra khỏi module luôn ở dạng `AppError` qua [oidcCall]. */
internal sealed class OidcException(message: String) : Exception(message) {

    class Http(val status: Int, val error: String?, val description: String?) :
        OidcException("HTTP $status${error?.let { " [$it]" }.orEmpty()}${description?.let { ": $it" }.orEmpty()}")

    class IssuerMismatch(expected: String, actual: String?) :
        OidcException("issuer không khớp: cấu hình '$expected', IdP trả '$actual'")

    /** `state` trả về không khớp — có thể là CSRF, dừng ngay. */
    class StateMismatch : OidcException("state trong callback không khớp")

    class InvalidCallback(reason: String) : OidcException("callback không hợp lệ: $reason")

    class InvalidResponse(reason: String) : OidcException("response không hợp lệ: $reason")

    class LaunchFailed(reason: String?) : OidcException("không mở được browser: ${reason.orEmpty()}")
}
