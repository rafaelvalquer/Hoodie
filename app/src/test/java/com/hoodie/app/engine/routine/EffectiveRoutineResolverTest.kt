package com.hoodie.app.engine.routine

import com.hoodie.app.core.model.*
import com.hoodie.app.domain.routine.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EffectiveRoutineResolverTest {
    private val date = LocalDate.of(2026, 10, 12)
    private val now = 1_800_000_000_000L
    private val manual = Routine(workMode = WorkMode.OFFICE)
    private fun pattern(type: RoutineEventType, minute: Int, group: String = "WEEKDAY", count: Int = 5,
        confidence: Float = .8f, at: Long = now) = RoutinePattern(group, type, minute, confidence, count, at)
    private fun resolve(auto: Set<RoutineEventType>, vararg patterns: RoutinePattern) =
        EffectiveRoutineResolver.resolve(date, now, manual, SleepSchedule(), auto, patterns.toList())
    @Test fun manualWinsAndMissingAutomaticFallsBackWithoutInventingPredictions() {
        val type = RoutineEventType.WORK_START
        assertEquals(manual.startMinute, resolve(emptySet(), pattern(type, 600)).routine.startMinute)
        val auto = resolve(RoutineEventType.entries.toSet())
        assertEquals(RoutineValueOrigin.FALLBACK, auto.values.getValue(type).origin)
        assertFalse(auto.values.containsKey(RoutineEventType.GYM_START))
    }
    @Test fun eligibleSpecificDayWinsOtherwiseEligibleGroupWins() {
        val type = RoutineEventType.WORK_START
        assertEquals(540, resolve(setOf(type), pattern(type, 530), pattern(type, 540, "MONDAY")).routine.startMinute)
        assertEquals(530, resolve(setOf(type), pattern(type, 530), pattern(type, 540, "MONDAY", count = 3)).routine.startMinute)
    }
    @Test fun expiredLowConfidenceAndFuturePatternsAreRejected() {
        val type = RoutineEventType.WORK_START
        for (p in listOf(pattern(type, 600, at = now - 29L * 86_400_000), pattern(type, 600, confidence = .59f), pattern(type, 600, at = now + 1)))
            assertEquals(manual.startMinute, resolve(setOf(type), p).routine.startMinute)
    }
    @Test fun invalidLearnedWorkAndLunchPairsFallBackTogether() {
        val all = RoutineEventType.entries.toSet()
        val work = resolve(all, pattern(RoutineEventType.WORK_START, 1100))
        assertEquals(manual.startMinute, work.routine.startMinute)
        assertEquals("INVALID_WORK_PAIR", work.values.getValue(RoutineEventType.WORK_END).fallbackReason)
        val lunch = resolve(all, pattern(RoutineEventType.LUNCH_START, 1000), pattern(RoutineEventType.LUNCH_END, 800))
        assertEquals(manual.lunchStartMinute, lunch.routine.lunchStartMinute)
        assertEquals(manual.lunchEndMinute, lunch.routine.lunchEndMinute)
    }
    @Test fun sleepMayCrossMidnightAndExceptionPreservesWorkConfiguration() {
        val result = EffectiveRoutineResolver.resolve(date, now, manual, SleepSchedule(), RoutineEventType.entries.toSet(),
            listOf(pattern(RoutineEventType.SLEEP, 1380), pattern(RoutineEventType.WAKE, 420)), dayOff = true)
        assertEquals(SleepSchedule(420, 1380), result.sleep)
        assertTrue(result.dayOff)
        assertEquals(manual.workMode, result.routine.workMode)
        assertEquals(manual.days, result.routine.days)
    }
}
