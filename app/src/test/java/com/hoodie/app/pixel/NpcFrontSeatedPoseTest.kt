package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import org.junit.Assert.*
import org.junit.Test

class NpcFrontSeatedPoseTest {
    @Test fun seatedInteractionsUseFrontFacingSeatedPosesAndKeepTheirProps() {
        val animations = listOf(NpcAnimation.TYPE, NpcAnimation.SIT_PHONE, NpcAnimation.SIT_EAT,
            NpcAnimation.SIT_DRINK, NpcAnimation.SIT_LOOK, NpcAnimation.SIT_READ_MENU, NpcAnimation.TALK)
        val style = NpcCharacterRegistry.CAT_GUEST
        for (animation in animations) {
            val frame = NpcPoseLibrary.frame(animation, 1_300, 42, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT)
            assertEquals("$animation orientation", Facing.FRONT, frame.pose.facing)
            assertEquals("$animation posture", Legs.SIT, frame.pose.legs)
        }
        assertEquals(Item.NONE, NpcPoseLibrary.frame(NpcAnimation.TYPE, 700, 2, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT).pose.item)
        assertEquals(Item.PHONE, NpcPoseLibrary.frame(NpcAnimation.SIT_PHONE, 800, 2, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT).pose.item)
        assertEquals(Item.FORK, NpcPoseLibrary.frame(NpcAnimation.SIT_EAT, 800, 2, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT).pose.item)
        assertEquals(Item.GLASS, NpcPoseLibrary.frame(NpcAnimation.SIT_DRINK, 800, 2, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT).pose.item)
        assertEquals(Item.MENU, NpcPoseLibrary.frame(NpcAnimation.SIT_READ_MENU, 800, 2, SpeciesMotionProfiles.forCharacter(style), seated = true, facing = Facing.FRONT).pose.item)
    }

    @Test fun frontTypingAlternatesHandsWithoutTheOldPencilProp() {
        val profile = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.RABBIT_ANALYST)
        val poses = (0L..1_200L step 260).map { NpcPoseLibrary.frame(NpcAnimation.TYPE, it, 4, profile, seated = true, facing = Facing.FRONT).pose }
        assertTrue(poses.any { it.leftArm == Arm.FORWARD_DOWN && it.rightArm == Arm.DOWN })
        assertTrue(poses.any { it.rightArm == Arm.FORWARD_DOWN && it.leftArm == Arm.DOWN })
        assertTrue(poses.any { it.rightArm == Arm.FORWARD_DOWN && it.leftArm == Arm.FORWARD_DOWN })
        assertTrue(poses.all { it.item == Item.NONE })
    }
}
