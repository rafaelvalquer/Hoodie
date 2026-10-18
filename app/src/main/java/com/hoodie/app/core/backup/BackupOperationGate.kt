package com.hoodie.app.core.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One backup operation per process; operational entry points also honor DatabaseGate's suspension. */
@Singleton class BackupOperationGate @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}
