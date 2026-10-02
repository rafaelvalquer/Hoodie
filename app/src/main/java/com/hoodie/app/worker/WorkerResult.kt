package com.hoodie.app.worker

import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException

/** Cancelamento pertence ao WorkManager; falha transitória pede nova tentativa. */
suspend inline fun runWorkerTask(
    onFailure: (Exception) -> Unit = {},
    block: suspend () -> Unit,
): ListenableWorker.Result = try {
    block()
    ListenableWorker.Result.success()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    onFailure(failure)
    ListenableWorker.Result.retry()
}
