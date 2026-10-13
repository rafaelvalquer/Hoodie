package com.hoodie.app.integration

import com.hoodie.app.core.database.LearnedRoutineSlotEntity
import com.hoodie.app.core.model.*
import com.hoodie.app.data.repository.EffectiveRoutineRepository
import com.hoodie.app.domain.routine.*
import com.hoodie.app.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EffectiveRoutineRepositoryTest {
    @Test fun learningStartsTomorrowAndManualEditsHaveAnAuditableInstant() = runBlocking {
        val g = TestGraph(at(MONDAY, 10).ms())
        try {
            g.setRoutine(officeRoutine)
            val repo = EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock)
            val today = g.clock.today(); val initialAt = g.clock.millis
            val initial = repo.currentSnapshot()
            g.clock.millis += 60_000
            repo.saveManual(officeRoutine, SleepSchedule(), setOf("WORK_START"))
            repo.beforeLearning()
            g.db.intelligenceDao().replaceRoutineSlots(listOf(LearnedRoutineSlotEntity(dayGroup = "WEEKDAY", type = "WORK_START",
                medianMinute = 560, deviationMinutes = 5, sampleCount = 8, confidence = .9f, updatedAt = g.clock.millis)))
            repo.afterLearning()
            assertEquals(officeRoutine.startMinute, repo.resolve(today, g.clock.millis).routine.startMinute)
            g.clock.millis = today.plusDays(1).atStartOfDay(g.clock.zone()).toInstant().toEpochMilli()
            val tomorrow = repo.currentSnapshot()
            assertEquals(560, tomorrow.routine.startMinute)
            assertEquals(RoutineValueOrigin.LEARNED, tomorrow.values.getValue(RoutineEventType.WORK_START).origin)
            val learnedAt = g.clock.millis
            g.clock.millis += 60_000
            repo.saveManual(officeRoutine.copy(startMinute = 600), SleepSchedule(), emptySet())
            assertEquals(600, repo.resolve(today.plusDays(1), g.clock.millis).routine.startMinute)
            assertEquals(600, repo.resolve(today.plusDays(5), g.clock.millis + 4 * 86_400_000L).routine.startMinute)
            assertEquals(560, repo.resolve(today.plusDays(1), learnedAt).routine.startMinute)
            assertEquals(initial.routine, repo.resolve(today, initialAt - 1).routine)
            assertEquals(officeRoutine.startMinute, repo.resolve(today.minusDays(1), initialAt - 86_400_000).routine.startMinute)
        } finally { g.close() }
    }

    @Test fun observedFlowUpdatesAfterSaveAndDayExceptionWins() = runBlocking {
        val g = TestGraph(at(MONDAY, 10).ms())
        try {
            g.setRoutine(officeRoutine)
            val repo = EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock)
            repo.currentSnapshot()
            val observing = async { withTimeout(10_000) { repo.current.first { it.routine.startMinute == 600 } } }
            g.clock.millis += 1_000
            repo.saveManual(officeRoutine.copy(startMinute = 600), SleepSchedule(), emptySet())
            assertEquals(600, observing.await().routine.startMinute)
            g.routines.setDayOff(g.clock.today(), true, g.clock.millis)
            assertTrue(repo.currentSnapshot().dayOff)
        } finally { g.close() }
    }
}
