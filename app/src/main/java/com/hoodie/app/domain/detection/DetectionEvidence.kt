package com.hoodie.app.domain.detection

enum class EvidenceType {
    KNOWN_PLACE, USER_DEFINED_PLACE, GEOFENCE_EXIT, MOVEMENT, SUSTAINED_ACTIVITY, PHONE_ACTIVITY,
    TIME_MATCH, DAY_MATCH, HISTORY, LEARNED_PATTERN, USER_CONFIRMATION, USER_CORRECTION
}

enum class DetectionSource(val priority: Int) {
    FALLBACK(0), ROUTINE(1), SENSOR(2), USER_CONFIRMATION(3), USER_CORRECTION(4)
}

/** Signed contribution: opposing evidence lowers confidence rather than being discarded. */
data class DetectionEvidence(val type: EvidenceType, val contribution: Float, val timestamp: Long) {
    init { require(contribution.isFinite() && contribution in -1f..1f) }
}

data class DetectionResult(
    val confidence: ConfidenceScore,
    val source: DetectionSource,
    val evidence: List<DetectionEvidence>,
    val conflicting: Boolean = false,
)
