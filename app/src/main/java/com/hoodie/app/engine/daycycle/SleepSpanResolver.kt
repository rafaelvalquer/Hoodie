package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.daycycle.InferredSleepSpan
import com.hoodie.app.domain.daycycle.SleepConfidence

/** Só infere sono após inatividade sustentada; o horário configurado fornece um limite individual. */
class SleepSpanResolver {
    fun resolveBefore(
        candidateAt: Long,
        activityTimestamps: List<Long>,
        scheduledSleepAt: Long?,
    ): InferredSleepSpan? {
        val lastActivity = activityTimestamps.asSequence().filter { it < candidateAt }.maxOrNull()
        val baseline = listOfNotNull(lastActivity, scheduledSleepAt?.takeIf { it < candidateAt }).maxOrNull()
            ?: return null
        val inactiveMs = candidateAt - baseline
        if (inactiveMs < HoodieConfig.SLEEP_INACTIVITY_MIN_MS) return null
        val start = (baseline + HoodieConfig.SLEEP_ONSET_GRACE_MS).coerceAtMost(candidateAt)
        val confidence = when {
            inactiveMs >= 6 * 60 * 60_000L && scheduledSleepAt != null -> SleepConfidence.HIGH
            scheduledSleepAt != null -> SleepConfidence.MEDIUM
            else -> SleepConfidence.LOW
        }
        return InferredSleepSpan(start, candidateAt, confidence)
    }
}
