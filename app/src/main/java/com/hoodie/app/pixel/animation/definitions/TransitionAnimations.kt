package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun transitionAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Postura (squash discreto ao sentar/levantar) ─────
        val sitDown: Builder.() -> Unit = {
            f(120, S.copy(bob = 1, stringSwing = 1)); f(120, S.copy(bob = 2, eyes = Eyes.LOOK_DOWN))
            f(140, SIT.copy(bob = -1, stringSwing = 1), AnimationEvent.SIT); f(150, SIT.copy(bob = 1, stringSwing = -1)); f(120, SIT)
        }
        val standUp: Builder.() -> Unit = {
            f(120, SIT.copy(bob = 1)); f(120, SIT.copy(bob = -1, eyes = Eyes.LOOK_UP))
            f(140, S.copy(bob = 2, stringSwing = 1), STAND); f(120, S.copy(bob = -1, stringSwing = -1)); f(120, S)
        }
        clip(AnimationId.SIT_DOWN, loop = false, policy = FINISH_CYCLE, build = sitDown)
        clip(AnimationId.STAND_UP, loop = false, policy = FINISH_CYCLE, build = standUp)
        clip(AnimationId.SIT_TABLE, loop = false, policy = FINISH_CYCLE, build = sitDown)
        clip(AnimationId.STAND_TABLE, loop = false, policy = FINISH_CYCLE, build = standUp)


        // ───── Reações (herdam a postura atual) ─────
        clip(AnimationId.WAVE, loop = false, policy = FINISH_CYCLE) {
            f(160, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, ears = Ears.ALERT))
            f(160, I.copy(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, ears = Ears.ALERT))
            f(160, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN))
            f(160, I.copy(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN))
            f(200, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, I.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.HAPPY, loop = false, policy = FINISH_CYCLE) {
            // Squash & stretch discreto: abaixa 1px, pula 2px, volta.
            f(120, I.copy(bob = 1, eyes = Eyes.HAPPY)); f(140, I.copy(lift = 2, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, stringSwing = 1, ears = Ears.ALERT), SPARKLE)
            f(100, I.copy(lift = 1, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, stringSwing = -1)); f(90, I.copy(bob = 1, eyes = Eyes.HAPPY)); f(250, I.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.SMILE, loop = false, policy = IMMEDIATE) { f(900, I.copy(eyes = Eyes.HAPPY, blush = true)); f(150, I) }
        clip(AnimationId.SURPRISED, loop = false) {
            f(150, I.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, lift = 1, ears = Ears.ALERT)); f(350, I.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, ears = Ears.ALERT)); f(300, I.copy(eyes = Eyes.WIDE)); f(150, I)
        }
        clip(AnimationId.SHRUG, loop = false) {
            f(250, I.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT))
            f(350, I.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = -1, ears = Ears.RELAXED)); f(200, I)
        }
        clip(AnimationId.NOTICE, loop = false, policy = IMMEDIATE) {
            f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(250, I.copy(ears = Ears.ALERT, eyes = Eyes.WIDE)); f(500, I.copy(ears = Ears.ALERT))
        }
        clip(AnimationId.GLANCE, loop = false, policy = IMMEDIATE) { f(150, I.copy(ears = Ears.TWITCH_RIGHT)); f(450, I); f(100, I.copy(eyes = Eyes.HALF)) }
        clip(AnimationId.EAR_FLICK, loop = false, policy = IMMEDIATE) {
            f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(100, I); f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(200, I)
        }
        clip(AnimationId.NOD, loop = false) { f(150, I.copy(headDy = 1, eyes = Eyes.HAPPY)); f(150, I); f(150, I.copy(headDy = 1, eyes = Eyes.HAPPY)); f(200, I) }

}.clips.toMap()


