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
        assertEquals("canonical side beak reaches the left of the head", duck.palette.accent, sideDuckFrame.image[4, 20])
        assertEquals("right-facing movement mirrors the canonical side", duck.palette.accent, sideDuckFrame.mirrored().image[43, 20])
    }

    @Test fun bulldogWalkProfileUsesTheSideSilhouetteAndProjectsItsNoseForward() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 0, motion)
        val front = CharacterPainter.paint(style, pose.copy(facing = Facing.FRONT), motion.renderMotion()).image
        val side = CharacterPainter.paint(style, pose.copy(facing = Facing.SIDE), motion.renderMotion()).image

        assertFalse("Bulldog's profile must replace the frontal head silhouette", front.pixels.contentEquals(side.pixels))
        assertEquals("the Bulldog's nose should lead the canonical left-facing profile", style.palette.outline, side[2, 19])
        assertEquals("the muzzle should stay within the top half of the head", 0, side[2, 40])
    }
}
