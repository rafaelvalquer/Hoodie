package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryEngineTest {
    private val day = 86_400_000L
    private val date = LocalDate.of(2026, 1, 1)
    private fun context(type: UserContextType, start: Long, end: Long?, placeId: Long? = 1) =
        ContextEventEntity(type = type, startedAt = start, endedAt = end, confidence = 1f, placeId = placeId, source = ContextSource.GEOFENCE)

    @Test fun summaryClipsMidnightAndOpenContextToSelectedDay() {
        val summary = DailySummaryCalculator.compute(
            listOf(ContextSpan(UserContextType.HOME, day - 30 * 60_000, day + 60 * 60_000), ContextSpan(UserContextType.WORK, day + 60 * 60_000, null)),
            date, day, day * 2, day + 3 * 60 * 60_000,
        )
        assertEquals(60 * 60_000L, summary.homeMs)
        assertEquals(2 * 60 * 60_000L, summary.workMs)
    }

    @Test fun timelineMergesContextAndHoodieInTimestampOrder() {
        val user = context(UserContextType.HOME, day, day + 2 * 60_000)
        val activity = HoodieActivityEntity(activity = HoodieActivity.WAKING_UP, startedAt = day + 60_000, endedAt = day + 90_000, userContext = UserContextType.HOME)
        val timeline = DailyTimelineBuilder.build(listOf(user), emptyList(), listOf(activity))
        assertEquals(listOf(DiaryActor.USER, DiaryActor.HOODIE, DiaryActor.USER), timeline.map { it.actor })
        assertTrue(timeline.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp })
    }

    @Test fun visitsIgnorePureCommuteAndKeepRepeatedPlacesDistinct() {
        val visits = PlaceVisitBuilder.build(
            listOf(context(UserContextType.WORK, day, day + 1_000), context(UserContextType.COMMUTING, day + 1_000, day + 2_000, null), context(UserContextType.WORK, day + 2_000, day + 3_000)),
            listOf(PlaceEntity(id = 1, name = "Escritório", type = PlaceType.WORK, encryptedCoordinates = "", radiusMeters = 100f, confidence = 1f, createdAt = day)),
            day, day * 2, day * 2,
        )
        assertEquals(2, visits.size)
        assertEquals("Escritório", visits.first().placeName)
        assertEquals(2, visits.first().visitsCount)
    }

    @Test fun mapPreservesVisitOrderAndEdgesIncludingRepeatedPlaces() {
        val visits = (0..2).map { i ->
            com.hoodie.app.domain.diary.model.PlaceVisit(1, "Trabalho", PlaceType.WORK, day + i * 10, day + i * 10 + 5, 5, 2)
        }
        val map = DailyMapBuilder.build(visits)
        assertEquals(listOf(1, 2, 3), map.nodes.map { it.visitIndex })
        assertEquals(2, map.edges.size)
        assertEquals("visit-0", map.edges.first().fromNodeId)
    }

    @Test fun replayActivatesVisitsContextAndTimelineThenFinishes() {
        val visit = com.hoodie.app.domain.diary.model.PlaceVisit(1, "Casa", PlaceType.HOME, day, day + 10, 10, 1)
        val nextVisit = visit.copy(placeId = 2, placeName = "Trabalho", placeType = PlaceType.WORK, arrivalAt = day + 60, departureAt = day + 90)
        val item = DiaryTimelineItem("event", day + 25, DiaryTimelineType.NOTE, DiaryActor.USER, "Em casa")
        val ctx = context(UserContextType.HOME, day, day + 30)
        val activity = HoodieActivityEntity(activity = HoodieActivity.READING, startedAt = day, endedAt = day + 30, userContext = UserContextType.HOME)
        val map = DailyMapBuilder.build(listOf(visit, nextVisit))
        val replay = ReplaySequenceBuilder.build(listOf(visit, nextVisit), listOf(item), day, day + 90, listOf(ctx), listOf(activity), day + 90, map)
        val middle = replay.frameAt(day + 30)
        assertEquals(null, middle.activeNodeId)
        assertEquals("edge-0", middle.activeEdgeId)
        assertTrue(middle.markerX > 0f)
        assertEquals(setOf("event"), middle.highlightedTimelineItemIds)
        assertEquals(UserContextType.HOME, middle.currentContext)
        assertEquals(HoodieActivity.READING, middle.currentHoodieActivity)
        assertEquals(day + 90, replay.frameAt(day + 120).timestamp)
    }

    @Test fun contextTimelineUsesRecordedEventAndDoesNotDuplicateIt() {
        val context = context(UserContextType.HOME, day, day + 5_000)
        val event = TimelineEventEntity(timestamp = day, actor = TimelineActor.USER, emoji = "🏠", text = "Chegou em casa", sourceType = TimelineSourceType.CONTEXT, sourceId = context.id)
        val items = DailyTimelineBuilder.build(listOf(context), listOf(event), emptyList())
        assertEquals(2, items.size)
        assertEquals(1, items.count { it.title == "Chegou em casa" })
        assertNull(items.firstOrNull { it.type == DiaryTimelineType.CONTEXT_CHANGE && it.id.startsWith("timeline-") }?.subtitle)
    }

    @Test fun persistedHoodieActivityTimelineEventIsShownOnce() {
        val activity = HoodieActivityEntity(activity = HoodieActivity.GAMING, startedAt = day + 1_000, endedAt = day + 5_000, userContext = UserContextType.HOME)
        val event = TimelineEventEntity(timestamp = day + 1_100, actor = TimelineActor.HOODIE, emoji = "🎮", text = "Hoodie jogou videogame", sourceType = TimelineSourceType.HOODIE_ACTIVITY, sourceId = activity.startedAt)
        val items = DailyTimelineBuilder.build(emptyList(), listOf(event), listOf(activity), day, day + 10_000, day + 10_000)
        assertEquals(1, items.size)
        assertEquals("Hoodie jogou videogame", items.single().title)
        assertEquals(DiaryActor.HOODIE, items.single().actor)
    }

    @Test fun openContextTimelineIsClippedAtSelectedDayEnd() {
        val context = context(UserContextType.HOME, day - 60_000, null)
        val items = DailyTimelineBuilder.build(listOf(context), emptyList(), emptyList(), day, day + 4 * 60_000, day + 4 * 60_000)
        assertEquals(day, items.first().timestamp)
        assertTrue(items.none { it.timestamp >= day + 4 * 60_000 })
    }

    @Test fun emptyDayReplayHasNoArtificialDayLongPlayback() {
        val replay = ReplaySequenceBuilder.build(emptyList(), emptyList(), day, day * 2)
        assertEquals(replay.startAt, replay.endAt)
    }
}
