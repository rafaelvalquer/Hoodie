package com.hoodie.app.integration

import androidx.lifecycle.viewModelScope
import androidx.room.InvalidationTracker
import com.hoodie.app.core.debug.HomeVisualDiagnostics
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.integration.mobility.MobilityGraph
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.presentation.screens.home.HomeUiEvent
import com.hoodie.app.presentation.screens.home.HomeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Real Home ViewModel and Room graph; gestures must not invalidate any persisted table. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HoodieInteractionHomeIntegrationTest {
    @Before fun setupDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }

    @Test fun enabledGestureProducesOneSequenceAndNeverWritesDiaryOrOtherTables() = verify(true)
    @Test fun disabledGateEmitsNoInteractionAndNeverWritesTables() = verify(false)

    private fun verify(enabled: Boolean) = runBlocking {
        val graph = MobilityGraph()
        val g = graph.g
        var vm: HomeViewModel? = null
        try {
            g.setRoutine(officeRoutine)
            g.settings.completeOnboarding("Hoodie", g.clock.millis)
            graph.at(MONDAY, 9)
            g.engine.onGeofence(graph.work.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER)
            val canonical = g.contextDao.current()!!
            val resolver = HoodieInteractionResolver(HoodieInteractionCatalog.parse(File("src/main/assets/metadata/interactive_dialogues.json").readText()), Random(1))
            val home = HomeViewModel(
                hoodie = g.hoodie, contextEngine = g.engine, settingsRepo = g.settings, routines = g.routines,
                places = g.places, location = LocationProvider(g.context, LocationPermissionManager(g.context)),
                dialogues = DialogueEngine(DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText())),
                clock = g.clock, log = g.log, router = graph.router, mobility = graph.engine, mobilityRepo = graph.repo,
                visualDiagnostics = HomeVisualDiagnostics(), contextDao = g.contextDao, questionDao = g.db.questionDao(),
                interactions = dagger.Lazy { resolver }, interactionFeature = HoodieInteractionFeature(enabled),
            )
            vm = home
            home.onHomeStarted()
            withTimeout(10_000) { home.homeNow.filterNotNull().first { it.contextEventId == canonical.id } }
            val ui = withTimeout(10_000) { home.state.first { !it.loading && it.snapshot != null && it.context?.id == canonical.id } }
            val machine = AnimationStateMachine(Random(1))
            machine.place(ui.visual!!, 1)
            val frame = machine.frame(2, 600, ui.period)!!
            val database = g.db.openHelper.readableDatabase
            val tables = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name != 'android_metadata'").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            fun persistedRows() = tables.associateWith { table ->
                database.query("SELECT * FROM \"$table\"").use { cursor ->
                    buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "<null>" }) }.sorted()
                }
            }
            g.db.invalidationTracker.refreshVersionsSync()
            val invalidated = ConcurrentHashMap.newKeySet<String>()
            val observer = object : InvalidationTracker.Observer(tables.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) { invalidated.addAll(tables) }
            }
            g.db.invalidationTracker.addObserver(observer)
            val before = persistedRows()
            try {
                val received = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(500) { home.events.filterIsInstance<HomeUiEvent.InteractionResponse>().first() }
                }
                home.onHoodieGesture(InteractionGesture.TAP, frame)
                home.onHoodieGesture(InteractionGesture.TAP, frame)
                val request = received.await()?.request
                if (enabled) {
                    assertNotNull(request)
                    assertTrue(home.interactionAllowed(request!!, frame))
                    assertEquals(canonical.type, request.context!!.userContext)
                    assertEquals(frame.occupancy.animation, request.context.animation)
                    assertTrue(request.response.animations.isNotEmpty())
                    assertTrue(home.onInteractionStarted(request))
                    assertFalse(home.onInteractionStarted(request))
                    home.onInteractionCancelled()
                    assertFalse(home.isInteractionCurrent(request))
                    home.onInteractionFinished(request)
                    assertNull(withTimeoutOrNull(100) { home.events.filterIsInstance<HomeUiEvent.InteractionResponse>().first() })
                } else assertNull(request)
                g.db.invalidationTracker.refreshVersionsSync()
                assertEquals(before, persistedRows())
                assertTrue("Interaction invalidated $invalidated", invalidated.isEmpty())
            } finally { g.db.invalidationTracker.removeObserver(observer) }
        } finally {
            vm?.onHomeStopped()
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            graph.close()
        }
    }
}
