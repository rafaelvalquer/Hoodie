package com.hoodie.app.domain.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import java.time.LocalDate

enum class RoutineValueOrigin { MANUAL, LEARNED, FALLBACK }
data class ResolvedRoutineValue(val minute: Int, val origin: RoutineValueOrigin,
    val confidence: Float? = null, val sampleCount: Int = 0, val fallbackReason: String? = null)
data class RoutinePattern(val dayGroup: String, val type: RoutineEventType, val minute: Int,
    val confidence: Float, val sampleCount: Int, val updatedAt: Long)
data class EffectiveRoutineSnapshot(val date: LocalDate, val effectiveFrom: Long, val routine: Routine,
    val sleep: SleepSchedule, val values: Map<RoutineEventType, ResolvedRoutineValue>, val dayOff: Boolean = false,
    val manualRoutine: Routine = routine, val manualSleep: SleepSchedule = sleep)

/** Pure resolution; manual settings and learned predictions never become observations. */
object EffectiveRoutineResolver {
    fun resolve(date: LocalDate, asOf: Long, manual: Routine, sleep: SleepSchedule,
        automatic: Set<RoutineEventType>, patterns: List<RoutinePattern>, dayOff: Boolean = false): EffectiveRoutineSnapshot {
        val references = mapOf(RoutineEventType.WAKE to sleep.wakeMinute, RoutineEventType.SLEEP to sleep.sleepMinute,
            RoutineEventType.WORK_START to manual.startMinute, RoutineEventType.WORK_END to manual.endMinute,
            RoutineEventType.LUNCH_START to manual.lunchStartMinute, RoutineEventType.LUNCH_END to manual.lunchEndMinute)
        val values = mutableMapOf<RoutineEventType, ResolvedRoutineValue>()
        for (type in RoutineEventType.entries) {
            val reference = references[type]
            if (type !in automatic) {
                if (reference != null) values[type] = ResolvedRoutineValue(reference, RoutineValueOrigin.MANUAL)
                continue
            }
            val eligible = patterns.filter { it.type == type && it.confidence >= .60f && it.minute in 0..1439 &&
                asOf - it.updatedAt in 0..(28L * 86_400_000) &&
                it.sampleCount >= if (it.dayGroup in setOf("WEEKDAY", "SATURDAY", "SUNDAY")) 3 else 4 }
            val pattern = eligible.firstOrNull { it.dayGroup == date.dayOfWeek.name }
                ?: eligible.firstOrNull { it.dayGroup == routineDayGroup(date.dayOfWeek) }
            if (pattern != null) values[type] = ResolvedRoutineValue(pattern.minute, RoutineValueOrigin.LEARNED, pattern.confidence, pattern.sampleCount)
            else if (reference != null) values[type] = ResolvedRoutineValue(reference, RoutineValueOrigin.FALLBACK, fallbackReason = "NO_ELIGIBLE_PATTERN")
        }
        fun minute(type: RoutineEventType) = values.getValue(type).minute
        fun resetPair(a: RoutineEventType, b: RoutineEventType, reason: String) {
            for (type in listOf(a, b)) values[type] = ResolvedRoutineValue(references.getValue(type), RoutineValueOrigin.FALLBACK, fallbackReason = reason)
        }
        if (minute(RoutineEventType.WORK_START) >= minute(RoutineEventType.WORK_END))
            resetPair(RoutineEventType.WORK_START, RoutineEventType.WORK_END, "INVALID_WORK_PAIR")
        if (minute(RoutineEventType.LUNCH_START) >= minute(RoutineEventType.LUNCH_END) ||
            manual.hasWork && (minute(RoutineEventType.LUNCH_START) < minute(RoutineEventType.WORK_START) ||
                minute(RoutineEventType.LUNCH_END) > minute(RoutineEventType.WORK_END))) {
            resetPair(RoutineEventType.LUNCH_START, RoutineEventType.LUNCH_END, "INVALID_LUNCH_PAIR")
            if (manual.hasWork && (manual.lunchStartMinute < minute(RoutineEventType.WORK_START) || manual.lunchEndMinute > minute(RoutineEventType.WORK_END)))
                resetPair(RoutineEventType.WORK_START, RoutineEventType.WORK_END, "INCOMPATIBLE_LUNCH")
        }
        return EffectiveRoutineSnapshot(date, asOf, manual.copy(startMinute = minute(RoutineEventType.WORK_START),
            endMinute = minute(RoutineEventType.WORK_END), lunchStartMinute = minute(RoutineEventType.LUNCH_START),
            lunchEndMinute = minute(RoutineEventType.LUNCH_END)),
            sleep.copy(wakeMinute = minute(RoutineEventType.WAKE), sleepMinute = minute(RoutineEventType.SLEEP)), values, dayOff, manual, sleep)
    }
}
