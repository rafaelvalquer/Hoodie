package com.hoodie.app.presentation.screens.diary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryLoadRequestGateTest {
    @Test
    fun `switching dates rejects results from the previous request`() {
        val today = LocalDate.of(2026, 10, 9)
        val gate = DiaryLoadRequestGate(today)
        val todayRequest = gate.begin(today)
        val yesterdayRequest = gate.begin(today.minusDays(1))

        assertFalse(gate.isCurrent(todayRequest))
        assertTrue(gate.isCurrent(yesterdayRequest))
    }

    @Test
    fun `retry supersedes an earlier request for the same date`() {
        val date = LocalDate.of(2026, 10, 9)
        val gate = DiaryLoadRequestGate(date)
        val initial = gate.begin(date)
        val retry = gate.begin(date)

        assertFalse(gate.isCurrent(initial))
        assertTrue(gate.isCurrent(retry))
        assertTrue(retry.requestId > initial.requestId)
    }
}
