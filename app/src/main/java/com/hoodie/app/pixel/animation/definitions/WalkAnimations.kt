package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun walkAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Locomoção ─────
        clip(AnimationId.WALK, policy = IMMEDIATE, directional = true) { walkFrames(this, backpack = false) }
        clip(AnimationId.WALK_BACKPACK, policy = IMMEDIATE, directional = true) { walkFrames(this, backpack = true) }
        clip(AnimationId.WALK_STOP, loop = false, directional = true) {
            f(110, S.copy(stringSwing = 1, headDy = 1)); f(110, S.copy(stringSwing = -1)); f(90, S)
        }
        clip(AnimationId.TURN, loop = false, policy = IMMEDIATE, directional = true) {
            f(140, S.copy(ears = Ears.ALERT)); f(140, S.copy(bob = 1, stringSwing = 1))
        }
        clip(AnimationId.RUN) {
            f(100, HoodiePose(legs = Legs.RUN_A, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1, stringSwing = 2), FOOTSTEP)
            f(90, HoodiePose(eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, stringSwing = 1))
            f(100, HoodiePose(legs = Legs.RUN_B, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1, stringSwing = -2), FOOTSTEP)
            f(90, HoodiePose(eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, stringSwing = -1))
        }
        clip(AnimationId.RUN_START, loop = false) {
            f(160, S.copy(bob = 1, eyes = Eyes.FOCUSED)); f(120, HoodiePose(legs = Legs.STEP_LEFT, eyes = Eyes.FOCUSED, stringSwing = 1))
            f(110, HoodiePose(legs = Legs.RUN_A, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, eyes = Eyes.FOCUSED, stringSwing = 2))
        }
        clip(AnimationId.RUN_STOP, loop = false) {
            f(110, HoodiePose(legs = Legs.RUN_B, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, mouth = Mouth.OPEN, stringSwing = -2))
            f(130, HoodiePose(legs = Legs.STEP_RIGHT, mouth = Mouth.OPEN, stringSwing = 2, bob = 1))
            f(160, S.copy(mouth = Mouth.OPEN, stringSwing = -1, bob = 1)); f(200, S.copy(eyes = Eyes.CLOSED))
        }


}.clips.toMap()

    // Caminhada: contato, descida, passagem, subida — para cada pé.
private val WALK_MS = longArrayOf(100, 80, 80, 100, 100, 80, 80, 100)
private val WALK_BOB = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
private val WALK_STRINGS = intArrayOf(0, 1, 1, 0, 0, -1, -1, 0)


    private fun walkFrames(b: ClipDefinitions.Builder, backpack: Boolean) {
        for (i in 0 until 8) {
            val prev = (i + 7) % 8
            b.f(
                WALK_MS[i],
                HoodiePose(
                    legs = Legs.WALK, stride = i, bob = WALK_BOB[i],
                    // A cabeça e a mochila chegam 1 frame atrasadas (movimento secundário).
                    headDy = WALK_BOB[prev] - WALK_BOB[i],
                    stringSwing = WALK_STRINGS[i],
                    leftArm = if (i < 4) Arm.SWING_BACK else Arm.SWING_FRONT,
                    rightArm = if (i < 4) Arm.SWING_FRONT else Arm.SWING_BACK,
                    backpack = backpack, backpackDy = if (backpack) WALK_BOB[prev] - WALK_BOB[i] else 0,
                ),
                *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray<AnimationEvent>()),
            )
        }
    }
