package com.hoodie.app.engine.daystate

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.*
import com.hoodie.app.domain.daystate.*
import com.hoodie.app.domain.detection.ConfidenceScore
import org.junit.Assert.*
import org.junit.Test

class DayStateEngineTest {
    private val window = DailyActivityWindow(0, 86_400_000, 1_000, 70_000_000, null, WakeReason.PHONE_SUSTAINED, WakeConfidence.HIGH, false)
    private fun snapshot(state: DayState, at: Long = 0) = DayStateSnapshot(state, at, ConfidenceScore(.9f), DayStateReason.ACTIVE_CONTEXT, false)
    private fun input(now: Long = 10_000) = DayStateInput(now, window, UserContextType.WORK, ConfidenceScore(.9f), lastActivityAt = now)

    @Test fun everyEdgeIsRestricted() {
        val allowed = setOf(DayState.SLEEPING to DayState.WAKING, DayState.WAKING to DayState.ACTIVE,
            DayState.ACTIVE to DayState.COMMUTING, DayState.COMMUTING to DayState.ACTIVE,
            DayState.ACTIVE to DayState.WINDING_DOWN, DayState.WINDING_DOWN to DayState.ACTIVE,
            DayState.WINDING_DOWN to DayState.SLEEPING)
        DayState.entries.forEach { from -> DayState.entries.forEach { to ->
            assertEquals("$from -> $to", from == to || (from to to) in allowed, DayStateEngine.canTransition(from, to))
        } }
    }

    @Test fun sleepingMovementPassesThroughWakeAndActive() {
        val moving = input().copy(context = UserContextType.COMMUTING, movementConfidence = ConfidenceScore(.92f), movementConfirmed = true)
        val waking = DayStateEngine.resolve(snapshot(DayState.SLEEPING), moving)
        val active = DayStateEngine.resolve(waking, moving)
        val commute = DayStateEngine.resolve(active, moving)
        assertEquals(DayState.WAKING, waking.state)
        assertEquals(DayState.ACTIVE, active.state)
        assertEquals(DayState.COMMUTING, commute.state)
        assertEquals(DayStateReason.COMMUTE_CONFIRMED, commute.reason)
        assertEquals(DayState.ACTIVE, DayStateEngine.resolve(commute, input()).state)
    }

    @Test fun scheduleChangesAndOldEventsNeverRollbackActive() {
        val active = snapshot(DayState.ACTIVE, 100_000)
        assertEquals(active, DayStateEngine.resolve(active, input(99_999)))
        val schedule = input(110_000).copy(window = window.copy(wakeReason = WakeReason.SCHEDULE_FALLBACK, wakeConfidence = WakeConfidence.LOW))
        assertEquals(DayState.ACTIVE, DayStateEngine.resolve(active, schedule).state)
        assertEquals(100_000L, DayStateEngine.resolve(active, schedule).startedAt)
    }

    @Test fun inactivityConsumesExistingSleepResultAndActivityResumes() {
        val sleepWindow = window.copy(sleepAfterEnd = InferredSleepOnset(1_000_000, SleepOnsetReason.CORROBORATED, SleepConfidence.HIGH))
        val quiet = input(2_000_000).copy(window = sleepWindow, context = UserContextType.HOME, lastActivityAt = 900_000, nearBedtime = true)
        val winding = DayStateEngine.resolve(snapshot(DayState.ACTIVE), quiet)
        assertEquals(DayState.WINDING_DOWN, winding.state)
        assertEquals(DayState.ACTIVE, DayStateEngine.resolve(winding, quiet.copy(phoneActive = true, lastActivityAt = quiet.now)).state)
        val sleeping = DayStateEngine.resolve(winding, quiet)
        assertEquals(DayState.SLEEPING, sleeping.state)
        assertEquals(DayState.SLEEPING, DayStateEngine.resolve(sleeping, quiet.copy(now = quiet.now + 60_000)).state)
    }

    @Test fun homePresenceOrScheduleAloneDoesNotWakeSleepingUser() {
        val quiet = input().copy(context = UserContextType.HOME, lastActivityAt = null,
            window = window.copy(wakeReason = WakeReason.SCHEDULE_FALLBACK, wakeConfidence = WakeConfidence.LOW))
        assertEquals(DayState.SLEEPING, DayStateEngine.resolve(snapshot(DayState.SLEEPING), quiet).state)
    }
}
