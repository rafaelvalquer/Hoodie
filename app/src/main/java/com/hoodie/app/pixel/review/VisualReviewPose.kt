package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcFrame
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcReaction
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

/**
 * Checkpoints semânticos e determinísticos partilhados pelo Pixel Lab e pela matriz.
 * Os tempos foram escolhidos dentro das janelas de cada clip, não dependem do relógio.
 */
enum class VisualReviewPose(
    val animation: NpcAnimation,
    val elapsedMs: Long,
    val label: String,
    val reaction: NpcReaction? = null,
    val poseOverride: (CharacterPose) -> CharacterPose = { it },
) {
    IDLE_NEUTRAL(NpcAnimation.IDLE, 500, "IDLE NEUTRAL", poseOverride = { it.copy(eyes = Eyes.OPEN, mouth = Mouth.SMILE) }),
    IDLE_BLINK(NpcAnimation.IDLE, 0, "IDLE BLINK", poseOverride = { it.copy(eyes = Eyes.CLOSED) }),
    LOOK_LEFT(NpcAnimation.LOOK, 300, "LOOK LEFT", poseOverride = { it.copy(eyes = Eyes.LOOK_LEFT, headTilt = -1) }),
    LOOK_RIGHT(NpcAnimation.LOOK, 1_700, "LOOK RIGHT", poseOverride = { it.copy(eyes = Eyes.LOOK_RIGHT, headTilt = 1) }),
    TALK_CLOSED(NpcAnimation.TALK, 200, "TALK CLOSED", poseOverride = { it.copy(mouth = Mouth.FLAT, rightArm = Arm.DOWN) }),
    TALK_OPEN(NpcAnimation.TALK, 0, "TALK OPEN", poseOverride = { it.copy(mouth = Mouth.OPEN, rightArm = Arm.DOWN) }),
    TALK_GESTURE(NpcAnimation.TALK, 1_100, "TALK GESTURE", poseOverride = { it.copy(mouth = Mouth.OPEN, rightArm = Arm.FORWARD_UP) }),
    STAND_NEUTRAL(NpcAnimation.STAND, 500, "STAND", poseOverride = { it.copy(legs = Legs.STAND, eyes = Eyes.OPEN) }),
    SIT_START(NpcAnimation.SIT, 0, "SIT START"),
    SIT_CONTACT(NpcAnimation.SIT, 500, "SIT CONTACT"),
    SIT_FINAL(NpcAnimation.SIT, 640, "SIT FINAL", poseOverride = { it.copy(legs = Legs.SIT) }),
    PHONE_IDLE(NpcAnimation.SIT_PHONE, 400, "PHONE IDLE", poseOverride = { it.copy(item = Item.PHONE) }),
    PHONE_INTERACT(NpcAnimation.SIT_PHONE, 1_600, "PHONE INTERACT", poseOverride = { it.copy(item = Item.PHONE, leftArm = Arm.HOLD_CHEST) }),
    EAT_PICK(NpcAnimation.SIT_EAT, 700, "EAT PICK", poseOverride = { it.copy(item = Item.FORK, rightArm = Arm.FORWARD_DOWN) }),
    EAT_MOUTH(NpcAnimation.SIT_EAT, 1_300, "EAT MOUTH", poseOverride = { it.copy(item = Item.FORK, rightArm = Arm.HOLD_MOUTH, mouth = Mouth.OPEN) }),
    EAT_CHEW(NpcAnimation.SIT_EAT, 1_700, "EAT CHEW", poseOverride = { it.copy(item = Item.FORK, mouth = Mouth.CHEW) }),
    SLEEP_RELAX(NpcAnimation.SIT_SLEEP, 400, "SLEEP RELAX", poseOverride = { it.copy(eyes = Eyes.CLOSED, ears = Ears.DOWN) }),
    SLEEP_DROP(NpcAnimation.SIT_HEAD_DROP, 700, "SLEEP DROP", poseOverride = { it.copy(headDy = 3, eyes = Eyes.CLOSED) }),
    REACTION_SMILE(NpcAnimation.REACTION, 300, "REACTION SMILE", NpcReaction.SMILE),
    REACTION_SURPRISED(NpcAnimation.REACTION, 300, "REACTION SURPRISED", NpcReaction.SURPRISED),
    REACTION_WAVE(NpcAnimation.REACTION, 500, "REACTION WAVE", NpcReaction.WAVE),
}

object NpcVisualReviewFrames {
    /** Seed zero is an explicit part of the review contract: the same request means the same pixels. */
    fun resolve(style: com.hoodie.app.pixel.character.CharacterStyle, checkpoint: VisualReviewPose, facing: Facing? = null): NpcFrame {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val frame = NpcPoseLibrary.frame(
            animation = checkpoint.animation,
            elapsedMs = checkpoint.elapsedMs,
            seed = 0,
            motion = motion,
            scale = 1f,
            reaction = checkpoint.reaction,
        )
        val pose = checkpoint.poseOverride(frame.pose).let { if (facing == null) it else it.copy(facing = facing) }
        return frame.copy(pose = pose)
    }

    val reviewSpecies = listOf(
        NpcCharacterRegistry.BULLDOG_EXEC,
        NpcCharacterRegistry.DOG_WORKER,
        NpcCharacterRegistry.RABBIT_ANALYST,
        NpcCharacterRegistry.MOUSE_COMMUTER,
        NpcCharacterRegistry.DUCK_SLEEPY,
        NpcCharacterRegistry.RACCOON_COMMUTER,
        NpcCharacterRegistry.CAT_COLLEAGUE,
    )
}
