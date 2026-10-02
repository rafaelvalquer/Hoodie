package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun phoneAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Celular ─────
        clip(AnimationId.PHONE_TAKE, loop = false) {
            f(150, I.copy(eyes = Eyes.LOOK_DOWN)); f(150, I.copy(rightArm = Arm.FORWARD_DOWN)); f(200, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
        }
        clip(AnimationId.PHONE_READ, policy = IMMEDIATE) { phone(this, I) }
        clip(AnimationId.PHONE_SCROLL, policy = FINISH_FRAME) {
            val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
            f(250, p.copy(leftArm = Arm.FORWARD_UP)); f(250, p.copy(leftArm = Arm.HOLD_CHEST)); f(500, p.copy(leftArm = Arm.FORWARD_UP)); f(300, p.copy(leftArm = Arm.HOLD_CHEST, bob = 1))
        }
        clip(AnimationId.PHONE_TYPE, policy = FINISH_FRAME) {
            val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN, mouth = Mouth.FLAT)
            f(120, p.copy(leftArm = Arm.FORWARD_UP)); f(120, p.copy(leftArm = Arm.HOLD_CHEST)); f(120, p.copy(leftArm = Arm.FORWARD_UP)); f(300, p.copy(leftArm = Arm.HOLD_CHEST))
        }
        val react: (Eyes, Mouth, Ears) -> Builder.() -> Unit = { eyes, mouth, ears ->
            {
                val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
                f(200, p); f(700, p.copy(eyes = eyes, mouth = mouth, ears = ears, lift = if (eyes == Eyes.WIDE) 1 else 0)); f(300, p)
            }
        }
        clip(AnimationId.PHONE_REACT_SMILE, loop = false, build = react(Eyes.HAPPY, Mouth.SMILE, Ears.ALERT))
        clip(AnimationId.PHONE_REACT_MEH, loop = false, build = react(Eyes.HALF, Mouth.FLAT, Ears.RELAXED))
        clip(AnimationId.PHONE_REACT_WOW, loop = false, build = react(Eyes.WIDE, Mouth.OPEN, Ears.ALERT))
        clip(AnimationId.PHONE_PUT, loop = false) {
            f(150, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE)); f(150, I.copy(rightArm = Arm.FORWARD_DOWN), PHONE_PUT); f(150, I)
        }
        clip(AnimationId.PHONE_SIT, policy = IMMEDIATE) { phone(this, SIT) }
        clip(AnimationId.PHONE_STAND, policy = IMMEDIATE) { phone(this, S) }


}.clips.toMap()

internal fun phone(b: ClipDefinitions.Builder, base: HoodiePose) {
        val p = base.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
        b.f(700, p); b.f(500, p.copy(bob = 1)); b.f(700, p); b.f(400, p.copy(eyes = Eyes.HAPPY))
    }
