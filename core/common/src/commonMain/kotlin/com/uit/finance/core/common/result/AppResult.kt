package com.uit.finance.core.common.result

/**
 * Railway-style result used across all layers instead of exceptions.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

val <T> AppResult<T>.isSuccess: Boolean get() = this is AppResult.Success
val <T> AppResult<T>.isFailure: Boolean get() = this is AppResult.Failure

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)
fun AppError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.mapError(transform: (AppError) -> AppError): AppResult<T> = when (this) {
    is AppResult.Success -> this
    is AppResult.Failure -> AppResult.Failure(transform(error))
}

inline fun <T, R> AppResult<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> @UnsafeVariance T): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fallback(error)
}
