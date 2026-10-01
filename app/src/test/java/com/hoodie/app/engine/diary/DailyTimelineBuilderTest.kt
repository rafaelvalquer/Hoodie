package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTimelineBuilderTest {
    @Test fun preservesPersistedEventsAndSortsChronologically() {
        val context = ContextEventEntity(id = 8, type = UserContextType.HOME, startedAt = 100, endedAt = 200, confidence = 1f, placeId = 2, source = ContextSource.MANUAL)
        val event = TimelineEventEntity(timestamp = 100, actor = TimelineActor.USER, emoji = "🏠", text = "Chegou", sourceType = TimelineSourceType.CONTEXT, sourceId = 8)
        val result = DailyTimelineBuilder.build(listOf(context), listOf(event), emptyList())
        assertEquals(2, result.size)
        assertEquals(1, result.count { it.title == "Chegou" })
        assertEquals(result.sortedBy { it.timestamp }, result)
    }

    @Test fun suppressesSyntheticContextAndActivityWhenEquivalentTimelineEventExists() {
        val context = ContextEventEntity(id = 9, type = UserContextType.WORK, startedAt = 300, endedAt = 500, confidence = 1f, placeId = 2, source = ContextSource.GEOFENCE)
        val activity = HoodieActivityEntity(id = 10, activity = HoodieActivity.WORKING, startedAt = 320, endedAt = 450, userContext = UserContextType.WORK)
        val events = listOf(
            TimelineEventEntity(id = 11, timestamp = 300, actor = TimelineActor.USER, emoji = "🏢", text = "Chegou ao trabalho", sourceType = TimelineSourceType.CONTEXT, sourceId = 999),
            TimelineEventEntity(id = 12, timestamp = 320, actor = TimelineActor.HOODIE, emoji = "💻", text = "Hoodie começou a trabalhar", sourceType = TimelineSourceType.HOODIE_ACTIVITY, sourceId = 999),
        )
        val result = DailyTimelineBuilder.build(listOf(context), events, listOf(activity), dayStart = 0, dayEnd = 1_000, now = 1_000)
        assertEquals(listOf("Chegou ao trabalho", "Hoodie começou a trabalhar", "Saiu de trabalho"), result.map { it.title })
    }
}
