package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.phoneinsights.model.AppSession

/** Junta usos próximos de apps sem depender dos eventos brutos do Android. */
object PhoneWakeEvidenceBuilder {
    fun build(sessions: List<AppSession>, from: Long, until: Long): List<WakeEvidence> {
        val clipped = sessions.mapNotNull { session ->
            val start = maxOf(session.startedAt, from)
            val end = minOf(session.endedAt, until)
            if (end <= start) null else start until end
        }.sortedBy { it.first }
        if (clipped.isEmpty()) return emptyList()

        val candidates = mutableListOf<WakeEvidence>()
        clipped.forEachIndexed { index, interval ->
            val windowEnd = interval.first + HoodieConfig.WAKE_PHONE_WINDOW_MS
            val activeMs = unionDuration(clipped.drop(index).takeWhile { it.first < windowEnd }, interval.first, windowEnd)
            if (activeMs >= HoodieConfig.WAKE_PHONE_MIN_ACTIVE_MS) {
                candidates += WakeEvidence(interval.first, WakeEvidenceType.PHONE_SUSTAINED, WakeDetectionPolicy.PHONE_SCORE, activeMs)
            }
        }
        // Uma sequência de apps gera uma evidência no começo da janela, não uma por app.
        return candidates.sortedBy { it.timestamp }.fold(mutableListOf()) { result, candidate ->
            val previous = result.lastOrNull()
            if (previous == null || candidate.timestamp - previous.timestamp >= HoodieConfig.WAKE_PHONE_WINDOW_MS) {
                result += candidate
            } else if ((candidate.durationMs ?: 0) > (previous.durationMs ?: 0)) {
                result[result.lastIndex] = previous.copy(durationMs = candidate.durationMs)
            }
            result
        }
    }

    private fun unionDuration(intervals: List<LongRange>, from: Long, until: Long): Long {
        var total = 0L
        var openStart: Long? = null
        var openEnd = Long.MIN_VALUE
        for (interval in intervals) {
            val start = maxOf(interval.first, from)
            val end = minOf(interval.last + 1, until)
            if (end <= start) continue
            if (openStart == null) {
                openStart = start
                openEnd = end
            } else if (start <= openEnd) {
                openEnd = maxOf(openEnd, end)
            } else {
                total += openEnd - openStart
                openStart = start
                openEnd = end
            }
        }
        if (openStart != null) total += openEnd - openStart
        return total
    }
}
