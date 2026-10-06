package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.MobilitySessionEntity

object MobilityWakeEvidenceBuilder {
    fun build(sessions: List<MobilitySessionEntity>, from: Long, until: Long): List<WakeEvidence> =
        sessions.asSequence()
            .filter { it.confirmed && it.startedAt in from until until }
            .sortedBy { it.startedAt }
            .map { WakeEvidence(it.startedAt, WakeEvidenceType.MOBILITY_CONFIRMED, WakeDetectionPolicy.MOBILITY_SCORE) }
            .toList()
}
