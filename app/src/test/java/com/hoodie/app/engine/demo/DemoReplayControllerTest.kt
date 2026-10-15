package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.screens.diary.ReplayState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

internal fun demoTimeline() = DemoReplayTimeline(DemoDayFactory.create(LocalDate.of(2026, 10, 15), ZoneId.of("America/Sao_Paulo")))

class DemoReplayControllerTest {
    @Test fun `play pause and resume use monotonic elapsed time without advancing while paused`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        assertEquals(ReplayState.IDLE, c.snapshot.state)
        assertEquals(t.startAt, c.play(100).timestamp)
        c.tick(1_100)
        assertEquals(t.timestampAtTourElapsed(1_000), c.snapshot.timestamp)
        val paused = c.pause(1_100)
        assertEquals(ReplayState.PAUSED, paused.state)
        assertEquals(paused, c.tick(50_000))
        c.play(50_000); c.tick(51_000)
        assertEquals(t.timestampAtTourElapsed(2_000), c.snapshot.timestamp)
    }

    @Test fun `full day preserves position when speed changes from five to ten minutes per second`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        c.setMode(DemoReplayMode.FULL_DAY, 0); c.play(100)
        c.tick(1_100)
        assertEquals(t.startAt + 5 * 60_000, c.snapshot.timestamp)
        c.setSpeed(ReplaySpeed.VERY_FAST, 1_100)
        assertEquals(t.startAt + 5 * 60_000, c.snapshot.timestamp)
        c.tick(2_100)
        assertEquals(t.startAt + 15 * 60_000, c.snapshot.timestamp)
    }

    @Test fun `tour always takes fifty seconds independently of complete replay speed`() {
        for (speed in ReplaySpeed.entries) {
            val t = demoTimeline(); val c = DemoReplayController(t)
            c.setSpeed(speed, 0); c.play(0); c.tick(49_999)
            assertEquals(ReplayState.PLAYING, c.snapshot.state)
            c.tick(50_000)
            assertEquals(t.endAt, c.snapshot.timestamp)
            assertEquals(ReplayState.FINISHED, c.snapshot.state)
            assertEquals(t.startAt, c.play(60_000).timestamp)
        }
    }

    @Test fun `full day finishes at twenty oclock even after an extremely delayed tick`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        c.setMode(DemoReplayMode.FULL_DAY, 0); c.play(1_000); c.tick(Long.MAX_VALUE)
        assertEquals(t.endAt, c.snapshot.timestamp)
        assertEquals(ReplayState.FINISHED, c.snapshot.state)
        assertEquals(t.startAt, c.restart().timestamp)
        assertEquals(ReplayState.IDLE, c.snapshot.state)
    }

    @Test fun `seek hits activity boundaries exactly and does not jump backwards on resume`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        val coffee = t.day.activities.single { it.activity == HoodieActivity.COFFEE }.startedAt
        c.seek(coffee, 100)
        assertEquals(coffee, c.snapshot.timestamp)
        c.play(100); c.tick(100)
        assertEquals(coffee, c.snapshot.timestamp)
        c.tick(101)
        assertTrue(c.snapshot.timestamp >= coffee)
        assertEquals(t.startAt, c.seek(Long.MIN_VALUE, 200).timestamp)
        assertEquals(t.endAt, c.seek(Long.MAX_VALUE, 200).timestamp)
    }

    @Test fun `next and previous use the selected mode events and restart resets the position`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        assertEquals(t.tour[1].timestamp, c.next(0).timestamp)
        assertEquals(t.tour[2].timestamp, c.next(0).timestamp)
        assertEquals(t.tour[1].timestamp, c.previous(0).timestamp)
        c.setMode(DemoReplayMode.FULL_DAY, 0)
        assertEquals(t.startAt, c.snapshot.timestamp)
        assertEquals(t.day.activities[1].startedAt, c.next(0).timestamp)
        assertEquals(t.startAt, c.previous(0).timestamp)
        c.seek(t.endAt, 0); c.previous(0)
        assertEquals(t.day.activities.last().startedAt, c.snapshot.timestamp)
        assertEquals(t.startAt, c.restart().timestamp)
    }

    @Test fun `restoration preserves position without resuming a clock while invisible`() {
        val t = demoTimeline(); val c = DemoReplayController(t)
        c.play(0); c.tick(15_000)
        val restored = DemoReplayController(t, c.snapshot)
        assertEquals(c.snapshot.timestamp, restored.snapshot.timestamp)
        assertEquals(ReplayState.PAUSED, restored.snapshot.state)
        assertEquals(c.snapshot.timestamp, restored.tick(1_000_000).timestamp)
        restored.play(1_000_000); restored.tick(1_001_000)
        assertEquals(t.timestampAtTourElapsed(16_000), restored.snapshot.timestamp)
    }
}
