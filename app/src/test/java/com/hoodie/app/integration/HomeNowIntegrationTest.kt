package com.hoodie.app.integration

import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.daystate.DayStateCoordinator
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.integration.mobility.MobilityGraph
import com.hoodie.app.presentation.screens.home.HomeViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeNowIntegrationTest {
    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    @Test fun contextEngineFlowsThroughHomeViewModelAndCorrectionsReachDiary() = runBlocking {
        val graph = MobilityGraph()
        val g = graph.g
        try {
            g.setRoutine(officeRoutine)
            g.settings.completeOnboarding("Hoodie", g.clock.millis)
            graph.at(MONDAY, 9)
            g.engine.onGeofence(graph.work.id, com.hoodie.app.engine.context.GeofenceTransition.ENTER)
            val canonical = g.contextDao.current()!!

            val context = g.context
            val dialogue = DialogueEngine(DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText()))
            val vm = HomeViewModel(
                hoodie = g.hoodie,
                contextEngine = g.engine,
                settingsRepo = g.settings,
                routines = g.routines,
                places = g.places,
                location = LocationProvider(context, LocationPermissionManager(context)),
                dialogues = dialogue,
                clock = g.clock,
                log = g.log,
                router = graph.router,
                mobility = graph.engine,
                mobilityRepo = graph.repo,
                contextDao = g.contextDao,
                questionDao = g.db.questionDao(),
                dayStates = dagger.Lazy { DayStateCoordinator(g.db, g.settings, g.clock) },
            )

            vm.onHomeStarted()
            val ui = withTimeout(10_000) { vm.state.first { !it.loading && it.context?.id == canonical.id } }
            val homeNow = withTimeout(10_000) { vm.homeNow.filterNotNull().first { it.contextEventId == canonical.id } }
            assertEquals(canonical.id, ui.context?.id)
            assertEquals(UserContextType.WORK, homeNow.context)
            assertEquals(canonical.confidence, homeNow.contextConfidence?.value)
            assertNotNull(homeNow.contextStartedAt)

            vm.onHomeStopped()
            vm.setManual(PlaceType.RESTAURANT)
            val correctedUi = withTimeout(10_000) { vm.state.first { it.context?.type == UserContextType.DINING } }
            assertEquals(UserContextType.DINING, correctedUi.context?.type)
            assertEquals(null, vm.homeNow.value)
            vm.onHomeStarted()
            val corrected = withTimeout(10_000) { vm.homeNow.filterNotNull().first { it.context == UserContextType.DINING } }
            assertNotEquals(canonical.id, corrected.contextEventId)
            graph.at(MONDAY, 9, 1) // Let the corrected context become a non-zero diary interval.
            val diary = DiaryRepository(g.contextDao, g.db.timelineDao(), g.db.hoodieActivityDao(), g.db.placeDao(), g.clock,
                mobility = MobilityRepository(g.db.mobilitySessionDao(), g.db.mobilitySegmentDao())).loadDiary(g.clock.today())
            assertEquals(UserContextType.DINING, g.contextDao.current()!!.type)
            assertTrue("Diary timeline: ${diary.timeline}", diary.timeline.any { it.relatedContext == UserContextType.DINING })
            assertTrue("Diary visits: ${diary.visits}", diary.visits.any { it.placeId == graph.restaurant.id && it.placeType == PlaceType.RESTAURANT })
        } finally {
            graph.close()
        }
    }
}
