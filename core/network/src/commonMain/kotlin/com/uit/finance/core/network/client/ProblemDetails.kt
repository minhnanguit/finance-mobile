package com.uit.finance.core.network.client

import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.network.api.ApiException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** RFC 7807 body as defined by `#/components/schemas/Problem`. */
@Serializable
internal data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val traceId: String? = null,
    val errors: List<FieldErrorDto> = emptyList(),
)

@Serializable
internal data class FieldErrorDto(
    @SerialName("field") val field: String,
    @SerialName("message") val message: String,
)

private val ProblemJson = ContentType("application", "problem+json")

/** Turns a non-2xx response into an [ApiException], tolerating bodies that are not problem+json. */
internal suspend fun HttpResponse.toApiException(json: Json): ApiException {
    val bodyText = runCatching { bodyAsText() }.getOrDefault("")
    val contentType = contentType()
    val looksLikeProblem = contentType?.match(ProblemJson) == true ||
        contentType?.match(ContentType.Application.Json) == true ||
        bodyText.trimStart().startsWith("{")
    val problem = if (looksLikeProblem && bodyText.isNotBlank()) {
        runCatching { json.decodeFromString(ProblemDetailsDto.serializer(), bodyText) }.getOrNull()
    } else {
        null
    }
    return ApiException(
        status = status.value,
        code = problem?.code,
        title = problem?.title ?: status.description,
        detail = problem?.detail,
        fieldErrors = problem?.errors.orEmpty().map { FieldError(it.field, it.message) },
        traceId = problem?.traceId,
    )
}
