package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.ReplayHudAssembler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DemoReplayTimelineTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun day() = DemoDayFactory.create(LocalDate.of(2026, 10, 15), zone)

    @Test fun `real diary assemblers receive eleven fictitious activities and the explicit active window`() {
        val day = day()
        val window = day.activityWindow!!
        assertEquals(11, day.activities.size)
        assertEquals(window.activeStartAt, day.activities.first().startedAt)
        assertEquals(window.activeEndAt, day.activities.last().endedAt)
        day.activities.zipWithNext().forEach { (a, b) -> assertEquals(a.endedAt, b.startedAt) }
        val view = DemoDiaryAssembler.assemble(day)
        assertEquals(window.activeStartAt, view.diary.replay.startAt)
        assertEquals(window.activeEndAt, view.diary.replay.endAt)
        assertEquals(window.activeStartAt, view.journey.startAt)
        assertEquals(listOf(1L, 2L, 3L, 2L, 1L), view.journey.nodes.map { it.placeId })
        val coffee = day.activities.single { it.activity == HoodieActivity.COFFEE }
        val hud = ReplayHudAssembler.assemble(view.diary.replay, null, coffee.startedAt, zone)
        assertEquals(HoodieActivity.COFFEE, hud.hoodieActivity)
        assertEquals(UserContextType.WORK, hud.context)
        assertNull(hud.activePhoneApp)
        assertTrue(day.places.all { it.encryptedCoordinates.isEmpty() })
    }

    @Test fun `tour covers all seven moments in fifty seconds with continuous commute progress`() {
        val day = day()
        val timeline = DemoReplayTimeline(day)
        val view = DemoDiaryAssembler.assemble(day)
        assertEquals(50_000L, timeline.tourDurationMs)
        assertEquals(7, timeline.tour.size)
        assertEquals(timeline.startAt, timeline.timestampAtTourElapsed(0))
        assertEquals(timeline.endAt, timeline.timestampAtTourElapsed(50_000))
        timeline.tour.forEach { step ->
            assertEquals(step.timestamp, timeline.timestampAtTourElapsed(step.elapsedStartMs))
            assertEquals(step.elapsedStartMs, timeline.tourElapsedAt(step.timestamp))
            assertTrue(step.narrative.isNotBlank())
        }
        val commute = timeline.tour[1]
        val midpoint = timeline.timestampAtTourElapsed(commute.elapsedStartMs + commute.durationMs / 2)
        val map = JourneyReplayAssembler.stateAt(view.journey, midpoint, true)
        assertNotNull(map.activeSegmentIndex)
        assertEquals(.5f, map.segmentProgress, .001f)
        var previous = timeline.startAt
        for (elapsed in 0L..50_000L step 25) {
            val current = timeline.timestampAtTourElapsed(elapsed)
            assertTrue(current >= previous)
            assertTrue(kotlin.math.abs(timeline.tourElapsedAt(current) - elapsed) <= 1)
            previous = current
        }
    }

    @Test fun `civil daylight saving changes never move the seven oclock replay start`() {
        val newYork = ZoneId.of("America/New_York")
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
            val day = DemoDayFactory.create(date, newYork)
            val window = day.activityWindow!!
            assertEquals(7, Instant.ofEpochMilli(window.activeStartAt).atZone(newYork).hour)
            assertEquals(20, Instant.ofEpochMilli(window.activeEndAt).atZone(newYork).hour)
            assertEquals(date.plusDays(1), Instant.ofEpochMilli(day.dayEnd).atZone(newYork).toLocalDate())
            assertEquals(13 * 3_600_000L, window.activeEndAt - window.activeStartAt)
        }
    }

    @Test fun `legacy static demo can retain its previous dataset while replay flag is off`() {
        val day = DemoDayFactory.create(LocalDate.of(2026, 10, 15), zone, includeReplayActivities = false)
        assertTrue(day.activities.isEmpty())
        assertNull(day.activityWindow)
        assertNull(day.contexts.last().endedAt)
        assertTrue(runCatching { DemoReplayTimeline(day) }.isFailure)
    }
}
