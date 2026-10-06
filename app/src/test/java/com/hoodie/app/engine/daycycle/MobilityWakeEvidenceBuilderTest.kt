package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MobilityWakeEvidenceBuilderTest {
    private fun session(at: Long, confirmed: Boolean) = MobilitySessionEntity(
        startedAt = at, initialMode = MovementMode.WALKING, currentMode = MovementMode.WALKING,
        state = MobilityState.WALKING, confidence = .9f, confirmed = confirmed, source = MobilitySource.ACTIVITY_RECOGNITION,
    )

    @Test fun onlyConfirmedSessionsAreWakeEvidence() {
        val result = MobilityWakeEvidenceBuilder.build(listOf(session(100, false), session(200, true)), 0, 300)
        assertEquals(listOf(200L), result.map { it.timestamp })
    }
}
