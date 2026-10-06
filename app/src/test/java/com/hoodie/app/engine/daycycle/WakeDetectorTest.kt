package com.hoodie.app.engine.daycycle

import com.hoodie.app.domain.daycycle.WakeConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeDetectorTest {
    private val detector = WakeDetector()
    private val sleepStart = 23 * 60 * 60_000L

    @Test fun ignoresShortIsolatedPhoneEvidenceWithoutLongSleep() {
        val result = detector.detect(
            listOf(WakeEvidence(sleepStart + 4 * 60 * 60_000L, WakeEvidenceType.PHONE_SUSTAINED, 70, 20_000)),
            listOf(sleepStart + 4 * 60 * 60_000L),
        ) { sleepStart }
        assertNull(result)
    }

    @Test fun sustainedPhoneWakeIsMediumAndMovementCorroboratesToHigh() {
        val phone = WakeEvidence(sleepStart + 8 * 60 * 60_000L, WakeEvidenceType.PHONE_SUSTAINED, 70, 3 * 60_000L)
        val movement = WakeEvidence(phone.timestamp + 20 * 60_000L, WakeEvidenceType.MOBILITY_CONFIRMED, 100)
        val result = detector.detect(listOf(phone, movement), listOf(sleepStart)) { sleepStart }
        assertEquals(phone, result?.evidence)
        assertEquals(WakeConfidence.HIGH, result?.confidence)
    }

    @Test fun mobilityAloneIsHighConfidence() {
        val movement = WakeEvidence(sleepStart + 7 * 60 * 60_000L, WakeEvidenceType.MOBILITY_CONFIRMED, 100)
        assertEquals(WakeConfidence.HIGH, detector.detect(listOf(movement), listOf(sleepStart)) { sleepStart }?.confidence)
    }
}
