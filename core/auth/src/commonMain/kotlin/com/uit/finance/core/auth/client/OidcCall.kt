package com.uit.finance.core.auth.client

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** Tương tự `apiCall` của core/network, nhưng cho lỗi OAuth của Keycloak thay vì RFC 7807. */
internal suspend fun <T> oidcCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: OidcException.Http) {
    AppResult.Failure(e.toAppError())
} catch (e: OidcException) {
    // Sai cấu hình hoặc vi phạm bảo mật (issuer/state): chỉ để log, không hiển thị nguyên văn.
    AppResult.Failure(AppError.Unknown(e.message))
} catch (e: HttpRequestTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: ConnectTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: SocketTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: IOException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Unknown("Malformed OIDC response: ${e.message}"))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message))
}

/** `invalid_grant` = code/refresh token đã chết (hết hạn, bị revoke, reuse) → session không cứu được. */
private fun OidcException.Http.toAppError(): AppError =
    if (error == "invalid_grant" || status == 401) {
        AppError.Unauthorized
    } else {
        AppError.Api(status = status, code = error, title = error, detail = description)
    }
