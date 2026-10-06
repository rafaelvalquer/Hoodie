package com.hoodie.app.engine.daycycle

enum class WakeEvidenceType {
    PHONE_SUSTAINED,
    MOBILITY_CONFIRMED,
    HOME_EXIT,
    CONTEXT_ACTIVITY,
    SCREEN_SUSTAINED,
    HOODIE_WAKING_UP,
}

data class WakeEvidence(
    val timestamp: Long,
    val type: WakeEvidenceType,
    val confidenceScore: Int,
    val durationMs: Long? = null,
)

data class DetectedWake(
    val evidence: WakeEvidence,
    val confidence: com.hoodie.app.domain.daycycle.WakeConfidence,
    val sleepBefore: com.hoodie.app.domain.daycycle.InferredSleepSpan?,
)
