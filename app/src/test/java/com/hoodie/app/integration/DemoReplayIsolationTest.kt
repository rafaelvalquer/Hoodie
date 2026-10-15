package com.hoodie.app.integration

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.hoodie.app.engine.demo.DemoReplayMode
import com.hoodie.app.presentation.screens.demo.DemoReplayViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real in-memory replay against a fully counted Room fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DemoReplayIsolationTest {
    private lateinit var graph: TestGraph
    private val zone = java.time.ZoneId.of("America/Sao_Paulo")

    @Before fun setUp() { graph = TestGraph(1_790_000_000_000L) }
    @After fun tearDown() { graph.close() }

    private fun allTableCounts(): Map<String, Long> {
        val db = graph.db.openHelper.readableDatabase
        val names = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return names.associateWith { name ->
            val quoted = name.replace("\"", "\"\"")
            db.query("SELECT COUNT(*) FROM \"$quoted\"").use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        }
    }

    @Test fun `navigation preview and lifecycle actions do not write to Room or notify`() {
        val before = allTableCounts()
        val saved = SavedStateHandle(mapOf(DemoReplayViewModel.DATE to "2026-10-13", DemoReplayViewModel.ZONE to zone.id))
        val vm = DemoReplayViewModel(saved)
        val store = ViewModelStore().apply { put("demo", vm) }
        vm.onVisible()
        vm.setMode(DemoReplayMode.FULL_DAY)
        vm.setPreview(true)
        vm.seek(vm.timeline.day.activities[4].startedAt)
        vm.previous()
        vm.next()
        vm.setPreview(false)
        vm.onHidden()
        vm.exit()
        store.clear()

        assertEquals(before, allTableCounts())
        assertTrue(graph.notifier.events.isEmpty() && graph.notifier.questions.isEmpty())
    }
}
