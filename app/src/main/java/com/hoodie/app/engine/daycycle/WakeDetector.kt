package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.daycycle.InferredSleepSpan
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason

class WakeDetector(private val sleepSpans: SleepSpanResolver = SleepSpanResolver()) {
    fun detect(
        evidence: List<WakeEvidence>,
        activityTimestamps: List<Long>,
        scheduledSleepAt: (Long) -> Long?,
    ): DetectedWake? {
        val actionable = evidence.filter { candidate ->
            candidate.type != WakeEvidenceType.HOODIE_WAKING_UP && candidate.confidenceScore >= 60 &&
                (candidate.type != WakeEvidenceType.PHONE_SUSTAINED || (candidate.durationMs ?: 0L) >= HoodieConfig.WAKE_PHONE_MIN_ACTIVE_MS)
        }
            .sortedWith(compareBy<WakeEvidence> { it.timestamp }.thenByDescending { it.confidenceScore })
        for (candidate in actionable) {
            val sleep = sleepSpans.resolveBefore(candidate.timestamp, activityTimestamps, scheduledSleepAt(candidate.timestamp))
            if (sleep == null && candidate.type !in setOf(WakeEvidenceType.MOBILITY_CONFIRMED, WakeEvidenceType.HOME_EXIT)) continue
            val corroborated = actionable.any { other ->
                other != candidate && other.type != candidate.type &&
                    kotlin.math.abs(other.timestamp - candidate.timestamp) <= HoodieConfig.WAKE_CORROBORATION_WINDOW_MS
            }
            val confidence = when {
                candidate.type == WakeEvidenceType.MOBILITY_CONFIRMED || candidate.type == WakeEvidenceType.HOME_EXIT -> WakeConfidence.HIGH
                corroborated -> WakeConfidence.HIGH
                else -> WakeConfidence.MEDIUM
            }
            return DetectedWake(candidate, confidence, sleep)
        }
        return null
    }

    companion object {
        fun reason(type: WakeEvidenceType): WakeReason = when (type) {
            WakeEvidenceType.PHONE_SUSTAINED -> WakeReason.PHONE_SUSTAINED
            WakeEvidenceType.MOBILITY_CONFIRMED -> WakeReason.MOBILITY_CONFIRMED
            WakeEvidenceType.HOME_EXIT -> WakeReason.HOME_EXIT
            WakeEvidenceType.CONTEXT_ACTIVITY -> WakeReason.CONTEXT_CHANGE
            WakeEvidenceType.SCREEN_SUSTAINED -> WakeReason.SCREEN_ACTIVITY
            WakeEvidenceType.HOODIE_WAKING_UP -> WakeReason.HOODIE_WAKE
        }
    }
}
