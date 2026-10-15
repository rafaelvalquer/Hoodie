package com.hoodie.app.integration

import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.debug.HomeVisualDiagnostics
import com.hoodie.app.core.location.*
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.integration.mobility.MobilityGraph
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.presentation.screens.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeContextReconciliationTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `real Home blocks before presentation and cancels an old context after manual correction`() = runBlocking {
        val graph = MobilityGraph()
        val g = graph.g
        var home: HomeViewModel? = null
        try {
            g.setRoutine(officeRoutine)
            g.settings.completeOnboarding("Hoodie", g.clock.millis)
            graph.at(MONDAY, 9)
            g.engine.onGeofence(graph.work.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER)
            val resolver = HoodieInteractionResolver(HoodieInteractionCatalog.parse(
                File("src/main/assets/metadata/interactive_dialogues.json").readText()), Random(1))
            val vm = HomeViewModel(g.hoodie, g.engine, g.settings, g.routines, g.places,
                LocationProvider(g.context, LocationPermissionManager(g.context)),
                DialogueEngine(DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText())),
                g.clock, g.log, graph.router, graph.engine, graph.repo, HomeVisualDiagnostics(), g.contextDao,
                g.db.questionDao(), interactions = dagger.Lazy { resolver }, interactionFeature = HoodieInteractionFeature(true),
                presentationFeatures = HomePresentationFeatures(context = true, coordinator = true))
            home = vm
            val collect = launch { vm.state.collect() }
            vm.onHomeStarted()
            val initial = withTimeout(10_000) { vm.state.first { !it.loading && it.presentedContext != null } }
            val machine = AnimationStateMachine(Random(1))
            machine.place(initial.visual!!, 1)
            val frame = machine.frame(2, 600, initial.period)!!
            assertFalse(initial.presentedContext!!.isReconciled)
            vm.onHoodieGesture(InteractionGesture.TAP, frame)
            assertNull(withTimeoutOrNull(100) { vm.events.filterIsInstance<HomeUiEvent.InteractionResponse>().first() })
            vm.onVisualPresented(initial.visual)
            withTimeout(10_000) { vm.state.first { it.presentedContext?.isReconciled == true } }
            val events = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000) { vm.events.filterIsInstance<HomeUiEvent.InteractionResponse>().first() }
            }
            vm.onHoodieGesture(InteractionGesture.TAP, frame)
            val request = events.await().request
            assertTrue(vm.interactionAllowed(request, frame))
            assertNull(vm.reactionPresentation.value)
            val oldId = initial.context!!.id
            g.clock.millis += 1_000
            g.engine.setManualPlace(PlaceType.RESTAURANT)
            val corrected = withTimeout(10_000) { vm.state.first { !it.loading && it.context?.id != oldId } }
            assertNotEquals(initial.presentedContext.visualRevision, corrected.presentedContext!!.visualRevision)
            assertFalse(vm.interactionAllowed(request, frame))
            assertFalse(vm.onInteractionStarted(request))
            assertNull(vm.reactionPresentation.value)
            vm.onHomeStopped()
            collect.cancelAndJoin()
        } finally {
            home?.onHomeStopped()
            home?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            graph.close()
        }
    }
}
