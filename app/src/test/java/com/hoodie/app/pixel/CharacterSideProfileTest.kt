package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.mirrored
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Facing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSideProfileTest {
    @Test fun everyNpcUsesSpeciesSpecificSideHeadAndMuzzle() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            val base = NpcMotionController.pose(NpcAnimation.IDLE, 0, 0, motion)
            val front = CharacterPainter.paint(style, base.copy(facing = Facing.FRONT), motion.renderMotion()).image
            val side = CharacterPainter.paint(style, base.copy(facing = Facing.SIDE), motion.renderMotion()).image
            assertFalse("${style.id} side view must change the silhouette and profile", front.pixels.contentEquals(side.pixels))
        }

        val duck = NpcCharacterRegistry.DUCK_SLEEPY
        val duckMotion = SpeciesMotionProfiles.forCharacter(duck)
        val sideDuckFrame = CharacterPainter.paint(duck, NpcMotionController.pose(
            NpcAnimation.IDLE, 0, 0, SpeciesMotionProfiles.forCharacter(duck),
        ).copy(facing = Facing.SIDE), duckMotion.renderMotion())
        val img = sideDuckFrame.image
        val leftmostAccent = (0 until img.width).first { x -> (0 until img.height).any { y -> img[x, y] == duck.palette.accent && y < 45 } }
        val leftmostHead = (0 until img.width).first { x -> (0 until 40).any { y -> img[x, y] == duck.palette.fur } }
        assertTrue("canonical side beak reaches the left of the head ($leftmostAccent < $leftmostHead)", leftmostAccent < leftmostHead)
        val mirrored = sideDuckFrame.mirrored().image
        assertEquals("right-facing movement mirrors the canonical side", duck.palette.accent, mirrored[img.width - 1 - leftmostAccent, (0 until 45).first { img[leftmostAccent, it] == duck.palette.accent }])
    }

    @Test fun bulldogWalkProfileUsesTheSideSilhouetteAndProjectsItsNoseForward() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 0, motion)
        val front = CharacterPainter.paint(style, pose.copy(facing = Facing.FRONT), motion.renderMotion()).image
        val side = CharacterPainter.paint(style, pose.copy(facing = Facing.SIDE), motion.renderMotion()).image

        assertFalse("Bulldog's profile must replace the frontal head silhouette", front.pixels.contentEquals(side.pixels))
        val l = com.hoodie.app.pixel.character.BodyLayout.resolve(style, pose.copy(facing = Facing.SIDE))
        val headRows = l.headTop..l.headBottom
        val leftmost = (0 until side.width).first { x -> headRows.any { y -> side[x, y] ushr 24 != 0 } }
        val noseRow = headRows.first { y -> side[leftmost, y] ushr 24 != 0 }
        assertTrue("the Bulldog's nose should lead the canonical left-facing profile (x=$leftmost)", leftmost <= 6)
        assertTrue("the muzzle should sit in the lower half of the head", noseRow >= (l.headTop + l.headBottom) / 2 - 4)
    }
}
