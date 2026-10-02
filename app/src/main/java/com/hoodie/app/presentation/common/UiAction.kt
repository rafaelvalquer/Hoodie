package com.hoodie.app.presentation.common

import com.hoodie.app.core.error.AppError
import com.hoodie.app.core.error.appErrorOr
import kotlinx.coroutines.CancellationException

/** Recoverable UI operations report typed failures and preserve coroutine cancellation. */
suspend fun runUiAction(
    fallback: AppError,
    onFailure: suspend (AppError, Exception) -> Unit,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error.appErrorOr(fallback), error)
    }
}
