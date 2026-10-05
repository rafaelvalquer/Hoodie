package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailySummaryCalculatorTest {
    @Test fun openContextStopsAtNowAndFutureSpansDoNotAddTime() {
        val summary = DailySummaryCalculator.compute(
            listOf(ContextSpan(UserContextType.HOME, 100, null), ContextSpan(UserContextType.WORK, 600, 800)),
            LocalDate.of(2026, 1, 1), 0, 1_000, 500,
        )
        assertEquals(400L, summary.homeMs)
        assertEquals(0L, summary.workMs)
        assertEquals(400L, summary.totalMs)
    }

    @Test fun emptyDayHasZeroTotals() {
        val summary = DailySummaryCalculator.compute(emptyList(), LocalDate.of(2026, 1, 1), 0, 1_000, 500)
        assertEquals(0L, summary.totalMs)
    }

    @Test fun diningDoesNotLeakIntoLunchSummary() {
        val summary = DailySummaryCalculator.compute(
            listOf(
                ContextSpan(UserContextType.LUNCH, 100, 200),
                ContextSpan(UserContextType.DINING, 200, 500),
            ),
            LocalDate.of(2026, 1, 1), 0, 1_000, 1_000,
        )
        assertEquals(100L, summary.lunchMs)
        assertEquals(300L, summary.otherMs)
        assertEquals(400L, summary.totalMs)
    }

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
