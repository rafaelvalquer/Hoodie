package com.hoodie.app.permissions

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks real process visibility across suspension, resumption and activity recreation. */
@RunWith(AndroidJUnit4::class)
class RoutineLifecycleTest {
    @Test fun suspensionResumptionAndRecreationKeepRoutineAndVisibilityConsistent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as com.hoodie.app.HoodieApp
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(app.processForeground.isVisible)
            val before = app.effectiveRoutine.get().currentSnapshot()
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(app.processForeground.isVisible)
            val background = app.effectiveRoutine.get().currentSnapshot()
            assertEquals(before, background)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(app.processForeground.isVisible)
            scenario.recreate()
            assertTrue(app.processForeground.isVisible)
            assertEquals(before, app.effectiveRoutine.get().currentSnapshot())
        }
    }
}
