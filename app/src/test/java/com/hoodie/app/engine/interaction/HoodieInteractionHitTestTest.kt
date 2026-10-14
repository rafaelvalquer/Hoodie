package com.hoodie.app.engine.interaction

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionHitTestTest {
    @Test fun `pixel hit accounts for centered margins scale and transparency`() {
        val m = AnimationStateMachine()
        m.place(VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1))), 1)
        val f = m.frame(1, 600, DayPeriod.DAY)!!
        val image = f.sprite.image
        val opaque = image.pixels.indexOfFirst { it.ushr(24) != 0 }
        val transparent = image.pixels.indexOfFirst { it == 0 }
        fun hit(index: Int): Boolean {
            val x = 10 + 2 * (f.x - f.sprite.anchors.feet.x + index % image.width) + 1f
            val y = 20 + 2 * (f.y - f.sprite.anchors.feet.y + index / image.width) + 1f
            return HoodieInteractionHitTest.hit(f, x, y, 500, 680)
        }
        assertTrue(hit(opaque))
        assertFalse(hit(transparent))
        assertFalse(HoodieInteractionHitTest.hit(f, 1f, 1f, 500, 680))
        assertFalse(HoodieInteractionHitTest.hit(f.copy(fade = 0f), 250f, 340f, 500, 680))
        assertFalse(HoodieInteractionHitTest.hit(null, 1f, 1f, 500, 680))
        val x = f.x - f.sprite.anchors.feet.x + opaque % image.width + .5f
        val y = f.y - f.sprite.anchors.feet.y + opaque / image.width + .5f
        assertFalse(HoodieInteractionHitTest.hit(f, x - 1, y, 240, 320))
        assertTrue(HoodieInteractionHitTest.nearHit(f, x - 1, y, 240, 320))
        assertFalse(HoodieInteractionHitTest.nearHit(f, x - 1, y, 240, 320, 0))
    }
}
