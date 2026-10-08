package com.hoodie.app.engine.detection

import com.hoodie.app.domain.detection.*

object ConfidenceEngine {
    fun decide(result: DetectionResult, relevant: Boolean = true): DetectionDecision {
        if (result.source.priority >= DetectionSource.USER_CONFIRMATION.priority) return DetectionDecision.AUTO_ACCEPT
        val score = result.confidence.value
        return when {
            score < .45f -> DetectionDecision.UNKNOWN
            result.conflicting -> if (relevant) DetectionDecision.ASK_USER else DetectionDecision.UNKNOWN
            score < .60f -> if (relevant) DetectionDecision.ASK_USER else DetectionDecision.UNKNOWN
            score < .85f -> DetectionDecision.PROVISIONAL
            else -> DetectionDecision.AUTO_ACCEPT
        }
    }

    fun canReplace(current: DetectionSource, incoming: DetectionSource): Boolean = incoming.priority >= current.priority
}
