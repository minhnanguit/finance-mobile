package com.uit.finance.core.auth.client

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Tương tự `apiCall` của core/network, nhưng cho lỗi OAuth của Keycloak thay vì RFC 7807.
 * Mọi lỗi đều được log (không kèm token) — `AppError` ra ngoài không đủ để debug.
 */
internal suspend fun <T> oidcCall(logger: Logger, block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    logger.w(e) { "OIDC call thất bại: ${e::class.simpleName}: ${e.message}" }
    AppResult.Failure(e.toAppError())
}

private fun Exception.toAppError(): AppError = when (this) {
    is OidcException.Http -> toAppError()
    is OidcException -> AppError.Unknown(message)
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException, is IOException ->
        AppError.Network(message)
    is SerializationException -> AppError.Unknown("Malformed OIDC response: $message")
    else -> AppError.Unknown(message)
}

/** `invalid_grant` = code/refresh token đã chết (hết hạn, bị revoke, reuse) → session không cứu được. */
private fun OidcException.Http.toAppError(): AppError =
    if (error == "invalid_grant" || status == 401) {
        AppError.Unauthorized
    } else {
        AppError.Api(status = status, code = error, title = error, detail = description)
    }
