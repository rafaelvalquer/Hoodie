package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Point
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAnchorsTest {
    @Test fun hoodieMouthAnchorTracksItsFrontAndSideMuzzleWithoutChangingTheSprite() {
        val front = CharacterPainter.paint(CharacterStyle.HOODIE, CharacterPose(facing = Facing.FRONT))
        val side = CharacterPainter.paint(CharacterStyle.HOODIE, CharacterPose(facing = Facing.SIDE))

        assertTrue(front.anchors.mouth == Point(24, 25))
        assertTrue(side.anchors.mouth == Point(7, 25))
        assertTrue(front.image.pixels.any { it ushr 24 != 0 })
        assertTrue(side.image.pixels.any { it ushr 24 != 0 })
    }

    @Test fun hoodieAnchorsFitTheCanvasAcrossEveryAnimationAndView() {
        AnimationId.entries.forEach { animation -> animation.frames.forEachIndexed { index, frame ->
            Facing.entries.forEach { facing ->
                val rendered = CharacterPainter.paint(CharacterStyle.HOODIE, frame.pose.copy(facing = facing))
                listOf(
                    rendered.anchors.feet, rendered.anchors.head,
                    rendered.anchors.leftHand, rendered.anchors.rightHand,
                    rendered.anchors.mouth,
                ).forEach { point ->
                    assertTrue("Hoodie ${animation.name} frame $index $facing anchor $point", point.x in 0 until 48 && point.y in 0 until 72)
                }
            }
        } }
    }

    @Test fun allRegisteredCharacterAnchorsFitTheCanvasForEveryViewAndUniversalPose() {
        NpcCharacterRegistry.all.forEach { style ->
            val poses = NpcAnimation.entries.map { NpcMotionController.pose(it, 575, 3, SpeciesMotionProfiles.forCharacter(style)) } + CharacterPose()
            poses.forEach { pose -> Facing.entries.forEach { facing ->
                val frame = CharacterPainter.paint(style, pose.copy(facing = facing), SpeciesMotionProfiles.forCharacter(style).renderMotion())
                listOf(frame.anchors.feet, frame.anchors.head, frame.anchors.leftHand, frame.anchors.rightHand, frame.anchors.mouth).forEach { point ->
                    assertTrue("${style.id} $facing anchor $point", point.x in 0 until frame.image.width && point.y in 0 until frame.image.height)
                }
            } } 
        }
    }

    @Test fun everyNpcSideViewKeepsItsFaceAndMouthAnchorOnTheLeadingSide() {
        NpcCharacterRegistry.all.forEach { style ->
            val profile = SpeciesMotionProfiles.forCharacter(style)
            val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 0, profile).copy(facing = Facing.SIDE)
            val frame = CharacterPainter.paint(style, pose, profile.renderMotion())

            assertTrue("${style.id} side-facing mouth anchor should lead the head", frame.anchors.mouth.x < frame.anchors.head.x)
            assertTrue(
                "${style.id} side-facing mouth anchor should stay inside the character height",
                frame.anchors.mouth.y in 0 until frame.image.height,
            )
        }
    }
}
