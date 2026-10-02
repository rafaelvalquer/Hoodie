package com.hoodie.app.presentation

import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UiActionTest {
    @Test fun successfulActionDoesNotEmitFailure() = runTest {
        var performed = false
        runUiAction(DatabaseError.WriteFailed, { _, _ -> fail("Unexpected failure") }) { performed = true }
        assertTrue(performed)
    }

    @Test fun unclassifiedExceptionUsesTypedFallbackAndPreservesDiagnosticCause() = runTest {
        val cause = IllegalStateException("internal diagnostic")
        var actual: AppError? = null
        var diagnostic: Exception? = null
        runUiAction(DatabaseError.WriteFailed, { error, exception -> actual = error; diagnostic = exception }) { throw cause }
        assertEquals(DatabaseError.WriteFailed, actual)
        assertSame(cause, diagnostic)
    }

    @Test fun placeNotFoundPreservesIdWithoutUsingExceptionTextForPresentation() = runTest {
        var actual: AppError? = null
        runUiAction(DatabaseError.WriteFailed, { error, _ -> actual = error }) { throw PlaceException.NotFound(42) }
        assertEquals(PlaceError.NotFound(42), actual)
    }

    @Test fun cancellationPropagatesWithoutEmittingFailure() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            runUiAction(DatabaseError.WriteFailed, { _, _ -> fail("Cancellation is not a UI error") }) { throw cancellation }
            fail("Cancellation swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun fatalErrorsAreNotConvertedToRecoverableMessages() = runTest {
        val fatal = AssertionError("fatal")
        try {
            runUiAction(DatabaseError.WriteFailed, { _, _ -> fail("Fatal failure swallowed") }) { throw fatal }
            fail("Error swallowed")
        } catch (actual: AssertionError) {
            assertSame(fatal, actual)
        }
    }
}
