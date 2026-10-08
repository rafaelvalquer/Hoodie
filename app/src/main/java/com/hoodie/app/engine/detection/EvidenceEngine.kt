package com.hoodie.app.engine.detection

import com.hoodie.app.domain.detection.*

object EvidenceEngine {
    /** Keep the latest sample of each kind: repeated sensor delivery is not corroboration. */
    fun evaluate(evidence: List<DetectionEvidence>, now: Long, maxAgeMillis: Long = 30 * 60_000L): DetectionResult {
        require(maxAgeMillis >= 0)
        val normalized = evidence.filter { it.timestamp <= now && now - it.timestamp <= maxAgeMillis }
            .groupBy { it.type }.values.map { samples -> samples.maxBy { it.timestamp } }
            .sortedBy { it.type.ordinal }
        val corrected = normalized.any { it.type == EvidenceType.USER_CORRECTION && it.contribution > 0 }
        val confirmed = normalized.any { it.type == EvidenceType.USER_CONFIRMATION && it.contribution > 0 }
        val source = when {
            corrected -> DetectionSource.USER_CORRECTION
            confirmed -> DetectionSource.USER_CONFIRMATION
            normalized.any { it.type in SENSOR_TYPES } -> DetectionSource.SENSOR
            normalized.isNotEmpty() -> DetectionSource.ROUTINE
            else -> DetectionSource.FALLBACK
        }
        val confidence = if (corrected || confirmed) ConfidenceScore.CERTAIN
            else ConfidenceScore(normalized.sumOf { it.contribution.toDouble() }.toFloat().coerceIn(0f, 1f))
        return DetectionResult(confidence, source, normalized, normalized.any { it.contribution < 0 } && !corrected && !confirmed)
    }

    private val SENSOR_TYPES = setOf(EvidenceType.KNOWN_PLACE, EvidenceType.GEOFENCE_EXIT,
        EvidenceType.MOVEMENT, EvidenceType.SUSTAINED_ACTIVITY, EvidenceType.PHONE_ACTIVITY)
}
