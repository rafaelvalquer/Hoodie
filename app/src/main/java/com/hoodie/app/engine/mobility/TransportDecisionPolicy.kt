package com.hoodie.app.engine.mobility

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.detection.*

data class TransportSwitchCandidate(val mode: MovementMode, val since: Long)
data class TransportSwitchResult(val mode: MovementMode, val pending: TransportSwitchCandidate?)

object TransportDecisionPolicy {
    const val REQUIRED_MARGIN = .15f
    const val SUSTAINED_MS = 45_000L
    fun resolve(current: MovementMode, currentConfidence: ConfidenceScore, incoming: TransportClassification,
        pending: TransportSwitchCandidate?, now: Long, corrected: Boolean = false): TransportSwitchResult {
        if (corrected || current == incoming.mode) return TransportSwitchResult(current, null)
        if (incoming.source.priority >= DetectionSource.USER_CONFIRMATION.priority) return TransportSwitchResult(incoming.mode, null)
        if (incoming.confidence.value < .60f) return TransportSwitchResult(current, null)
        if (current == MovementMode.VEHICLE_UNKNOWN || current == MovementMode.NONE) return TransportSwitchResult(incoming.mode, null)
        if (incoming.confidence.value < currentConfidence.value + REQUIRED_MARGIN) return TransportSwitchResult(current, null)
        val candidate = pending?.takeIf { it.mode == incoming.mode && it.since <= now } ?: TransportSwitchCandidate(incoming.mode, now)
        return if (now - candidate.since >= SUSTAINED_MS) TransportSwitchResult(incoming.mode, null) else TransportSwitchResult(current, candidate)
    }
}
