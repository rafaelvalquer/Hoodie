package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.PlaceVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayActiveStartTest {
    @Test fun carryOverHomeAndSleepingAreClippedVisuallyToWakeWithoutMidnightNodes() {
        val dayStart = 0L
        val wake = 6 * 3_600_000L + 47 * 60_000L
        val leave = 7 * 3_600_000L + 28 * 60_000L
        val home = ContextEventEntity(id = 1, type = UserContextType.HOME, startedAt = -50 * 60_000L, endedAt = leave, confidence = 1f, placeId = 1, source = ContextSource.GEOFENCE)
        val sleeping = HoodieActivityEntity(id = 1, activity = HoodieActivity.SLEEPING, startedAt = -40 * 60_000L, endedAt = wake, userContext = UserContextType.HOME)
        val visit = PlaceVisit(1, "Casa", PlaceType.HOME, dayStart, leave, leave, 1)
        val replay = ReplaySequenceBuilder.build(listOf(visit), emptyList(), dayStart, 24 * 3_600_000L, listOf(home), listOf(sleeping), 24 * 3_600_000L, activeStartAt = wake, activeEndAt = 10 * 3_600_000L)
        assertEquals(wake, replay.startAt)
        assertEquals(wake, replay.visits.single().arrivalAt)
        assertEquals(wake, replay.contexts.single().first.first)
        assertTrue(replay.activities.none { it.second == HoodieActivity.SLEEPING })
        assertTrue(replay.activities.any { it.second == HoodieActivity.WAKING_UP })
    }
}
