package com.hoodie.app.engine.interaction

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.*
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionVisibilityTest {
    @Test fun `foreground object occludes target even when it paints the same color as Hoodie`() {
        val m = AnimationStateMachine()
        m.place(VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1))), 1)
        val frame = m.frame(1, 600, DayPeriod.DAY)!!
        val index = frame.sprite.image.pixels.indexOfFirst { it.ushr(24) != 0 }
        val x = frame.x - frame.sprite.anchors.feet.x + index % frame.sprite.image.width
        val y = frame.y - frame.sprite.anchors.feet.y + index / frame.sprite.image.width
        val color = frame.sprite.image.pixels[index]
        val scene = object : PixelScene(SceneId.UNKNOWN) {
            override val spots = mapOf(SpotId.CENTER to Spot(frame.x, frame.y))
            override val defaultSpot = SpotId.CENTER
            override fun drawBackground(b: PixelBuffer, env: SceneEnv) = b.fill(0xFF000000.toInt())
            override fun ambientNpcs(env: SceneEnv) = emptyList<com.hoodie.app.pixel.npc.AmbientNpcSlot>()
            override fun props() = listOf(Prop(frame.y + 1) { buffer, _, _ -> buffer.set(x, y, color) })
        }
        val rendered = frame.copy(scene = scene)
        val renderer = SceneRenderer(true)
        renderer.render(rendered, 1)
        assertTrue(HoodieInteractionHitTest.hit(frame, x + .5f, y + .5f, 240, 320))
        assertFalse(HoodieInteractionHitTest.hit(rendered.copy(interactionHitMask = renderer.lastInteractionHitMask), x + .5f, y + .5f, 240, 320))
        renderer.dispose()
    }

    @Test fun `drawing ownership follows opaque spans and transparent pixels do not steal the target`() {
        val b = PixelBuffer(4, 4)
        val mask = ByteArray(16)
        b.drawOwnership = mask
        b.drawOwner = 1
        b.box(0, 0, 3, 3, 0xFFFFFFFF.toInt())
        b.drawOwner = 0
        b.blit(PixelBuffer(1, 1), 1, 1)
        assertEquals(1, mask[5].toInt())
        b.blit(PixelBuffer(1, 1).apply { fill(0xFFFFFFFF.toInt()) }, 1, 1)
        assertEquals(0, mask[5].toInt())
        assertEquals(1, mask[6].toInt())
    }
}
