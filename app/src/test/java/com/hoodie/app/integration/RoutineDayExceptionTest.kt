package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.DayExceptionEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.domain.dayreport.DayReportStatus
import com.hoodie.app.engine.dayreport.DayReportAssembler
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.SATURDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RoutineDayExceptionTest {
    private lateinit var graph: TestGraph
    private val date = LocalDate.of(2026, 10, 5)

    @Before fun setUp() {
        graph = TestGraph(at(MONDAY, 9).ms())
    }

    @After fun tearDown() = graph.close()

    @Test fun activatingDayOffIsObservedImmediately() = runBlocking {
        assertFalse(graph.routines.isDayOff(date))
        graph.routines.setDayOff(date, true, graph.clock.millis)
        assertTrue(graph.routines.isDayOff(date))
        assertTrue(graph.routines.observeDayOff(date).first())
    }

    @Test fun disablingDayOffRestoresScheduledDay() = runBlocking {
        graph.routines.setDayOff(date, true, graph.clock.millis)
        graph.routines.setDayOff(date, false, graph.clock.millis + 1)
        assertFalse(graph.routines.isDayOff(date))
        assertFalse(graph.routines.observeDayOff(date).first())
    }

    @Test fun settingTrueTwiceIsIdempotent() = runBlocking {
        graph.routines.setDayOff(date, true, graph.clock.millis)
        graph.routines.setDayOff(date, true, graph.clock.millis + 1)
        assertTrue(graph.routines.isDayOff(date))
        assertEquals("DAY_OFF", graph.db.dayExceptionDao().get(date.toEpochDay())?.kind)
    }

    @Test fun exceptionIsScopedToItsCivilDate() = runBlocking {
        val saturday = LocalDate.of(2026, 10, 10)
        assertEquals(SATURDAY, saturday.dayOfMonth)
        graph.routines.setDayOff(date, true, graph.clock.millis)
        assertTrue(graph.routines.isDayOff(date))
        assertFalse(graph.routines.isDayOff(saturday))
    }

    @Test fun otherExceptionKindsAreNotReportedOrDeletedAsDayOff() = runBlocking {
        val saturday = LocalDate.of(2026, 10, 10)
        graph.db.dayExceptionDao().upsert(DayExceptionEntity(saturday.toEpochDay(), "VACATION", graph.clock.millis))

        assertFalse(graph.routines.isDayOff(saturday))
        assertFalse(graph.routines.observeDayOff(saturday).first())
        assertTrue(graph.routines.daysOff(saturday, saturday).isEmpty())
        graph.routines.setDayOff(saturday, false, graph.clock.millis)
        assertEquals("VACATION", graph.db.dayExceptionDao().get(saturday.toEpochDay())?.kind)
    }

    @Test fun markingOffPreservesWorkContextAndDiaryMetrics() = runBlocking {
        graph.setRoutine(officeRoutine)
        val workStart = at(MONDAY, 8).ms()
        val workEnd = at(MONDAY, 12).ms()
        graph.db.contextEventDao().insert(
            ContextEventEntity(
                type = UserContextType.WORK,
                startedAt = workStart,
                endedAt = workEnd,
                confidence = 1f,
                placeId = null,
                source = ContextSource.USER_CORRECTION,
            ),
        )
        graph.clock.millis = at(MONDAY, 12).ms()
        val diaryRepository = DiaryRepository(
            graph.contextDao,
            graph.db.timelineDao(),
            graph.db.hoodieActivityDao(),
            graph.db.placeDao(),
            graph.clock,
            dayExceptions = graph.db.dayExceptionDao(),
        )
        val before = diaryRepository.loadDiary(date)
        val beforeEvent = graph.contextDao.all().single()
        val beforeReport = DayReportAssembler().assemble(before, emptyList(), DayReportStatus.READY, graph.clock.millis, graph.clock.zone())

        graph.routines.setDayOff(date, true, at(MONDAY, 9).ms())

        val after = diaryRepository.loadDiary(date)
        val afterEvent = graph.contextDao.all().single()
        val afterReport = DayReportAssembler().assemble(after, emptyList(), DayReportStatus.READY, graph.clock.millis, graph.clock.zone())
        assertTrue(after.isException)
        assertEquals(beforeEvent, afterEvent)
        assertEquals(4 * 60 * 60_000L, before.summary.workMs)
        assertEquals(before.summary.workMs, after.summary.workMs)
        assertEquals(beforeReport.workMs, afterReport.workMs)
        assertEquals(4 * 60 * 60_000L, afterReport.workMs)
        assertTrue(afterReport.journey.any { it.type == PlaceType.WORK })
        assertNull("A day-off exception is not a representative routine comparison", afterReport.highlight)
    }
}
