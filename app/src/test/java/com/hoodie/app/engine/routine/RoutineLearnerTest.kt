package com.hoodie.app.engine.routine

import com.hoodie.app.domain.detection.*
import com.hoodie.app.domain.routine.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RoutineLearnerTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun observation(days: Long, minute: Int, source: DetectionSource = DetectionSource.SENSOR) =
        RoutineObservation(today.minusDays(days), RoutineEventType.WAKE, minute, ConfidenceScore(.9f), source)

    @Test fun weightedMedianAndMadResistAnOutlier() {
        val ages = listOf(0L, 1L, 2L, 5L, 6L)
        val learned = RoutineLearner.learn(listOf(405, 410, 407, 411, 638).mapIndexed { i, minute -> observation(ages[i], minute) }, today)
        val slot = learned.single { it.dayGroup == "WEEKDAY" }
        assertTrue(slot.medianMinute in 405..411)
        assertTrue(slot.deviationMinutes <= 6)
    }

    @Test fun correctionsHaveMoreWeightThanOldSensorHistory() {
        val old = listOf(9L, 10L, 11L, 12L, 15L, 16L).map { observation(it, 400) }
        val corrected = listOf(0L, 1L, 2L).map { observation(it, 430, DetectionSource.USER_CORRECTION) }
        val slot = RoutineLearner.learn(old + corrected, today).single { it.dayGroup == "WEEKDAY" }
        assertEquals(430, slot.medianMinute)
        assertTrue(slot.confidence.value >= .85f)
        assertEquals(480, RoutineLearner.resolveMinute(480, slot))
        assertEquals(430, RoutineLearner.resolveMinute(null, slot))
    }

    @Test fun weekendPatternsAndExceptionsDoNotContaminateWeekdays() {
        val weekday = listOf(0L, 1L, 2L).map { observation(it, 420) }
        val saturday = listOf(4L, 11L, 18L).map { observation(it, 600) }
        val excluded = listOf(observation(28, 900), observation(-1, 900), observation(7, 900).copy(exception = true),
            observation(8, 900).copy(complete = false), observation(9, 900).copy(contradictory = true), observation(10, 900).copy(confidence = ConfidenceScore(.4f)))
        val slots = RoutineLearner.learn(weekday + saturday + excluded, today)
        assertEquals(420, slots.single { it.dayGroup == "WEEKDAY" }.medianMinute)
        assertEquals(600, slots.single { it.dayGroup == "SATURDAY" }.medianMinute)
        assertEquals(3, slots.single { it.dayGroup == "WEEKDAY" }.sampleCount)
    }

    @Test fun sleepAroundMidnightUsesCircularMinutes() {
        val samples = listOf(1435, 5, 0).mapIndexed { i, minute -> observation(i.toLong(), minute).copy(type = RoutineEventType.SLEEP) }
        val slot = RoutineLearner.learn(samples, today).single { it.dayGroup == "WEEKDAY" }
        assertTrue(slot.medianMinute < 10 || slot.medianMinute > 1430)
        assertTrue(slot.deviationMinutes <= 10)
    }
}
