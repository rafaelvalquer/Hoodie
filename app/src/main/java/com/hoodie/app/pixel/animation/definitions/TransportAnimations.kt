package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun transportAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    clip(AnimationId.CAR_ENTER, loop = false, policy = FINISH_CYCLE, directional = true) {
        f(160, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_RIGHT, ears = Ears.ALERT)); f(170, S.copy(facing = Facing.SIDE, legs = Legs.SIT, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, bob = 1), AnimationEvent.SIT)
        f(180, S.copy(facing = Facing.SIDE, legs = Legs.SIT, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED)); f(160, S.copy(facing = Facing.SIDE, legs = Legs.SIT, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, bob = -1))
    }
    clip(AnimationId.CAR_IDLE, directional = true) {
        f(720, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED, bob = 0))
        f(420, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED, bob = 1))
        f(180, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.CLOSED))
        f(400, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED, ears = Ears.TWITCH_LEFT))
    }
    clip(AnimationId.CAR_LOOK_WINDOW, loop = false, directional = true) {
        f(230, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT, headDy = 1)); f(850, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
        f(220, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED, headDy = -1))
    }
    clip(AnimationId.CAR_LOOK_FRONT, loop = false, directional = true) {
        f(230, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT)); f(720, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED))
        f(200, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED))
    }
    clip(AnimationId.CAR_BUMP, loop = false, directional = true) {
        f(100, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, bob = 2, headDy = -1, ears = Ears.ALERT, stringSwing = 2))
        f(120, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, bob = -1, headDy = 1, stringSwing = -2)); f(300, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, leftArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED))
    }
    clip(AnimationId.CAR_EXIT, loop = false, policy = FINISH_CYCLE, directional = true) {
        f(160, HoodiePose(legs = Legs.SIT, facing = Facing.SIDE, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT)); f(180, HoodiePose(legs = Legs.STAND, facing = Facing.SIDE, rightArm = Arm.FORWARD_UP, bob = 1), AnimationEvent.STAND)
        f(150, HoodiePose(legs = Legs.STAND, facing = Facing.SIDE, rightArm = Arm.FORWARD_UP, stringSwing = 1)); f(160, S.copy(facing = Facing.SIDE))
    }

    clip(AnimationId.BUS_ENTER, loop = false, policy = FINISH_CYCLE) {
        f(200, S.copy(eyes = Eyes.LOOK_UP, ears = Ears.ALERT)); f(220, HoodiePose(legs = Legs.SIT, rightArm = Arm.HOLD_CHEST, bob = -1), AnimationEvent.SIT)
        f(200, HoodiePose(legs = Legs.SIT, rightArm = Arm.DOWN, bob = 1)); f(180, HoodiePose(legs = Legs.SIT))
    }
    clip(AnimationId.BUS_LOOK_WINDOW, loop = false) {
        f(200, HoodiePose(legs = Legs.SIT, eyes = Eyes.LOOK_RIGHT, headDy = 1)); f(820, HoodiePose(legs = Legs.SIT, eyes = Eyes.LOOK_RIGHT))
        f(180, HoodiePose(legs = Legs.SIT, eyes = Eyes.OPEN))
    }
    clip(AnimationId.BUS_PHONE, policy = FINISH_CYCLE) {
        f(240, HoodiePose(legs = Legs.SIT, rightArm = Arm.HOLD_CHEST, item = Item.PHONE), AnimationEvent.PHONE_PICK)
        f(620, HoodiePose(legs = Legs.SIT, rightArm = Arm.HOLD_MOUTH, item = Item.PHONE, eyes = Eyes.FOCUSED))
        f(420, HoodiePose(legs = Legs.SIT, rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.OPEN))
        f(220, HoodiePose(legs = Legs.SIT, rightArm = Arm.DOWN), AnimationEvent.PHONE_PUT)
    }
    clip(AnimationId.BUS_BUMP, loop = false) {
        f(100, HoodiePose(legs = Legs.SIT, bob = 2, leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_UP, ears = Ears.ALERT))
        f(130, HoodiePose(legs = Legs.SIT, bob = -1, headDy = 1)); f(250, HoodiePose(legs = Legs.SIT, eyes = Eyes.OPEN))
    }
    clip(AnimationId.BUS_STAND, loop = false, policy = FINISH_CYCLE) {
        f(180, HoodiePose(legs = Legs.SIT, eyes = Eyes.LOOK_UP)); f(180, HoodiePose(legs = Legs.STAND, rightArm = Arm.HOLD_CHEST, bob = 1), AnimationEvent.STAND)
        f(300, HoodiePose(legs = Legs.STAND, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
    }
    clip(AnimationId.BUS_EXIT, loop = false, policy = FINISH_CYCLE) {
        f(180, HoodiePose(legs = Legs.STAND, rightArm = Arm.FORWARD_UP)); f(200, HoodiePose(legs = Legs.STEP_LEFT, rightArm = Arm.DOWN, stringSwing = 1))
        f(200, S)
    }

    clip(AnimationId.TRAIN_ENTER, loop = false, policy = FINISH_CYCLE) { f(180, S.copy(eyes = Eyes.LOOK_RIGHT)); f(180, SIT.copy(bob = 1), AnimationEvent.SIT); f(180, SIT) }
    clip(AnimationId.TRAIN_SIT) { f(700, SIT); f(250, SIT.copy(bob = 1, stringSwing = 1)); f(700, SIT.copy(eyes = Eyes.HALF)); f(250, SIT.copy(bob = -1, stringSwing = -1)) }
    clip(AnimationId.TRAIN_WINDOW, loop = false) { f(200, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(900, SIT.copy(eyes = Eyes.LOOK_RIGHT, headDy = 1)); f(200, SIT.copy(eyes = Eyes.OPEN)) }
    clip(AnimationId.TRAIN_PHONE, policy = FINISH_CYCLE) {
        f(240, SIT.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE), AnimationEvent.PHONE_PICK)
        f(800, SIT.copy(rightArm = Arm.HOLD_MOUTH, item = Item.PHONE, eyes = Eyes.FOCUSED)); f(220, SIT.copy(rightArm = Arm.DOWN), AnimationEvent.PHONE_PUT)
    }
    clip(AnimationId.TRAIN_STAND, loop = false, policy = FINISH_CYCLE) { f(180, SIT); f(200, S.copy(bob = 1), AnimationEvent.STAND); f(260, S.copy(eyes = Eyes.LOOK_RIGHT)) }
    clip(AnimationId.TRAIN_BRAKE, loop = false) {
        f(100, SIT.copy(bob = 2, headDy = -1, stringSwing = 2, ears = Ears.ALERT)); f(130, SIT.copy(bob = -1, headDy = 1, stringSwing = -2))
        f(280, SIT.copy(eyes = Eyes.OPEN))
    }
    clip(AnimationId.TRAIN_EXIT, loop = false, policy = FINISH_CYCLE) { f(180, S.copy(eyes = Eyes.LOOK_RIGHT)); f(180, S.copy(legs = Legs.STEP_RIGHT)); f(220, S) }

    clip(AnimationId.METRO_ENTER, loop = false, policy = FINISH_CYCLE) { f(180, S.copy(eyes = Eyes.LOOK_RIGHT, ears = Ears.ALERT)); f(180, SIT.copy(bob = 1), AnimationEvent.SIT); f(180, SIT) }
    clip(AnimationId.METRO_SIT) { f(700, SIT.copy(eyes = Eyes.OPEN)); f(300, SIT.copy(eyes = Eyes.HALF, ears = Ears.RELAXED)); f(160, SIT.copy(eyes = Eyes.CLOSED)); f(300, SIT) }
    clip(AnimationId.METRO_STAND, loop = false, policy = FINISH_CYCLE) { f(180, SIT.copy(eyes = Eyes.LOOK_UP)); f(180, S.copy(bob = 1), AnimationEvent.STAND); f(240, S.copy(eyes = Eyes.LOOK_RIGHT)) }
    clip(AnimationId.METRO_HANDLE) { f(460, S.copy(leftArm = Arm.FORWARD_UP, eyes = Eyes.OPEN)); f(180, S.copy(leftArm = Arm.FORWARD_UP, bob = 1, stringSwing = 1)); f(460, S.copy(leftArm = Arm.FORWARD_UP)); f(180, S.copy(leftArm = Arm.FORWARD_UP, bob = -1, stringSwing = -1)) }
    clip(AnimationId.METRO_LOOK_WINDOW, loop = false) { f(180, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(500, SIT.copy(eyes = Eyes.LOOK_RIGHT, headDy = 1)); f(220, SIT.copy(eyes = Eyes.OPEN)) }
    clip(AnimationId.METRO_PHONE, policy = FINISH_CYCLE) { f(220, SIT.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE), AnimationEvent.PHONE_PICK); f(700, SIT.copy(rightArm = Arm.HOLD_MOUTH, item = Item.PHONE, eyes = Eyes.FOCUSED)); f(220, SIT.copy(rightArm = Arm.DOWN), AnimationEvent.PHONE_PUT) }
    clip(AnimationId.METRO_BRAKE, loop = false) { f(100, S.copy(bob = 2, leftArm = Arm.FORWARD_UP, ears = Ears.ALERT)); f(130, S.copy(bob = -1, headDy = 1, stringSwing = -2)); f(280, S) }
    clip(AnimationId.METRO_EXIT, loop = false, policy = FINISH_CYCLE) { f(180, S.copy(eyes = Eyes.LOOK_RIGHT)); f(170, S.copy(legs = Legs.STEP_LEFT, rightArm = Arm.FORWARD_UP)); f(220, S) }

    clip(AnimationId.BIKE_START, loop = false, policy = FINISH_CYCLE) {
        f(180, S.copy(eyes = Eyes.LOOK_DOWN)); f(140, S.copy(legs = Legs.STEP_LEFT, rightArm = Arm.FORWARD_UP, bob = 1));
        f(140, S.copy(legs = Legs.STEP_RIGHT, leftArm = Arm.FORWARD_UP, lift = 1)); f(180, S.copy(legs = Legs.WALK, facing = Facing.SIDE, stringSwing = 1))
    }
    clip(AnimationId.BIKE_PEDAL) {
        val phases = listOf(0, 2, 4, 6, 0, 2, 4, 6)
        phases.forEachIndexed { i, stride -> f(100, HoodiePose(legs = Legs.WALK, stride = stride, facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = if (i % 2 == 0) 0 else 1, stringSwing = if (i % 2 == 0) 1 else -1), if (i % 2 == 0) AnimationEvent.FOOTSTEP else AnimationEvent.SPARKLE) }
    }
    clip(AnimationId.BIKE_COAST) { f(650, S.copy(legs = Legs.STAND, facing = Facing.SIDE)); f(250, S.copy(legs = Legs.STAND, facing = Facing.SIDE, bob = 1, stringSwing = 1)); f(650, S.copy(legs = Legs.STAND, facing = Facing.SIDE)); f(250, S.copy(legs = Legs.STAND, facing = Facing.SIDE, bob = -1, stringSwing = -1)) }
    clip(AnimationId.BIKE_LOOK, loop = false) { f(180, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_LEFT, leftArm = Arm.FORWARD_DOWN)); f(600, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_LEFT, leftArm = Arm.FORWARD_DOWN)); f(180, S.copy(facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN)) }
    clip(AnimationId.BIKE_BRAKE, loop = false, policy = FINISH_CYCLE) { f(100, S.copy(legs = Legs.STEP_RIGHT, facing = Facing.SIDE, bob = 1)); f(140, S.copy(legs = Legs.STAND, facing = Facing.SIDE, rightArm = Arm.FORWARD_UP, stringSwing = 1)); f(260, S.copy(legs = Legs.STAND, facing = Facing.SIDE)) }
    clip(AnimationId.BIKE_STOP, loop = false) { f(180, S.copy(facing = Facing.SIDE, legs = Legs.STAND, eyes = Eyes.LOOK_DOWN)); f(180, S.copy(facing = Facing.FRONT, eyes = Eyes.OPEN)) }

    clip(AnimationId.OTHER_RIDE_START, loop = false, policy = FINISH_CYCLE) { f(180, S.copy(eyes = Eyes.LOOK_DOWN)); f(160, S.copy(legs = Legs.STEP_LEFT, rightArm = Arm.FORWARD_UP)); f(180, S.copy(legs = Legs.STAND, facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN)) }
    clip(AnimationId.OTHER_RIDE) { f(520, S.copy(facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN)); f(180, S.copy(facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = 1, stringSwing = 1)); f(520, S.copy(facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN)); f(180, S.copy(facing = Facing.SIDE, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = -1, stringSwing = -1)) }
    clip(AnimationId.OTHER_RIDE_LOOK, loop = false) { f(180, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_LEFT)); f(600, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_LEFT)); f(200, S.copy(facing = Facing.SIDE, eyes = Eyes.OPEN)) }
    clip(AnimationId.OTHER_RIDE_STOP, loop = false, policy = FINISH_CYCLE) { f(140, S.copy(facing = Facing.SIDE, bob = 1)); f(180, S.copy(legs = Legs.STAND, facing = Facing.FRONT)); f(220, S) }

    // Perfil deliberadamente neutro para transporte coletivo/veículo não identificado.
    clip(AnimationId.TRANSIT_ENTER, loop = false, policy = FINISH_CYCLE) {
        f(180, S.copy(eyes = Eyes.LOOK_RIGHT)); f(200, SIT.copy(bob = 1), AnimationEvent.SIT); f(220, SIT)
    }
    clip(AnimationId.TRANSIT_SIT) {
        f(700, SIT.copy(eyes = Eyes.OPEN)); f(260, SIT.copy(eyes = Eyes.HALF, ears = Ears.RELAXED))
        f(180, SIT.copy(eyes = Eyes.CLOSED)); f(300, SIT.copy(eyes = Eyes.OPEN, stringSwing = 1))
    }
    clip(AnimationId.TRANSIT_LOOK_WINDOW, loop = false) {
        f(180, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(700, SIT.copy(eyes = Eyes.LOOK_RIGHT, headDy = 1)); f(180, SIT.copy(eyes = Eyes.OPEN))
    }
    clip(AnimationId.TRANSIT_BUMP, loop = false) {
        f(100, SIT.copy(bob = 1, headDy = -1, ears = Ears.ALERT, stringSwing = 1))
        f(140, SIT.copy(bob = -1, headDy = 1, stringSwing = -1)); f(260, SIT.copy(eyes = Eyes.OPEN))
    }
    clip(AnimationId.TRANSIT_EXIT, loop = false, policy = FINISH_CYCLE) {
        f(180, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(200, S.copy(bob = 1), AnimationEvent.STAND); f(220, S.copy(eyes = Eyes.OPEN))
    }
}.clips.toMap()
