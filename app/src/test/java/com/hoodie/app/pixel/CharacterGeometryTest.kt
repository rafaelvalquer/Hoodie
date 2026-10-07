package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterGeometry
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.character.species.EarStyle
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Posture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGeometryTest {
    @Test fun hoodieLegacyPosesResolveThroughCommonGeometryWithoutNormalizingOffsets() {
        AnimationId.entries.forEach { animation -> animation.frames.forEachIndexed { index, frame ->
            Facing.entries.forEach { facing ->
                val pose = frame.pose.copy(facing = facing)
                val geometry = CharacterGeometry.resolveHoodie(pose)
                val expectedUp = (if (pose.legs == Legs.SIT || pose.posture == Posture.SIT_FRONT) 5 else 0) + pose.bob
                val expectedBodyTop = (if (facing == Facing.SIDE) 28 else 33) + expectedUp
                val expectedBodyBottom = (if (facing == Facing.SIDE) 55 else 60) + expectedUp

                assertEquals("${animation.name}:$index:$facing legacy pose offset", expectedUp, geometry.torsoOffsetY)
                assertEquals("${animation.name}:$index:$facing legacy jump", pose.lift, geometry.lift)
                assertEquals("${animation.name}:$index:$facing legacy head", 9 + expectedUp + pose.headDy, geometry.headTop)
                assertEquals("${animation.name}:$index:$facing legacy head width", 34, geometry.headWidth)
                assertEquals("${animation.name}:$index:$facing legacy body top", expectedBodyTop, geometry.bodyTop)
                assertEquals("${animation.name}:$index:$facing legacy body bottom", expectedBodyBottom, geometry.bodyBottom)
            }
        } }
    }

    @Test fun registeredCharactersResolveScaleAndEarClearanceOnTheSharedCanvas() {
        NpcCharacterRegistry.all.forEach { style ->
            val geometry = CharacterGeometry.resolve(style, CharacterPose())
            val bodyWidth = geometry.bodyRight - geometry.bodyLeft + 1
            val pr = style.artProfile.proportions

            assertEquals("${style.id} shoulder width comes from its proportions", pr.shoulderWidth, bodyWidth)
            assertTrue("${style.id} ears keep clearance from the top", geometry.headTop >= 1 + style.species.earClearance)
            if (style.species.earStyle == EarStyle.LONG) assertTrue("${style.id} long ears reserve room", geometry.headTop >= 15)
            assertEquals("${style.id} head width", pr.headWidth, geometry.headWidth)
            assertTrue("${style.id} body width on 48px canvas", geometry.bodyLeft >= 0 && geometry.bodyRight < 48)
        }
    }

    @Test fun sideFacingNpcTorsoNarrowsWithoutChangingTheFrontScale() {
        NpcCharacterRegistry.all.forEach { style ->
            val front = CharacterGeometry.resolve(style, CharacterPose(facing = Facing.FRONT))
            val side = CharacterGeometry.resolve(style, CharacterPose(facing = Facing.SIDE))
            val frontWidth = front.bodyRight - front.bodyLeft + 1
            val sideWidth = side.bodyRight - side.bodyLeft + 1

            assertTrue("${style.id} side torso should be narrower than its front", sideWidth < frontWidth)
            assertTrue("${style.id} side torso should retain readable pixel volume", sideWidth >= 13)
            assertEquals("${style.id} front scale stays unchanged", style.artProfile.proportions.shoulderWidth, frontWidth)
        }
    }

    @Test fun seatedNpcGeometryMovesHeadTorsoAndHandsTogetherWithoutMovingGroundLine() {
        NpcCharacterRegistry.all.forEach { style ->
            val standing = CharacterGeometry.resolve(style, CharacterPose())
            val seated = CharacterGeometry.resolve(style, CharacterPose(legs = Legs.SIT, facing = Facing.SIDE))
            val expectedShift = com.hoodie.app.pixel.character.BodyLayout.SIT_DROP

            assertEquals("${style.id} seated torso shift", expectedShift, seated.torsoOffsetY)
            assertEquals("${style.id} seated body shift", standing.bodyTop + expectedShift, seated.bodyTop)
            assertEquals("${style.id} seated head shift", standing.headTop + expectedShift, seated.headTop)
            assertEquals("${style.id} seated body height", standing.bodyBottom - standing.bodyTop, seated.bodyBottom - seated.bodyTop)
        }
    }
}
