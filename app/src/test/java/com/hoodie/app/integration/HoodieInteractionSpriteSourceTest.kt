package com.hoodie.app.integration

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.pixel.sprite.*
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionSpriteSourceTest {
    private val sheet = object : SpriteProvider by ProceduralSpriteProvider {
        override val name = "test-sheet"
        override fun durations(animation: AnimationId, direction: Direction) =
            if (animation == AnimationId.PET_HEAD) longArrayOf(300, 300) else ProceduralSpriteProvider.durations(animation, direction)
        override fun frame(request: SpriteRequest): SpriteFrame {
            if (request.animation != AnimationId.PET_HEAD) return ProceduralSpriteProvider.frame(request)
            require(request.frameIndex in 0..1) { "Use the asset's frame count instead of the fallback's count" }
            val frame = ProceduralSpriteProvider.frame(request)
            return frame.copy(image = PixelBuffer(frame.image.width, frame.image.height).apply { fill(0xFF22AAEE.toInt()) },
                source = name, events = setOf(AnimationEvent.FOOD_DONE))
        }
    }

    @Test fun `individual sprite override is used with its own timing and cannot inject prop events`() {
        val m = AnimationStateMachine(sprites = { sheet })
        m.place(VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000))), 1)
        val before = m.frame(2, 600, DayPeriod.DAY)!!
        assertTrue(m.interact(InteractionResponse("art", listOf(AnimationId.PET_HEAD), null, 0), SceneId.HOME, 3))
        val frames = (4L..8_000L step 20).map { m.frame(it, 600, DayPeriod.DAY)!! }
        assertTrue(frames.any { it.sprite.source == "test-sheet+interaction" })
        assertTrue(frames.none { SceneFlag.FOOD_DONE in it.env.flags })
        assertTrue(frames.all { it.x == before.x && it.y == before.y })
    }

    @Test fun `held mug uses inherited composition instead of an incompatible standing sheet`() {
        val m = AnimationStateMachine(sprites = { sheet })
        m.place(VisualState(SceneId.OFFICE, SpotId.DESK, listOf(MicroAction(AnimationId.COFFEE_SIT, 1, 60_000, 60_000)),
            steadyFlags = setOf(SceneFlag.MUG_IN_HAND)), 1)
        m.frame(2, 600, DayPeriod.DAY)
        assertTrue(m.interact(InteractionResponse("mug", listOf(AnimationId.PET_HEAD), null, 0), SceneId.OFFICE, 3))
        val frames = (4L..8_000L step 20).map { m.frame(it, 600, DayPeriod.DAY)!! }
        assertTrue(frames.any { it.animation == AnimationId.PET_HEAD })
        assertTrue(frames.none { it.sprite.source == "test-sheet+interaction" })
        assertTrue(frames.all { SceneFlag.MUG_IN_HAND in it.env.flags })
    }
}
