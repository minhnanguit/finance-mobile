package com.uit.finance.core.network.api

import com.uit.finance.core.common.result.FieldError

/**
 * Non-2xx response, already parsed from RFC 7807 `application/problem+json` when the server sent one.
 * Only the network layer sees this type; [apiCall] converts it into `AppError`.
 */
class ApiException(
    val status: Int,
    val code: String?,
    val title: String?,
    val detail: String?,
    val fieldErrors: List<FieldError> = emptyList(),
    val traceId: String? = null,
) : RuntimeException(buildString {
    append("HTTP ").append(status)
    code?.let { append(" [").append(it).append(']') }
    title?.let { append(' ').append(it) }
    detail?.let { append(": ").append(it) }
})
