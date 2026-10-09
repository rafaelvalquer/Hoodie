package com.hoodie.app.integration

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.data.repository.toDomain
import com.hoodie.app.domain.dayreport.DayReportStatus
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.SUNDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.engine.dayreport.DayReportAssembler
import com.hoodie.app.engine.daystate.DayStateCoordinator
import com.hoodie.app.engine.home.HomeNowAssembler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayOffContextIntegrationTest {
    private lateinit var graph: TestGraph
    private val date = LocalDate.of(2026, 10, 5)
    private val zone = java.time.ZoneId.of("America/Sao_Paulo")

    @Before fun setUp() = runBlocking {
        graph = TestGraph(at(SUNDAY - 7, 20).ms())
        graph.setRoutine(officeRoutine)
        graph.settings.completeOnboarding("Hoodie", graph.clock.millis)
        // Keep the initial HOME context before the Monday scenario date.
        graph.clock.millis = at(MONDAY - 1, 20).ms()
        graph.engine.savePlaceHere(PlaceType.HOME, "Casa", -23.55, -46.63)
        Unit
    }

    @After fun tearDown() = graph.close()

    @Test fun dayOffChangesOnlyRoutineExpectationWhileWorkIsObserved() = runBlocking {
        val home = graph.places.all().first { it.type == PlaceType.HOME }
        val work = graph.addPlace(PlaceType.WORK, -23.60)
        graph.clock.millis = at(MONDAY, 7, 30).ms()
        graph.engine.onGeofence(home.id, GeofenceTransition.EXIT)
        graph.clock.millis = at(MONDAY, 8, 50).ms()
        graph.engine.onGeofence(work.id, GeofenceTransition.ENTER, at(MONDAY, 8, 5).ms())
        val before = graph.contextDao.current()!!
        assertEquals(UserContextType.WORK, before.type)
        assertEquals(at(MONDAY, 8, 5).ms(), before.startedAt)

        graph.clock.millis = at(MONDAY, 9, 1).ms()
        graph.routines.setDayOff(date, true, graph.clock.millis)
        graph.clock.millis = at(MONDAY, 9, 2).ms()

        val afterMarking = graph.contextDao.current()!!
        assertEquals(before, afterMarking)
        val observedDayState = DayStateCoordinator(graph.db, graph.settings, graph.clock).reconcile()
        assertEquals(DayState.ACTIVE, observedDayState?.state)
        val resolved = graph.hoodie.resolve()
        assertEquals(UserContextType.WORK, resolved.state.userContext)
        assertEquals(ContextSource.GEOFENCE, afterMarking.source)
        val homeNow = HomeNowAssembler.assemble(observedDayState, afterMarking.toDomain(), "Escritório", null, null, graph.clock.millis)
        assertEquals(UserContextType.WORK, homeNow.context)

        val eventCountAtWork = graph.contextDao.all().size
        val diaryRepository = DiaryRepository(
            graph.contextDao,
            graph.db.timelineDao(),
            graph.db.hoodieActivityDao(),
            graph.db.placeDao(),
            graph.clock,
            dayExceptions = graph.db.dayExceptionDao(),
        )
        val diary = diaryRepository.loadDiary(date)
        val report = DayReportAssembler().assemble(diary, emptyList(), DayReportStatus.LIVE, graph.clock.millis, zone)
        assertTrue(diary.isException)
        assertTrue("Work since 08:05 remains in the day report", requireNotNull(report.workMs) > 0)
        assertTrue(report.journey.any { it.type == PlaceType.WORK })
        assertNull(report.highlight)

        graph.routines.setDayOff(date, false, graph.clock.nowMillis())
        assertTrue(!graph.routines.isDayOff(date))
        assertEquals(before, graph.contextDao.current())
        assertEquals(eventCountAtWork, graph.contextDao.all().size)
        assertEquals(UserContextType.WORK, graph.hoodie.resolve().state.userContext)
    }
}
