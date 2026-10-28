package com.dosprint.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): AppError? = (this as? Outcome.Failure)?.error

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) {
        action(value)
    }
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) {
        action(error)
    }
    return this
}

inline fun <T> runCatchingOutcome(crossinline block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (security: SecurityException) {
    AppLog.warn(AppLog.throwableTag("security", security))
    Outcome.Failure(AppError.PermissionDenied)
} catch (outOfMemory: OutOfMemoryError) {
    AppLog.error("Out of memory", outOfMemory)
    Outcome.Failure(AppError.OutOfMemory)
} catch (error: Exception) {
    AppLog.error(AppLog.throwableTag("runCatchingOutcome", error))
    Outcome.Failure(AppError.Unknown(error))
}

fun errorFrom(throwable: Throwable): AppError = when (throwable) {
    is SecurityException -> AppError.PermissionDenied
    is OutOfMemoryError -> AppError.OutOfMemory
    else -> AppError.Unknown(throwable)
}

fun <T> Flow<T>.asOutcome(): Flow<Outcome<T>> = this
    .map<T, Outcome<T>> { Outcome.Success(it) }
    .catch { throwable ->
        if (throwable is CancellationException) {
            throw throwable
        }
        AppLog.error(AppLog.throwableTag("flowFailure", throwable))
        emit(Outcome.Failure(errorFrom(throwable)))
    }

fun <T> Flow<T>.logEmissions(label: String): Flow<T> = onEach { value ->
    AppLog.debug("$label emitted $value")
}
