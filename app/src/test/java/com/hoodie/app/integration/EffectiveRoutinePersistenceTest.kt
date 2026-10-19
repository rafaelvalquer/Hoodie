package com.hoodie.app.integration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hoodie.app.core.database.LearnedRoutineSlotEntity
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.EffectiveRoutineRepository
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.domain.routine.RoutineValueOrigin
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A no-op forecast refresh must not replace IDs imported from a backup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EffectiveRoutinePersistenceTest {
    @Test fun unchangedForecastSurvivesRefreshAndRepositoryRecreationWithoutWrites() = runBlocking {
        val g = TestGraph(at(MONDAY, 10).ms())
        try {
            g.setRoutine(officeRoutine)
            val repository = EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock)
            repository.currentSnapshot()
            val before = g.db.effectiveRoutineDao().all()
            val sql = observeWrites(g)

            // A new repository models the worker/application boundary after restore.
            repeat(3) {
                EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock).afterLearning()
            }

            assertEquals(0, writeCount(sql))
            assertEquals(before, g.db.effectiveRoutineDao().all())
        } finally { g.close() }
    }

    @Test fun changedLearningUpdatesTomorrowWithTheSameIdAndKeepsTodayFrozen() = runBlocking {
        val g = TestGraph(at(MONDAY, 10).ms())
        try {
            g.setRoutine(officeRoutine)
            g.settings.setAutomaticRoutineEvents(setOf("WORK_START"))
            val repository = EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock)
            repository.currentSnapshot()
            val today = g.clock.today()
            val tomorrow = today.plusDays(1)
            val dao = g.db.effectiveRoutineDao()
            val todayBefore = dao.all().filter { it.epochDay == today.toEpochDay() }
            val forecastBefore = dao.at(tomorrow.toEpochDay(), Long.MAX_VALUE)!!
            g.db.intelligenceDao().replaceRoutineSlots(listOf(LearnedRoutineSlotEntity(
                dayGroup = "WEEKDAY", type = "WORK_START", medianMinute = 560,
                deviationMinutes = 5, sampleCount = 8, confidence = .9f, updatedAt = g.clock.millis,
            )))

            repository.afterLearning()

            val forecastAfter = dao.at(tomorrow.toEpochDay(), Long.MAX_VALUE)!!
            assertEquals(forecastBefore.id, forecastAfter.id)
            assertNotEquals(forecastBefore.snapshot, forecastAfter.snapshot)
            assertEquals(todayBefore, dao.all().filter { it.epochDay == today.toEpochDay() })
            assertEquals(officeRoutine.startMinute, repository.resolve(today, g.clock.millis).routine.startMinute)
            val learned = repository.resolve(tomorrow, forecastAfter.effectiveFrom)
            assertEquals(560, learned.routine.startMinute)
            assertEquals(RoutineValueOrigin.LEARNED, learned.values.getValue(RoutineEventType.WORK_START).origin)
        } finally { g.close() }
    }

    @Test fun timezoneChangeKeepsTheVersionAtTheOtherMidnight() = runBlocking {
        val g = TestGraph(at(MONDAY, 10).ms())
        try {
            g.setRoutine(officeRoutine)
            val utc = FixedClock(g.clock.millis, ZoneId.of("UTC"))
            EffectiveRoutineRepository(g.db, g.routines, g.settings, utc).currentSnapshot()
            val day = g.clock.today().plusDays(1)
            val before = g.db.effectiveRoutineDao().all().single { it.epochDay == day.toEpochDay() }

            EffectiveRoutineRepository(g.db, g.routines, g.settings, g.clock).afterLearning()

            val versions = g.db.effectiveRoutineDao().all().filter { it.epochDay == day.toEpochDay() }
            assertEquals(2, versions.size)
            assertTrue(before in versions)
            assertEquals(setOf(
                day.atStartOfDay(utc.zone()).toInstant().toEpochMilli(),
                day.atStartOfDay(g.clock.zone()).toInstant().toEpochMilli(),
            ), versions.map { it.effectiveFrom }.toSet())
        } finally { g.close() }
    }

    private fun observeWrites(g: TestGraph): SupportSQLiteDatabase =
        g.db.openHelper.writableDatabase.also { sql ->
            sql.execSQL("CREATE TEMP TABLE qa_routine_version_writes (count INTEGER NOT NULL)")
            sql.execSQL("INSERT INTO qa_routine_version_writes VALUES (0)")
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                sql.execSQL("CREATE TEMP TRIGGER qa_routine_$operation AFTER $operation ON effective_routine_versions BEGIN UPDATE qa_routine_version_writes SET count = count + 1; END")
            }
        }

    private fun writeCount(sql: SupportSQLiteDatabase) =
        sql.query("SELECT count FROM qa_routine_version_writes").use { it.moveToFirst(); it.getInt(0) }
}
