package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
                ))
        } } }
    }
}
