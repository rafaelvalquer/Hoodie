package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterComparisonRenderer
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderRequest
import com.hoodie.app.pixel.character.mirrored
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Posture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CharacterComparisonRendererTest {
    @Test fun renderRequestKeepsSpeciesMotionOutsideTheSharedBodyPose() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 0, profile)
        val request = CharacterRenderRequest(style, pose, profile.renderMotion())

        assertArrayEquals(
            CharacterPainter.paint(style, pose, profile.renderMotion()).image.pixels,
            CharacterPainter.render(request).image.pixels,
        )
    }

    @Test fun postureOverrideMapsToSharedBodyLegState() {
        assertEquals(Legs.STAND, CharacterPose().withPosture(Posture.STANDING).legs)
        assertEquals(Legs.SIT, CharacterPose().withPosture(Posture.SITTING).legs)
    }

    @Test fun labAppliesTheSameSidePoseAndSelectedOrientationToBothCharacters() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 0, SpeciesMotionProfiles.forCharacter(style))
            .copy(facing = Facing.SIDE)
        val leftHoodie = CharacterComparisonRenderer.hoodie(pose)
        val rightHoodie = CharacterComparisonRenderer.hoodie(pose, facingRight = true)
        val renderMotion = SpeciesMotionProfiles.forCharacter(style).renderMotion()
        val leftNpc = CharacterComparisonRenderer.character(style, pose, motion = renderMotion)
        val rightNpc = CharacterComparisonRenderer.character(style, pose, facingRight = true, motion = renderMotion)

        assertArrayEquals(leftHoodie.mirrored().image.pixels, rightHoodie.image.pixels)
        assertArrayEquals(leftNpc.mirrored().image.pixels, rightNpc.image.pixels)
        assertFalse(leftHoodie.image.pixels.contentEquals(rightHoodie.image.pixels))
        assertFalse(leftNpc.image.pixels.contentEquals(rightNpc.image.pixels))
        assertFalse("the same side pose must show side silhouettes", leftHoodie.image.pixels.contentEquals(leftNpc.image.pixels))
        // The Hoodie side frame is also rendered from the selected shared pose, including its gait phase.
        val staleFront = CharacterPainter.paint(com.hoodie.app.pixel.character.CharacterStyle.HOODIE, pose.copy(facing = Facing.FRONT))
        assertFalse(leftHoodie.image.pixels.contentEquals(staleFront.image.pixels))
    }
}
