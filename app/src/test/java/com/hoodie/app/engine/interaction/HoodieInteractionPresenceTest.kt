package com.hoodie.app.engine.interaction

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionPresenceTest {
    private fun frame(scene: SceneId, variant: Int = 0) = AnimationStateMachine().let { machine ->
        machine.place(VisualState(scene, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1)), variant = variant), 1)
        machine.frame(2, 600, DayPeriod.DAY)!!
    }

    @Test fun `renderer reports nearby Bulldog only during his actual presence`() {
        val base = frame(SceneId.OFFICE)
        val probe = OfficeNpcDirector.createSession(base.env)
        val bulldog = probe.npcSlots(600).first { it.officeBrain?.npcId == "bulldog_exec" }.officeBrain!!
        val position = bulldog.movementAt(30_000)
        val nearby = base.copy(x = position.x, y = position.floorY + 20)
        val renderer = SceneRenderer(true)
        renderer.render(nearby, 30_000)
        assertTrue(InteractionElement.BULLDOG in renderer.lastInteractionElements)
        renderer.render(nearby, 90_000)
        assertFalse(InteractionElement.BULLDOG in renderer.lastInteractionElements)
        renderer.dispose()
        probe.dispose()
    }

    @Test fun `butterfly fact follows the actually painted scene variant`() {
        for (variant in 0..2) {
            val renderer = SceneRenderer(true)
            renderer.render(frame(SceneId.LEISURE, variant), 1_000)
            assertEquals(variant == 2, InteractionElement.BUTTERFLY in renderer.lastInteractionElements)
            renderer.dispose()
        }
    }
}
