package com.hoodie.app.engine.detection

import com.hoodie.app.domain.detection.*

data class DetectionQuestion(val candidate: String, val askedAt: Long, val transportSessionId: Long? = null, val mode: String? = null)

object QuestionPolicy {
    const val SAME_CANDIDATE_COOLDOWN_MS = 30 * 60_000L

    fun canAsk(
        result: DetectionResult,
        candidate: String,
        now: Long,
        recent: List<DetectionQuestion>,
        relevant: Boolean = true,
        transportSessionId: Long? = null,
        mode: String? = null,
        realModeChange: Boolean = false,
    ): Boolean {
        if (ConfidenceEngine.decide(result, relevant) != DetectionDecision.ASK_USER) return false
        if (recent.any { it.candidate == candidate && it.askedAt <= now && now - it.askedAt < SAME_CANDIDATE_COOLDOWN_MS }) return false
        if (transportSessionId != null && recent.any {
                it.transportSessionId == transportSessionId && (!realModeChange || mode == null || it.mode == mode)
            }) return false
        return true
    }
}
