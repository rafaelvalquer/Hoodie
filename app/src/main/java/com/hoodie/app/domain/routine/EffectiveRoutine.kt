package com.hoodie.app.domain.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import java.time.LocalDate

enum class RoutineValueOrigin { MANUAL, LEARNED, FALLBACK }
data class ResolvedRoutineValue(val minute: Int, val origin: RoutineValueOrigin,
    val confidence: Float? = null, val sampleCount: Int = 0, val fallbackReason: String? = null,
    val learnedMinute: Int? = null, val deviationMinutes: Int? = null, val shiftLimited: Boolean = false)
data class RoutinePattern(val dayGroup: String, val type: RoutineEventType, val minute: Int,
    val confidence: Float, val sampleCount: Int, val updatedAt: Long, val deviationMinutes: Int = 0)
data class EffectiveRoutineSnapshot(val date: LocalDate, val effectiveFrom: Long, val routine: Routine,
    val sleep: SleepSchedule, val values: Map<RoutineEventType, ResolvedRoutineValue>, val dayOff: Boolean = false,
    val manualRoutine: Routine = routine, val manualSleep: SleepSchedule = sleep)

/** Pure resolution; manual settings and learned predictions never become observations. */
object EffectiveRoutineResolver {
    const val MAX_SHIFT_MINUTES = 90
    const val MAX_DEVIATION_MINUTES = 45

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
            val eligible = patterns.filter { it.type == type && it.confidence >= .60f &&
                it.deviationMinutes <= MAX_DEVIATION_MINUTES && it.minute in 0..1439 &&
                asOf - it.updatedAt in 0..(28L * 86_400_000) &&
                it.sampleCount >= if (it.dayGroup in setOf("WEEKDAY", "SATURDAY", "SUNDAY")) 3 else 4 }
            val pattern = eligible.firstOrNull { it.dayGroup == date.dayOfWeek.name }
                ?: eligible.firstOrNull { it.dayGroup == routineDayGroup(date.dayOfWeek) }
            if (pattern != null) {
                if (reference == null) values[type] = ResolvedRoutineValue(pattern.minute, RoutineValueOrigin.LEARNED,
                    pattern.confidence, pattern.sampleCount, learnedMinute = pattern.minute,
                    deviationMinutes = pattern.deviationMinutes)
                else {
                    val delta = circularDelta(reference, pattern.minute)
                    val limitedDelta = delta.coerceIn(-MAX_SHIFT_MINUTES, MAX_SHIFT_MINUTES)
                    values[type] = ResolvedRoutineValue(wrap(reference + limitedDelta), RoutineValueOrigin.LEARNED,
                        pattern.confidence, pattern.sampleCount, learnedMinute = pattern.minute,
                        deviationMinutes = pattern.deviationMinutes, shiftLimited = limitedDelta != delta)
                }
            }
            else if (reference != null) values[type] = ResolvedRoutineValue(reference, RoutineValueOrigin.FALLBACK, fallbackReason = "NO_ELIGIBLE_PATTERN")
        }
        fun minute(type: RoutineEventType) = values.getValue(type).minute
        fun resetPair(a: RoutineEventType, b: RoutineEventType, reason: String) {
            for (type in listOf(a, b)) values[type] = ResolvedRoutineValue(references.getValue(type), RoutineValueOrigin.FALLBACK, fallbackReason = reason)
        }
        if (minute(RoutineEventType.WORK_START) >= minute(RoutineEventType.WORK_END))
            resetPair(RoutineEventType.WORK_START, RoutineEventType.WORK_END, "INVALID_WORK_PAIR")
        val lunchStart = minute(RoutineEventType.LUNCH_START)
        val lunchEnd = minute(RoutineEventType.LUNCH_END)
        val workStart = minute(RoutineEventType.WORK_START)
        val workEnd = minute(RoutineEventType.WORK_END)
        if (lunchStart >= lunchEnd || lunchEnd - lunchStart < MIN_LUNCH_MINUTES ||
            manual.hasWork && (lunchStart <= workStart || lunchEnd >= workEnd)) {
            resetPair(RoutineEventType.LUNCH_START, RoutineEventType.LUNCH_END, "INVALID_LUNCH_PAIR")
            if (manual.hasWork && (manual.lunchStartMinute <= minute(RoutineEventType.WORK_START) ||
                    manual.lunchEndMinute >= minute(RoutineEventType.WORK_END)))
                resetPair(RoutineEventType.WORK_START, RoutineEventType.WORK_END, "INCOMPATIBLE_LUNCH")
        }
        val sleepDuration = (minute(RoutineEventType.WAKE) - minute(RoutineEventType.SLEEP) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        if (sleepDuration !in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES) {
            values[RoutineEventType.WAKE] = ResolvedRoutineValue(sleep.wakeMinute, RoutineValueOrigin.FALLBACK,
                fallbackReason = "INVALID_SLEEP_DURATION")
            values[RoutineEventType.SLEEP] = ResolvedRoutineValue(sleep.sleepMinute, RoutineValueOrigin.FALLBACK,
                fallbackReason = "INVALID_SLEEP_DURATION")
        }
        if (manual.hasWork && date.dayOfWeek in manual.days && minute(RoutineEventType.WAKE) >= minute(RoutineEventType.WORK_START))
            values[RoutineEventType.WAKE] = ResolvedRoutineValue(sleep.wakeMinute, RoutineValueOrigin.FALLBACK,
                fallbackReason = "WAKE_AFTER_WORK_START")
        return EffectiveRoutineSnapshot(date, asOf, manual.copy(startMinute = minute(RoutineEventType.WORK_START),
            endMinute = minute(RoutineEventType.WORK_END), lunchStartMinute = minute(RoutineEventType.LUNCH_START),
            lunchEndMinute = minute(RoutineEventType.LUNCH_END)),
            sleep.copy(wakeMinute = minute(RoutineEventType.WAKE), sleepMinute = minute(RoutineEventType.SLEEP)), values, dayOff, manual, sleep)
    }

    fun circularDelta(manualMinute: Int, learnedMinute: Int): Int {
        val delta = (learnedMinute - manualMinute) % MINUTES_PER_DAY
        return when { delta > MINUTES_PER_DAY / 2 -> delta - MINUTES_PER_DAY
            delta <= -MINUTES_PER_DAY / 2 -> delta + MINUTES_PER_DAY
            else -> delta }
    }

    private fun wrap(minute: Int) = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    private const val MINUTES_PER_DAY = 1440
    private const val MIN_LUNCH_MINUTES = 15
    private const val MIN_SLEEP_MINUTES = 4 * 60
    private const val MAX_SLEEP_MINUTES = 12 * 60
}
