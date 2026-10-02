package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun idleAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Idle ─────
        // Respiração lenta em 8 tempos (inspira, segura, expira) com cordões e cabeça
        // atrasados. A arte final usa os mesmos 8 tempos; a piscada vem do overlay.
        val breathe: Builder.(HoodiePose) -> Unit = { base ->
            f(650, base); f(450, base.copy(stringSwing = 1)); f(600, base.copy(bob = 1)); f(450, base.copy(bob = 1, headDy = -1))
            f(600, base.copy(bob = 1)); f(450, base.copy(stringSwing = -1)); f(500, base); f(400, base)
        }
        clip(AnimationId.IDLE, policy = IMMEDIATE) { breathe(S) }
        clip(AnimationId.IDLE_SIT, policy = IMMEDIATE) { breathe(SIT) }
        clip(AnimationId.IDLE_LOOK, loop = false, policy = IMMEDIATE) {
            f(900, I.copy(eyes = Eyes.LOOK_LEFT)); f(250, I); f(900, I.copy(eyes = Eyes.LOOK_RIGHT)); f(300, I)
        }
        clip(AnimationId.IDLE_EAR, loop = false, policy = IMMEDIATE) {
            f(200, I.copy(ears = Ears.TWITCH_LEFT)); f(250, I); f(200, I.copy(ears = Ears.TWITCH_RIGHT)); f(250, I); f(600, I.copy(ears = Ears.ALERT))
        }
        // Pata → cabeça, coça três vezes, pata desce (8 frames).
        clip(AnimationId.IDLE_SCRATCH, loop = false) {
            f(180, I.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP))
            for (k in 0 until 3) {
                f(160, I.copy(rightArm = Arm.HEAD, eyes = Eyes.CLOSED, ears = Ears.TWITCH_RIGHT))
                f(160, I.copy(rightArm = Arm.HEAD, eyes = Eyes.HAPPY, headDy = 1))
            }
            f(200, I)
        }
        clip(AnimationId.IDLE_PHONE) { phone(this, I) }
        clip(AnimationId.LOOK_AROUND, policy = IMMEDIATE) {
            f(700, S.copy(eyes = Eyes.LOOK_LEFT)); f(300, S); f(700, S.copy(eyes = Eyes.LOOK_RIGHT)); f(300, S.copy(ears = Ears.TWITCH_LEFT)); f(500, S.copy(eyes = Eyes.LOOK_UP))
        }
        clip(AnimationId.YAWN, loop = false) {
            f(200, I.copy(eyes = Eyes.HALF, mouth = Mouth.FLAT, ears = Ears.RELAXED))
            f(500, I.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, bob = -1, headDy = -1, ears = Ears.RELAXED))
            f(400, I.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, ears = Ears.RELAXED))
            f(200, I.copy(eyes = Eyes.HALF, ears = Ears.RELAXED)); f(150, I)
        }
        clip(AnimationId.STRETCH, loop = false) {
            f(160, S.copy(bob = 1)); f(250, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED))
            f(400, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, bob = -1, lift = 1, stringSwing = 1))
            f(250, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, S.copy(eyes = Eyes.HAPPY, stringSwing = -1))
        }
        clip(AnimationId.STRETCH_SIT, loop = false) {
            f(250, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED))
            f(400, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, mouth = Mouth.OPEN, bob = -1))
            f(250, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, SIT.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.THINK) { f(800, SIT.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT)); f(700, SIT.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1)) }
        clip(AnimationId.THINK_STAND) { f(800, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT)); f(700, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1)) }


}.clips.toMap()


