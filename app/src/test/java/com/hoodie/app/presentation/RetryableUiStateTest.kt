package com.hoodie.app.presentation

import com.hoodie.app.presentation.common.retryableUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryableUiStateTest {
    @Test fun emitsLoadingThenContent() = runTest {
        assertEquals(listOf("loading", "content"), retryableUiState(flowOf(0L), "loading", { "error" }) { flowOf("content") }.toList())
    }
    @Test fun sourceFactoryFailureReportsOriginalCause() = runTest {
        val expected = IllegalStateException("Database unavailable")
        var actual: Exception? = null
        val result = retryableUiState(flowOf(0L), "loading", { actual = it; "error" }) { throw expected }.toList()
        assertEquals(listOf("loading", "error"), result)
        assertSame(expected, actual)
    }
    @Test fun explicitRetryResubscribesAndRecovers() = runTest {
        val retries = MutableStateFlow(0L)
        var calls = 0
        val seen = mutableListOf<String>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            retryableUiState(retries, "loading", { "error" }) { flow { calls++; if (calls == 1) throw IllegalStateException(); emit("content") } }.collect { seen += it }
        }
        runCurrent()
        assertEquals(listOf("loading", "error"), seen)
        retries.value++
        runCurrent()
        assertEquals(listOf("loading", "error", "loading", "content"), seen)
        assertEquals(2, calls)
        job.cancel()
    }
    @Test fun cancellationPropagatesWithoutUiFailure() = runTest {
        var reportedFailure = false
        var collectedCancellation = false
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            try {
                retryableUiState(flowOf(0L), "loading", { reportedFailure = true; "error" }) {
                    flow<String> { awaitCancellation() }
                }.collect { }
            } catch (actual: CancellationException) {
                collectedCancellation = true
                throw actual
            }
        }
        runCurrent()
        job.cancel(CancellationException("Cancelled"))
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(collectedCancellation)
        assertFalse(reportedFailure)
    }
    @Test fun fatalErrorPropagatesWithoutUiFailure() = runTest {
        val expected = AssertionError("Fatal")
        try {
            retryableUiState(flowOf(0L), "loading", { fail("Fatal error became UI failure"); "error" }) { flow<String> { throw expected } }.toList()
            fail("Fatal error swallowed")
        } catch (actual: AssertionError) {
            assertEquals(expected.javaClass, actual.javaClass)
            assertEquals(expected.message, actual.message)
            assertTrue(actual === expected || generateSequence(actual.cause) { it.cause }.any { it === expected })
        }
    }
}
