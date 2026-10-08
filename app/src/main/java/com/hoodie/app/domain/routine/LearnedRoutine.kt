package com.hoodie.app.domain.routine

import com.hoodie.app.domain.detection.*
import java.time.DayOfWeek
import java.time.LocalDate

enum class RoutineEventType { WAKE, LEAVE_HOME, WORK_START, LUNCH_START, LUNCH_END, WORK_END, GYM_START, HOME_RETURN, SLEEP }

fun routineDayGroup(day: DayOfWeek): String = when (day) {
    DayOfWeek.SATURDAY -> "SATURDAY"
    DayOfWeek.SUNDAY -> "SUNDAY"
    else -> "WEEKDAY"
}

data class RoutineObservation(
    val date: LocalDate, val type: RoutineEventType, val minute: Int,
    val confidence: ConfidenceScore, val source: DetectionSource,
    val complete: Boolean = true, val exception: Boolean = false, val contradictory: Boolean = false,
)

data class LearnedRoutineSlot(val dayGroup: String, val type: RoutineEventType, val medianMinute: Int,
    val deviationMinutes: Int, val sampleCount: Int, val confidence: ConfidenceScore)
