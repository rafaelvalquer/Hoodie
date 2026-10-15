package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.ReplayHudAssembler
import org.junit.Assert.*
import org.junit.Test

class DemoReplayNavigationTest {
    @Test fun `repeated previous presses reach earlier tour events even while playback advances`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        c.seek(t.tour[4].timestamp, 0); c.play(100); c.tick(200)
        assertEquals(t.tour[3].timestamp, c.previous(200).timestamp)
        assertEquals(t.tour[2].timestamp, c.previous(250).timestamp)
        assertEquals(t.tour[1].timestamp, c.previous(300).timestamp)
    }

    @Test fun `full replay can navigate back through individual activities without getting stuck on one event`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        c.setMode(DemoReplayMode.FULL_DAY, 0)
        c.seek(t.day.activities.last().startedAt, 0); c.play(0); c.tick(20)
        assertEquals(t.day.activities[9].startedAt, c.previous(20).timestamp)
        assertEquals(t.day.activities[8].startedAt, c.previous(40).timestamp)
        assertEquals(t.day.activities[7].startedAt, c.previous(60).timestamp)
        assertEquals(t.day.activities[8].startedAt, c.next(80).timestamp)
    }

    @Test fun `seeking one timestamp synchronizes actual diary HUD and map location`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        val view = DemoDiaryAssembler.assemble(t.day)
        val coffee = t.day.activities.single { it.activity == HoodieActivity.COFFEE }.startedAt
        c.seek(coffee, 0)
        var hud = ReplayHudAssembler.assemble(view.diary.replay, null, c.snapshot.timestamp, t.day.zone)
        var map = JourneyReplayAssembler.stateAt(view.journey, c.snapshot.timestamp, true)
        assertEquals(HoodieActivity.COFFEE, hud.hoodieActivity)
        assertEquals(2L, view.journey.nodes[map.activeNodeIndex!!].placeId)
        val commute = t.tour[1]
        c.seek(t.timestampAtTourElapsed(commute.elapsedStartMs + commute.durationMs / 2), 0)
        hud = ReplayHudAssembler.assemble(view.diary.replay, null, c.snapshot.timestamp, t.day.zone)
        map = JourneyReplayAssembler.stateAt(view.journey, c.snapshot.timestamp, true)
        assertEquals(c.snapshot.timestamp, hud.timestamp)
        assertEquals(UserContextType.COMMUTING, hud.context)
        assertNotNull(map.activeSegmentIndex)
        assertEquals(.5f, map.segmentProgress, .001f)
    }
}
