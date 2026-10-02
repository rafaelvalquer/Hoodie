package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun gymAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Academia ─────
        clip(AnimationId.GYM_WARMUP) {
            f(200, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, lift = 2, stringSwing = 1)); f(200, S.copy(bob = 1, stringSwing = -1), FOOTSTEP)
        }
        clip(AnimationId.LIFT_PICK, loop = false, policy = FINISH_CYCLE) {
            f(250, S.copy(eyes = Eyes.LOOK_DOWN)); f(200, S.copy(bob = 3, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
            f(220, S.copy(bob = 3, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED), ITEM_PICK)
            f(200, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED))
        }
        clip(AnimationId.LIFT, policy = FINISH_CYCLE) {
            f(600, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED, mouth = Mouth.FLAT))
            f(250, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, bob = 1))
            f(600, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, bob = -1))
        }
        clip(AnimationId.LIFT_PUT, loop = false, policy = FINISH_CYCLE) {
            f(200, S.copy(bob = 2, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true))
            f(220, S.copy(bob = 3, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN), ITEM_PUT); f(200, S.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.GYM_REST) {
            val r = S.copy(bob = 2, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.CLOSED, mouth = Mouth.OPEN, ears = Ears.RELAXED)
            f(500, r); f(500, r.copy(bob = 3, headDy = 1))
        }
        clip(AnimationId.WATER, policy = FINISH_CYCLE) {
            f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE)); f(350, S.copy(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED))
            f(350, S.copy(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED, headDy = -1)); f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE, eyes = Eyes.HAPPY))
        }


}.clips.toMap()


