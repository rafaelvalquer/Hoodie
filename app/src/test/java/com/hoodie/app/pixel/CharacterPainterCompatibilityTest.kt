package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.mirrored
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPainterCompatibilityTest {
    @Test fun legacyAndSharedPaintersExposeTheSameLogicalCanvasAndGroundAnchor() {
        assertEquals(48, CharacterCanvas.WIDTH)
        assertEquals(72, CharacterCanvas.HEIGHT)
        assertEquals(CharacterCanvas.WIDTH, HoodiePainter.WIDTH)
        assertEquals(CharacterCanvas.HEIGHT, HoodiePainter.HEIGHT)
        assertEquals(CharacterCanvas.FEET, HoodiePainter.FEET)
        assertEquals(CharacterCanvas.FEET, CharacterPainter.FEET)
    }

    @Test fun sharedPainterPreservesLegacyHoodiePixelsAndAnchorsAcrossAllClipsAndViews() {
        AnimationId.entries.forEach { id -> id.frames.forEachIndexed { index, frame -> Facing.entries.forEach { facing ->
            val pose = frame.pose.copy(facing = facing)
            val old = HoodiePainter.painted(pose)
            val shared = CharacterPainter.paint(CharacterStyle.HOODIE, pose)
            assertArrayEquals("pixels changed for ${id.name}:$index:$facing", old.image.pixels, shared.image.pixels)
            assertEquals("anchors changed for ${id.name}:$index:$facing", old.anchors,
                com.hoodie.app.pixel.sprite.SpriteAnchors(
                    rightHand = shared.anchors.rightHand, leftHand = shared.anchors.leftHand,
                    head = shared.anchors.head, back = shared.anchors.back, feet = shared.anchors.feet,
                    seatHip = shared.anchors.seatHip,
                ))
        } } }
    }

    @Test fun npcFrameCacheReusesEquivalentPixelsAndEvictsWithinItsBound() {
        CharacterPainter.clearFrameCacheForTest()
        val style = NpcCharacterRegistry.RABBIT_ANALYST
        val pose = CharacterPose()
        val first = CharacterPainter.paint(style, pose)
        assertSame(first, CharacterPainter.paint(style, pose))
        assertTrue(CharacterPainter.cacheStats().hits >= 1)

        repeat(160) { stride -> CharacterPainter.paint(style, pose.copy(stride = stride)) }
        val stats = CharacterPainter.cacheStats()
        assertEquals(stats.capacity, stats.entries)
        assertTrue("cache evicts least-recently-used frames", stats.evictions > 0)
        assertNotSame(first, CharacterPainter.paint(style, pose))
    }

    @Test fun npcSideOrientationsAreReusedWithoutChangingPixels() {
        CharacterPainter.clearFrameCacheForTest()
        val style = NpcCharacterRegistry.RABBIT_ANALYST
        val pose = CharacterPose(facing = Facing.SIDE)
        val motion = CharacterRenderMotion()
        val left = CharacterPainter.paintForNpc(style, pose, motion, facingRight = false)
        val right = CharacterPainter.paintForNpc(style, pose, motion, facingRight = true)

        assertSame(left, CharacterPainter.paintForNpc(style, pose, motion, facingRight = false))
        assertSame(right, CharacterPainter.paintForNpc(style, pose, motion, facingRight = true))
        assertArrayEquals(left.mirrored().image.pixels, right.image.pixels)
        assertEquals(1, CharacterPainter.cacheStats().entries)
    }
}
