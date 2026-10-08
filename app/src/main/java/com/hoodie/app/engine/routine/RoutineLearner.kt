package com.hoodie.app.engine.routine

import com.hoodie.app.domain.detection.*
import com.hoodie.app.domain.routine.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

object RoutineLearner {
    fun learn(observations: List<RoutineObservation>, today: LocalDate): List<LearnedRoutineSlot> {
        val reliable = observations.filter {
            it.minute in 0..1439 && it.complete && !it.exception && !it.contradictory && it.confidence.value >= .60f &&
                ChronoUnit.DAYS.between(it.date, today) in 0L..27L
        }.groupBy { it.date to it.type }.values.mapNotNull { day ->
            // More than one contradictory event in a day does not become extra samples.
            if (day.map { it.minute }.distinct().size > 1) null else day.maxBy { it.source.priority }
        }
        val coarse = reliable.groupBy { routineDayGroup(it.date.dayOfWeek) to it.type }
        val fine = reliable.filter { routineDayGroup(it.date.dayOfWeek) == "WEEKDAY" }.groupBy { it.date.dayOfWeek.name to it.type }
        return (coarse.mapNotNull { (key, samples) -> slot(key.first, key.second, samples, today, 3) } +
            fine.mapNotNull { (key, samples) -> slot(key.first, key.second, samples, today, 4) }).sortedWith(compareBy({ it.dayGroup }, { it.type.ordinal }))
    }

    fun resolveMinute(manual: Int?, learned: LearnedRoutineSlot?): Int? = manual ?: learned?.takeIf { it.confidence.value >= .60f }?.medianMinute

    private fun slot(group: String, type: RoutineEventType, samples: List<RoutineObservation>, today: LocalDate, minimum: Int): LearnedRoutineSlot? {
        if (samples.size < minimum) return null
        val wrap = samples.maxOf { it.minute } - samples.minOf { it.minute } > 720
        val weighted = samples.map { observation ->
            val minute = if (wrap && observation.minute < 720) observation.minute + 1440 else observation.minute
            val boost = when (observation.source) { DetectionSource.USER_CORRECTION -> 3.0; DetectionSource.USER_CONFIRMATION -> 2.0; else -> 1.0 }
            minute to exp(-ChronoUnit.DAYS.between(observation.date, today).toDouble() / 14.0) * boost * observation.confidence.value
        }
        val median = weightedMedian(weighted)
        val deviation = weightedMedian(weighted.map { abs(it.first - median) to it.second })
        val confidence = ((weighted.sumOf { it.second } / 8.0).coerceAtMost(1.0) / (1.0 + deviation / 60.0)).coerceIn(0.0, .95).toFloat()
        return LearnedRoutineSlot(group, type, median % 1440, deviation, samples.size, ConfidenceScore(confidence))
    }

    private fun weightedMedian(values: List<Pair<Int, Double>>): Int {
        val half = values.sumOf { it.second } / 2
        var sum = 0.0
        return values.sortedBy { it.first }.first { sum += it.second; sum >= half }.first
    }
}
