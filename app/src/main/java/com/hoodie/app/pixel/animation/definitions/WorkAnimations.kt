package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun workAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Trabalho ─────
        clip(AnimationId.WORK_TYPING, policy = FINISH_CYCLE) {
            f(140, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(130, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP))
            f(140, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(160, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, headDy = 1))
            f(130, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN))
        }
        clip(AnimationId.STOP_TYPING, loop = false) {
            f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN)); f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.DOWN, bob = 1))
        }
        clip(AnimationId.REACH_MOUSE, loop = false) {
            f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.DOWN, eyes = Eyes.LOOK_RIGHT)); f(170, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
        }
        clip(AnimationId.WORK_MOUSE, policy = FINISH_FRAME) {
            f(400, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
            f(250, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
            f(600, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
        }
        clip(AnimationId.WORK_READ, policy = IMMEDIATE) {
            f(900, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT)); f(800, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT, bob = 1))
        }
        clip(AnimationId.WORK_NOTES, policy = FINISH_CYCLE) {
            val n = SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.FORK, eyes = Eyes.LOOK_DOWN)
            f(220, n); f(180, n.copy(rightArm = Arm.FORWARD_UP)); f(220, n); f(400, n.copy(eyes = Eyes.LOOK_UP, rightArm = Arm.CHIN))
        }
        clip(AnimationId.WORK_TIRED, policy = FINISH_FRAME) {
            val t = SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.SLEEPY, ears = Ears.DOWN, mouth = Mouth.FLAT)
            // A cabeça cai e volta em degraus de até 2 px (sem "pulo" na volta do loop).
            f(700, t); f(700, t.copy(bob = 1, headDy = 1)); f(500, t.copy(eyes = Eyes.CLOSED, bob = 2, headDy = 1)); f(300, t.copy(bob = 1))
        }


}.clips.toMap()


