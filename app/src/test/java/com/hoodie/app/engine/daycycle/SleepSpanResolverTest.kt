package com.hoodie.app.engine.daycycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepSpanResolverTest {
    @Test fun requiresThreeHoursAndUsesLatestRealActivityAsBaseline() {
        val resolver = SleepSpanResolver()
        assertNull(resolver.resolveBefore(4 * 3_600_000L, listOf(2 * 3_600_000L), null))
        val span = resolver.resolveBefore(8 * 3_600_000L, listOf(1 * 3_600_000L, 4 * 3_600_000L), null)
        assertEquals(4 * 3_600_000L + com.hoodie.app.core.config.HoodieConfig.SLEEP_ONSET_GRACE_MS, span?.startedAt)
    }
}
