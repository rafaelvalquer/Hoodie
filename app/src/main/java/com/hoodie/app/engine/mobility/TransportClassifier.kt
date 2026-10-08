package com.hoodie.app.engine.mobility

import com.hoodie.app.core.database.TransportPatternEntity
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.detection.*
import kotlin.math.exp

data class TransportClassification(val mode: MovementMode, val confidence: ConfidenceScore,
    val probabilities: Map<MovementMode, ConfidenceScore>, val evidence: Map<MovementMode, List<DetectionEvidence>>,
    val source: DetectionSource = DetectionSource.SENSOR) {
    val detection get() = DetectionResult(confidence, source, evidence[mode].orEmpty())
}

object TransportClassifier {
    val modes = listOf(MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO)
    fun classify(features: TransportFeatures, patterns: List<TransportPatternEntity>, timestamp: Long, confirmed: MovementMode? = null): TransportClassification {
        if (confirmed != null) return TransportClassification(confirmed, ConfidenceScore.CERTAIN,
            (modes + confirmed).distinct().associateWith { if (it == confirmed) ConfidenceScore.CERTAIN else ConfidenceScore.UNKNOWN },
            mapOf(confirmed to listOf(DetectionEvidence(EvidenceType.USER_CONFIRMATION, 1f, timestamp))), DetectionSource.USER_CONFIRMATION)
        if (!features.vehicle) return TransportClassification(MovementMode.VEHICLE_UNKNOWN, ConfidenceScore.UNKNOWN,
            modes.associateWith { ConfidenceScore(.25f) }, emptyMap())
        val evidence = modes.associateWith { mutableListOf<DetectionEvidence>() }
        fun add(mode: MovementMode, type: EvidenceType, contribution: Float) { evidence.getValue(mode) += DetectionEvidence(type, contribution, timestamp) }
        if (features.vehicle) modes.forEach { add(it, EvidenceType.MOVEMENT, .20f) }
        if (features.meanSpeedKmh != null) {
            if (features.meanSpeedKmh in 15f..90f) add(MovementMode.CAR, EvidenceType.SUSTAINED_ACTIVITY, .15f)
            if (features.meanSpeedKmh in 15f..60f) add(MovementMode.BUS, EvidenceType.SUSTAINED_ACTIVITY, .10f)
            if (features.maxSpeedKmh != null && features.maxSpeedKmh >= 80 && features.regularStops) add(MovementMode.TRAIN, EvidenceType.SUSTAINED_ACTIVITY, .30f)
            if (features.regularStops && features.meanSpeedKmh in 20f..65f && features.meanStopMs in 20_000..80_000) add(MovementMode.METRO, EvidenceType.SUSTAINED_ACTIVITY, .25f)
        }
        if (features.durationMs >= 5 * 60_000L) {
            if (features.stopCount <= 1 && features.meanSpeedKmh != null) add(MovementMode.CAR, EvidenceType.HISTORY, .20f)
            if (features.stopCount >= 3 && !features.regularStops) add(MovementMode.BUS, EvidenceType.HISTORY, .20f)
        }
        modes.forEach { mode ->
            patterns.firstOrNull { it.mode == mode.name }?.let { pattern ->
                val support = if (pattern.confirmations >= 3) .85f else .20f
                add(mode, EvidenceType.LEARNED_PATTERN, (support * pattern.confidence).coerceIn(0f, .85f))
                if (pattern.rejections > pattern.confirmations) add(mode, EvidenceType.USER_CORRECTION, -.20f)
            }
        }
        val scores = evidence.mapValues { (_, samples) -> samples.sumOf { it.contribution.toDouble() } }
        val strongest = scores.maxBy { it.value }
        val weights = scores.mapValues { exp((it.value - strongest.value) / .12) }
        val total = weights.values.sum()
        val probabilities = weights.mapValues { ConfidenceScore((it.value / total).toFloat()) }
        return TransportClassification(strongest.key, probabilities.getValue(strongest.key), probabilities, evidence)
    }
}
