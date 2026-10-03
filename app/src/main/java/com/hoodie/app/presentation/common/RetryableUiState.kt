package com.hoodie.app.presentation.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/** A failed read stays visible until explicit retry; cancellation and fatal errors propagate. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> retryableUiState(
    retries: Flow<Long>,
    loading: T,
    onFailure: (Exception) -> T,
    source: () -> Flow<T>,
): Flow<T> = retries.flatMapLatest {
    flow { emitAll(source()) }
        .onStart { emit(loading) }
        .catch { cause ->
            if (cause is CancellationException) throw cause
            if (cause !is Exception) throw cause
            emit(onFailure(cause))
        }
}
