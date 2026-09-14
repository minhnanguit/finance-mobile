package com.mosaicglobal.finance.core.presentation.text

import com.mosaicglobal.finance.core.common.result.AppError

/**
 * Text that a screen can render without knowing where it came from.
 * `Key` entries are resolved by a string catalog once localisation lands; until then the
 * English fallback is used so state stays serialisable and testable.
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
    is AppError.Network -> UiText.Key("error.network", "No connection. Check your network and try again.")
    is AppError.Unauthorized -> UiText.Key("error.unauthorized", "Your session has expired. Please sign in again.")
    is AppError.Validation -> UiText.Dynamic(fieldErrors.firstOrNull()?.message ?: "Please check your input.")
    is AppError.Api -> {
        val detailText = detail
        val titleText = title
        when {
            fieldErrors.isNotEmpty() -> UiText.Dynamic(fieldErrors.joinToString("\n") { it.message })
            !detailText.isNullOrBlank() -> UiText.Dynamic(detailText)
            !titleText.isNullOrBlank() -> UiText.Dynamic(titleText)
            else -> UiText.Key("error.server", "The server rejected the request ($status).")
        }
    }
    is AppError.Storage -> UiText.Key("error.storage", "Could not access local storage.")
    is AppError.Unknown -> UiText.Key("error.unknown", "Something went wrong. Please try again.")
}
