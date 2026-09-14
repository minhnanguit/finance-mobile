package com.mosaicglobal.finance.core.network.api

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * The only place where transport/HTTP exceptions become [AppError]s.
 * Data-layer repositories wrap every remote call with this.
 */
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    AppResult.Failure(e.toAppError())
} catch (e: HttpRequestTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: ConnectTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: SocketTimeoutException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: IOException) {
    AppResult.Failure(AppError.Network(e.message))
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Unknown("Malformed response: ${e.message}"))
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message))
}

fun ApiException.toAppError(): AppError = when (status) {
    HttpStatusCode.Unauthorized.value -> AppError.Unauthorized
    else -> AppError.Api(
        status = status,
        code = code,
        title = title,
        detail = detail,
        fieldErrors = fieldErrors,
    )
}
