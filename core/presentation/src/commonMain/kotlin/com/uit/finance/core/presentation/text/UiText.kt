package com.uit.finance.core.presentation.text

import com.uit.finance.core.common.result.AppError

/**
 * Text that a screen can render without knowing where it came from.
 * `Key` entries are resolved by a string catalog once localisation lands; until then the
 * Vietnamese fallback is used so state stays serialisable and testable.
 */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Key(val key: String, val fallback: String) : UiText

    fun resolve(): String = when (this) {
        is Dynamic -> value
        is Key -> fallback
    }

    companion object {
        val Empty: UiText = Dynamic("")
    }
}

fun String.asUiText(): UiText = UiText.Dynamic(this)

/** Single place that turns a domain error into user-facing copy. */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.Network -> UiText.Key("error.network", "Không có kết nối. Hãy kiểm tra mạng rồi thử lại.")
    is AppError.Unauthorized -> UiText.Key("error.unauthorized", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
    is AppError.Validation -> UiText.Dynamic(fieldErrors.firstOrNull()?.message ?: "Vui lòng kiểm tra thông tin đã nhập.")
    is AppError.Api -> {
        val detailText = detail
        val titleText = title
        when {
            fieldErrors.isNotEmpty() -> UiText.Dynamic(fieldErrors.joinToString("\n") { it.message })
            !detailText.isNullOrBlank() -> UiText.Dynamic(detailText)
            !titleText.isNullOrBlank() -> UiText.Dynamic(titleText)
            else -> UiText.Key("error.server", "Máy chủ không xử lý được yêu cầu ($status).")
        }
    }
    is AppError.Storage -> UiText.Key("error.storage", "Không thể truy cập dữ liệu trên thiết bị.")
    is AppError.Unknown -> UiText.Key("error.unknown", "Đã xảy ra lỗi. Vui lòng thử lại.")
}
