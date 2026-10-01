package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.PlaceVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaySequenceBuilderTest {
    @Test fun frameMovesAlongRouteAndReflectsTimelineAndContext() {
        val first = PlaceVisit(1, "Casa", PlaceType.HOME, 100, 120, 20, 1)
        val second = PlaceVisit(2, "Trabalho", PlaceType.WORK, 180, 200, 20, 1)
        val timeline = listOf(DiaryTimelineItem("note", 150, DiaryTimelineType.NOTE, DiaryActor.USER, "Pausa"))
        val context = ContextEventEntity(type = UserContextType.HOME, startedAt = 100, endedAt = 160, confidence = 1f, placeId = 1, source = ContextSource.MANUAL)
        val replay = ReplaySequenceBuilder.build(listOf(first, second), timeline, 100, 220, listOf(context), emptyList(), 220, DailyMapBuilder.build(listOf(first, second)))
        val frame = replay.frameAt(150)
        assertNull(frame.activeNodeId)
        assertEquals("edge-0", frame.activeEdgeId)
        assertTrue(frame.markerX > 0f)
        assertEquals(setOf("note"), frame.highlightedTimelineItemIds)
        assertEquals(UserContextType.HOME, frame.currentContext)
        assertEquals(200L, replay.frameAt(500).timestamp)
        assertEquals(setOf("note"), replay.frameAt(200).highlightedTimelineItemIds)
    }
}
