package com.mosaicglobal.finance.feature.auth.domain.validation

import com.mosaicglobal.finance.core.common.result.FieldError

/** Mirrors the constraints of the OpenAPI schemas so obviously bad input never hits the network. */
internal object CredentialRules {
    const val FIELD_EMAIL = "email"
    const val FIELD_PASSWORD = "password"
    const val FIELD_DISPLAY_NAME = "displayName"

    private const val EMAIL_MAX = 254
    private const val PASSWORD_MIN = 8
    private const val PASSWORD_MAX = 72
    private const val DISPLAY_NAME_MAX = 100

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): FieldError? = when {
        email.isBlank() -> FieldError(FIELD_EMAIL, "Email is required.")
        email.length > EMAIL_MAX || !emailRegex.matches(email) -> FieldError(FIELD_EMAIL, "Enter a valid email address.")
        else -> null
    }

    fun validateLoginPassword(password: String): FieldError? = when {
        password.isEmpty() -> FieldError(FIELD_PASSWORD, "Password is required.")
        password.length > PASSWORD_MAX -> FieldError(FIELD_PASSWORD, "Password is too long.")
        else -> null
    }

    fun validateNewPassword(password: String): FieldError? = when {
        password.length < PASSWORD_MIN -> FieldError(FIELD_PASSWORD, "Password must be at least $PASSWORD_MIN characters.")
        password.length > PASSWORD_MAX -> FieldError(FIELD_PASSWORD, "Password must be at most $PASSWORD_MAX characters.")
        else -> null
    }

    fun validateDisplayName(displayName: String): FieldError? = when {
        displayName.isBlank() -> FieldError(FIELD_DISPLAY_NAME, "Display name is required.")
        displayName.length > DISPLAY_NAME_MAX -> FieldError(FIELD_DISPLAY_NAME, "Display name is too long.")
        else -> null
    }
}
