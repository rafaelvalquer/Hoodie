package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun foodAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Café como ação completa ─────
        clip(AnimationId.REACH_MUG, loop = false) {
            f(160, I.copy(eyes = Eyes.LOOK_DOWN)); f(160, I.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
            f(200, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.MUG), MUG_PICKUP)
        }
        clip(AnimationId.DRINK, policy = FINISH_CYCLE) { drink(this, I) }
        clip(AnimationId.PUT_MUG, loop = false) {
            f(200, I.copy(rightArm = Arm.FORWARD_DOWN, item = Item.MUG, eyes = Eyes.LOOK_DOWN))
            f(160, I.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN), MUG_PUT); f(150, I)
        }
        clip(AnimationId.COFFEE_SIT, policy = FINISH_CYCLE) { drink(this, SIT) }
        clip(AnimationId.COFFEE_STAND, policy = FINISH_CYCLE) { drink(this, S) }
        clip(AnimationId.COFFEE_TIRED, policy = FINISH_CYCLE) { drink(this, I.copy(bob = 1), eyes = Eyes.SLEEPY, ears = Ears.DOWN, slow = 250) }
        clip(AnimationId.COFFEE_HAPPY, policy = FINISH_CYCLE) { drink(this, I.copy(blush = true), eyes = Eyes.HAPPY, ears = Ears.ALERT) }


        // ───── Comida ─────
        clip(AnimationId.LOOK_MENU, loop = false) {
            val m = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.MENU, eyes = Eyes.LOOK_DOWN)
            f(700, m); f(500, m.copy(eyes = Eyes.LOOK_LEFT)); f(600, m); f(500, m.copy(eyes = Eyes.LOOK_RIGHT, ears = Ears.ALERT)); f(300, SIT)
        }
        clip(AnimationId.WAIT_FOOD, loop = false) {
            f(600, SIT.copy(eyes = Eyes.LOOK_LEFT)); f(400, SIT); f(600, SIT.copy(eyes = Eyes.LOOK_RIGHT))
            f(500, SIT.copy(eyes = Eyes.HAPPY, ears = Ears.ALERT, lift = 1), FOOD_SERVED); f(300, SIT)
        }
        clip(AnimationId.EAT, policy = FINISH_CYCLE) {
            f(250, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.LOOK_DOWN))
            f(220, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_MOUTH, item = Item.FORK, mouth = Mouth.OPEN))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.CHEW))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.SMILE, headDy = 1))
        }
        clip(AnimationId.FINISH_FOOD, loop = false) {
            f(400, SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.HAPPY, blush = true))
            f(300, SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.HAPPY, blush = true, bob = 1))
            f(400, SIT.copy(eyes = Eyes.CLOSED), FOOD_DONE)
        }


}.clips.toMap()

    private fun drink(b: ClipDefinitions.Builder, base: HoodiePose, eyes: Eyes = Eyes.OPEN, ears: Ears = Ears.NORMAL, slow: Long = 0) {
        val p = base.copy(rightArm = Arm.HOLD_CHEST, item = Item.MUG, ears = ears)
        b.f(500 + slow, p.copy(eyes = eyes))
        b.f(400 + slow, p.copy(rightArm = Arm.HOLD_MOUTH, eyes = Eyes.CLOSED))
        b.f(300 + slow, p.copy(rightArm = Arm.HOLD_MOUTH, eyes = Eyes.CLOSED, bob = base.bob + 1))
        b.f(300, p.copy(eyes = if (eyes == Eyes.OPEN) Eyes.HAPPY else eyes))
        b.f(600 + slow, p.copy(eyes = eyes, bob = base.bob))
    }
