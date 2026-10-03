package com.hoodie.app.presentation

import com.hoodie.app.presentation.common.runSystemAction
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SystemActionTest {
    @Test fun successExecutesOnce() {
        var calls = 0
        runSystemAction({ fail("Unexpected failure") }) { calls++ }
        assertEquals(1, calls)
    }
    @Test fun recoverableFailurePreservesCause() {
        val expected = IllegalStateException("Unavailable")
        var actual: Exception? = null
        runSystemAction({ actual = it }) { throw expected }
        assertSame(expected, actual)
    }
    @Test fun cancellationPropagates() {
        val expected = CancellationException("Cancelled")
        try {
            runSystemAction({ fail("Cancellation was handled as UI failure") }) { throw expected }
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) { assertSame(expected, actual) }
    }
    @Test fun fatalErrorPropagates() {
        val expected = AssertionError("Fatal")
        try {
            runSystemAction({ fail("Fatal error was handled as UI failure") }) { throw expected }
            fail("Fatal error was swallowed")
        } catch (actual: AssertionError) { assertSame(expected, actual) }
    }
}
