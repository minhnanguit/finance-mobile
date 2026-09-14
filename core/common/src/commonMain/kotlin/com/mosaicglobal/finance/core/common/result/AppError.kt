package com.mosaicglobal.finance.core.common.result

/**
 * Every failure that can cross a layer boundary. Exceptions never leave the data layer;
 * they are translated into one of these values.
 */
sealed interface AppError {

    /** No connectivity, DNS failure, connect/read timeout. */
    data class Network(val message: String? = null) : AppError

    /** The server answered with an RFC 7807 problem (any non-2xx other than 401). */
    data class Api(
        val status: Int,
        val code: String?,
        val title: String?,
        val detail: String?,
        val fieldErrors: List<FieldError> = emptyList(),
    ) : AppError

    /** Access token rejected and the refresh token could not rotate it. The session is gone. */
    data object Unauthorized : AppError

    /** Input rejected before it ever reached the network. */
    data class Validation(val fieldErrors: List<FieldError>) : AppError {
        constructor(field: String, message: String) : this(listOf(FieldError(field, message)))
    }

    /** Local persistence (SQLDelight, secure storage) failed. */
    data class Storage(val message: String? = null) : AppError

    /** Anything else. Message is for logs, never shown verbatim to the user. */
    data class Unknown(val message: String? = null) : AppError
}

data class FieldError(val field: String, val message: String)
