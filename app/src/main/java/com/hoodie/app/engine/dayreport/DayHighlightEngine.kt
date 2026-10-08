package com.hoodie.app.engine.dayreport

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.dayreport.*
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.core.config.HoodieConfig
import java.time.Instant
import java.time.ZoneId

class DayHighlightEngine(
    private val minSamples: Int = HoodieConfig.DAY_HIGHLIGHT_MIN_SAMPLES,
    private val minRoutineConfidence: Float = HoodieConfig.DAY_HIGHLIGHT_MIN_CONFIDENCE,
    private val significantDifferenceMinutes: Int = HoodieConfig.DAY_HIGHLIGHT_SIGNIFICANT_MINUTES,
) {
    fun select(diary: DailyDiary, routine: List<LearnedRoutineSlot>, zone: ZoneId): DayHighlight? {
        if (diary.isException) return null
        val gym = diary.visits.filter { it.placeType == PlaceType.GYM }.maxByOrNull { it.durationMs }
        if (gym != null && gym.durationMs >= 20 * 60_000L && (gym.confidence ?: 0f) >= .70f &&
            gym.source !in setOf(com.hoodie.app.core.model.ContextSource.ROUTINE, com.hoodie.app.core.model.ContextSource.ONBOARDING)) return DayHighlight(
            DayHighlightType.GYM, "day_report_highlight_gym", listOf((gym.durationMs / 60_000L).toString()),
            ConfidenceScore.fromPoints(90), "${gym.placeId}:${gym.arrivalAt}")
        val homeReturn = diary.visits.lastOrNull { it.placeType == PlaceType.HOME && it.arrivalAt in diary.activityWindow.civilStartAt until diary.activityWindow.civilEndAt }
            ?: return null
        val dayGroup = com.hoodie.app.domain.routine.routineDayGroup(diary.summary.date.dayOfWeek)
        val learned = routine.firstOrNull { it.type == RoutineEventType.HOME_RETURN && it.dayGroup == dayGroup }
            ?.takeIf { it.sampleCount >= minSamples && it.confidence.value >= minRoutineConfidence }
            ?: return null
        if (diary.activityWindow.provisional || homeReturn.confidence == null || homeReturn.confidence < minRoutineConfidence ||
            homeReturn.source == com.hoodie.app.core.model.ContextSource.ROUTINE ||
            homeReturn.source == com.hoodie.app.core.model.ContextSource.ONBOARDING) return null
        val actualMinute = Instant.ofEpochMilli(homeReturn.arrivalAt).atZone(zone).let { it.hour * 60 + it.minute }
        val delta = learned.medianMinute - actualMinute
        if (kotlin.math.abs(delta) < significantDifferenceMinutes) return null
        val earlier = delta > 0
        return DayHighlight(if (earlier) DayHighlightType.HOME_RETURN_EARLIER else DayHighlightType.HOME_RETURN_LATER,
            if (earlier) "day_report_highlight_return_early" else "day_report_highlight_return_late",
            listOf(kotlin.math.abs(delta).toString()), ConfidenceScore(minOf(homeReturn.confidence, learned.confidence.value)), "${homeReturn.placeId}:${homeReturn.arrivalAt}")
    }
}
