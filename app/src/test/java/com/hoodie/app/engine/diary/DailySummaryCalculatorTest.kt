package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailySummaryCalculatorTest {
    @Test fun clipsAtMidnightAndCountsUnmappedContextsAsOther() {
        val date = LocalDate.of(2026, 1, 1)
        val day = 86_400_000L
        val summary = DailySummaryCalculator.compute(
            listOf(ContextSpan(UserContextType.HOME, day - 20, day + 30), ContextSpan(UserContextType.STUDY, day + 30, day + 70)),
            date, day, day * 2, day + 60,
        )
        assertEquals(30L, summary.homeMs)
        assertEquals(30L, summary.otherMs)
        assertEquals(60L, summary.totalMs)
    }
}
