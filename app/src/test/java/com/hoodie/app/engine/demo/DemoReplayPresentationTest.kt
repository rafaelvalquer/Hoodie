package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.diary.ReplayState
import org.junit.Assert.*
import org.junit.Test

class DemoReplayPresentationTest {
    @Test fun `every fictitious activity drives the HUD map and contextual scene from one timestamp`() {
        val timeline = demoTimeline()
        val view = DemoDiaryAssembler.assemble(timeline.day)
        for (activity in timeline.day.activities) {
            val p = DemoReplayPresentation.assemble(timeline, view,
                DemoReplaySnapshot(activity.startedAt, state = ReplayState.PAUSED))
            assertEquals(activity.startedAt, p.replay.currentTimestamp)
            assertEquals(activity.activity, p.activity)
            assertEquals(activity.userContext, p.context)
            assertEquals(p.activity, p.replay.currentHoodieActivity)
            assertNull(p.replay.activePhoneApp)
            val scene = when (p.context) {
                UserContextType.HOME -> SceneId.HOME
                UserContextType.WORK -> SceneId.OFFICE
                UserContextType.LUNCH -> SceneId.RESTAURANT
                UserContextType.COMMUTING -> SceneId.STREET
                else -> error("Unexpected demo context")
            }
            assertEquals(scene, p.contextualVisual().scene)
            assertEquals(p.activePlaceId, p.contextualVisual().placeId)
        }
    }

    @Test fun `closing presentation retains the final place and the twenty oclock clock`() {
        val t = demoTimeline(); val view = DemoDiaryAssembler.assemble(t.day)
        val p = DemoReplayPresentation.assemble(t, view, DemoReplaySnapshot(t.endAt, state = ReplayState.FINISHED))
        assertEquals("20:00", p.clockLabel)
        assertEquals("Fim da demonstração", p.title)
        assertEquals(1L, p.activePlaceId)
        assertEquals(UserContextType.HOME, p.context)
        assertEquals(t.endAt, p.replay.currentTimestamp)
        assertEquals(1f, p.replay.progress, .0001f)
        assertTrue(p.narrative.contains("fictício"))
    }
}
