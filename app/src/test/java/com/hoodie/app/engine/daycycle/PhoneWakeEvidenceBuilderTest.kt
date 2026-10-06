package com.hoodie.app.engine.daycycle

import com.hoodie.app.domain.phoneinsights.model.AppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWakeEvidenceBuilderTest {
    @Test fun ignoresTwentySecondNightPeek() {
        val result = PhoneWakeEvidenceBuilder.build(listOf(AppSession("chat", 3 * 3_600_000L, 3 * 3_600_000L + 20_000L)), 0, 86_400_000L)
        assertTrue(result.isEmpty())
    }

    @Test fun aggregatesForegroundAcrossAppsAndReturnsSequenceStart() {
        val result = PhoneWakeEvidenceBuilder.build(
            listOf(AppSession("a", 24_600_000, 24_640_000), AppSession("b", 24_665_000, 24_735_000), AppSession("c", 24_750_000, 24_800_000)),
            0, 86_400_000L,
        )
        assertEquals(24_600_000L, result.first().timestamp)
    }
}
