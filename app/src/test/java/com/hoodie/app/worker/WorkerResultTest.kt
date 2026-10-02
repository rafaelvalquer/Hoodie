package com.hoodie.app.worker

import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class WorkerResultTest {
    @Test fun successAndFailureReturnExpectedResults() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), runWorkerTask {})
        val failure = IllegalStateException("test")
        var logged: Exception? = null
        assertEquals(ListenableWorker.Result.retry(), runWorkerTask(onFailure = { logged = it }) { throw failure })
        assertSame(failure, logged)
    }

    @Test fun cancellationIsRethrownAndNotLoggedAsFailure() = runBlocking {
        val cancelled = CancellationException("cancelled")
        var logged = false
        try {
            runWorkerTask(onFailure = { logged = true }) { throw cancelled }
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertEquals(false, logged)
    }
}
