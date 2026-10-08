package com.hoodie.app.domain.detection

@JvmInline
value class ConfidenceScore(val value: Float) {
    init { require(value.isFinite() && value in 0f..1f) { "Confidence must be finite and between 0 and 1" } }
    val band: ConfidenceBand get() = when {
        value < .45f -> ConfidenceBand.VERY_LOW
        value < .60f -> ConfidenceBand.LOW
        value < .75f -> ConfidenceBand.MEDIUM
        value < .90f -> ConfidenceBand.HIGH
        else -> ConfidenceBand.VERY_HIGH
    }
    companion object {
        val CERTAIN = ConfidenceScore(1f)
        val UNKNOWN = ConfidenceScore(0f)
        fun fromPoints(points: Int) = ConfidenceScore(points.coerceIn(0, 100) / 100f)
    }
}

enum class ConfidenceBand { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
