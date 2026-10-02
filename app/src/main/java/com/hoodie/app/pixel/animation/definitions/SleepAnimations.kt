package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun sleepAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Sono ─────
        clip(AnimationId.BED_SIT, loop = false, policy = FINISH_CYCLE) {
            f(500, SIT.copy(eyes = Eyes.SLEEPY, ears = Ears.RELAXED), BED_ENTER); f(500, SIT.copy(eyes = Eyes.SLEEPY, ears = Ears.RELAXED, bob = 1))
        }
        // Sentado → apoia a pata → inclina o corpo → afunda na cama → só a cabeça no travesseiro.
        clip(AnimationId.BED_LIE_DOWN, loop = false, policy = FINISH_CYCLE) {
            val s = SIT.copy(ears = Ears.RELAXED)
            f(240, s.copy(eyes = Eyes.SLEEPY, leftArm = Arm.FORWARD_DOWN))
            f(220, s.copy(eyes = Eyes.HALF, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = 1))
            f(220, s.copy(eyes = Eyes.HALF, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = 3, headDy = 1))
            f(220, s.copy(eyes = Eyes.CLOSED, bob = 6))
            f(250, HoodiePose(headOnly = true, eyes = Eyes.CLOSED, bob = -1, ears = Ears.RELAXED)); f(300, HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED))
        }
        clip(AnimationId.SLEEP, policy = PLAY_EXIT) {
            // Nunca totalmente parado: respiração + uma orelha que mexe no meio do sono.
            val z = HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED)
            f(900, z); f(800, z); f(900, z.copy(bob = 1)); f(250, z.copy(bob = 1, ears = Ears.TWITCH_LEFT)); f(650, z.copy(bob = 1)); f(900, z)
        }
        clip(AnimationId.SLEEP_TURN, loop = false, policy = PLAY_EXIT) {
            val z = HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED)
            f(300, z.copy(headTilt = 1)); f(250, z.copy(headTilt = 1, ears = Ears.TWITCH_RIGHT)); f(500, z.copy(headTilt = 1, bob = 1)); f(400, z)
        }
        clip(AnimationId.WAKE_EYES, loop = false, policy = FINISH_CYCLE) {
            val h = HoodiePose(headOnly = true)
            f(400, h.copy(eyes = Eyes.HALF, ears = Ears.RELAXED)); f(200, h.copy(eyes = Eyes.CLOSED)); f(300, h.copy(eyes = Eyes.HALF)); f(500, h.copy(eyes = Eyes.OPEN, ears = Ears.TWITCH_LEFT))
        }
        clip(AnimationId.BED_EXIT, loop = false, policy = FINISH_CYCLE) {
            f(200, SIT.copy(eyes = Eyes.HALF)); f(150, SIT.copy(bob = -1)); f(160, S.copy(bob = 2, eyes = Eyes.HALF), BED_EXIT, STAND); f(150, S.copy(bob = -1, stringSwing = 1)); f(120, S)
        }
        clip(AnimationId.WAKE_UP, loop = false) {
            f(300, S.copy(eyes = Eyes.SLEEPY)); f(250, S.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP))
            f(400, S.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP, bob = -1, lift = 1)); f(250, S.copy(eyes = Eyes.HAPPY)); f(200, S)
        }
        clip(AnimationId.NAP_SIT) { f(800, SIT.copy(eyes = Eyes.CLOSED, ears = Ears.RELAXED)); f(800, SIT.copy(eyes = Eyes.CLOSED, ears = Ears.RELAXED, bob = 1)) }


}.clips.toMap()


