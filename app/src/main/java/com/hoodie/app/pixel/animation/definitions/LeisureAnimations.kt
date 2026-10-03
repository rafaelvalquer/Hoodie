package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.definitions.ClipDefinitions.Builder
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

internal fun leisureAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
        // ───── Videogame ─────
        val pad = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER)
        clip(AnimationId.GAMING, policy = IMMEDIATE) {
            f(170, pad.copy(eyes = Eyes.LOOK_RIGHT)); f(170, pad.copy(eyes = Eyes.LOOK_RIGHT, bob = 1)); f(200, pad.copy(eyes = Eyes.FOCUSED)); f(170, pad.copy(eyes = Eyes.LOOK_RIGHT, bob = 1))
        }
        clip(AnimationId.GAME_PRESS, policy = IMMEDIATE) {
            f(90, pad.copy(eyes = Eyes.FOCUSED)); f(90, pad.copy(eyes = Eyes.FOCUSED, bob = 1, leftArm = Arm.FORWARD_UP)); f(90, pad.copy(eyes = Eyes.FOCUSED)); f(90, pad.copy(eyes = Eyes.FOCUSED, bob = 1, rightArm = Arm.FORWARD_UP))
        }
        clip(AnimationId.GAME_FOCUSED, policy = IMMEDIATE) {
            f(500, pad.copy(eyes = Eyes.FOCUSED, ears = Ears.ALERT, headDy = 1)); f(400, pad.copy(eyes = Eyes.FOCUSED, ears = Ears.ALERT, headDy = 2, mouth = Mouth.FLAT))
        }
        clip(AnimationId.GAME_WIN, loop = false, policy = FINISH_CYCLE) {
            f(120, pad.copy(bob = 1, eyes = Eyes.WIDE))
            f(180, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.WIDE, mouth = Mouth.OPEN, lift = 2, ears = Ears.ALERT), SPARKLE)
            f(160, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 1))
            f(180, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 2))
            f(300, pad.copy(eyes = Eyes.HAPPY, blush = true))
        }
        clip(AnimationId.GAME_LOSE, loop = false, policy = FINISH_CYCLE) {
            f(200, pad.copy(eyes = Eyes.WIDE)); f(600, pad.copy(eyes = Eyes.CLOSED, ears = Ears.DOWN, mouth = Mouth.FLAT, bob = 1, headDy = 1))
            f(500, pad.copy(eyes = Eyes.SLEEPY, ears = Ears.DOWN, mouth = Mouth.FLAT)); f(300, pad)
        }


        // ───── Casa e lugares ─────
        clip(AnimationId.READING) {
            val r = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN)
            f(1000, r); f(900, r.copy(bob = 1)); f(160, r.copy(rightArm = Arm.FORWARD_UP)); f(900, r)
        }
        clip(AnimationId.WATCH_TV, policy = IMMEDIATE) { f(700, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(700, SIT.copy(eyes = Eyes.LOOK_RIGHT, bob = 1)) }
        clip(AnimationId.COOK) {
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.FOCUSED))
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED, stringSwing = 1))
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.HAPPY))
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED, stringSwing = -1))
        }
        clip(AnimationId.CLEAN) {
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.BROOM, eyes = Eyes.LOOK_DOWN, stringSwing = -1))
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_UP, item = Item.BROOM, eyes = Eyes.LOOK_DOWN, bob = 1, stringSwing = 1))
        }
        clip(AnimationId.BUS_SIT, policy = IMMEDIATE) {
            val b = SIT.copy(backpack = true, eyes = Eyes.LOOK_LEFT)
            f(500, b); f(500, b.copy(bob = 1, backpackDy = -1)); f(500, b.copy(backpackDy = 1)); f(500, b.copy(eyes = Eyes.OPEN, bob = 1))
        }
        // ───── Passeio ─────
        // Caminhada derivada do WALK, sem mochila, mais lenta e olhando o ambiente.
        clip(AnimationId.LEISURE_WALK, policy = IMMEDIATE, directional = true) {
            val bob = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
            val gaze = arrayOf(Eyes.OPEN, Eyes.OPEN, Eyes.LOOK_UP, Eyes.LOOK_UP, Eyes.OPEN, Eyes.OPEN, Eyes.LOOK_LEFT, Eyes.OPEN)
            for (i in 0 until 8) {
                f(
                    140,
                    HoodiePose(
                        legs = Legs.WALK, stride = i, bob = bob[i], headDy = bob[(i + 7) % 8] - bob[i],
                        stringSwing = if (i < 4) 1 else -1, ears = Ears.RELAXED, eyes = gaze[i],
                        leftArm = if (i < 4) Arm.SWING_BACK else Arm.SWING_FRONT,
                        rightArm = if (i < 4) Arm.SWING_FRONT else Arm.SWING_BACK,
                    ),
                    *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray()),
                )
            }
        }

        // Sentado no banco (SIT_DOWN/STAND_UP vêm da microação): olha o ambiente, relaxado.
        clip(AnimationId.LEISURE_BENCH, policy = IMMEDIATE) {
            val bench = SIT.copy(ears = Ears.RELAXED)
            f(900, bench.copy(eyes = Eyes.LOOK_LEFT))
            f(700, bench.copy(eyes = Eyes.LOOK_LEFT, bob = 1))
            f(900, bench.copy(eyes = Eyes.LOOK_UP))
            f(140, bench.copy(eyes = Eyes.CLOSED))
            f(900, bench.copy(eyes = Eyes.HAPPY, bob = 1))
        }

        // Pega o celular → levanta (CAMERA_READY) → mira → flash (PHOTO_TAKEN) → baixa → guarda.
        clip(AnimationId.LEISURE_PHOTO, loop = false, policy = FINISH_CYCLE) {
            f(200, S.copy(eyes = Eyes.LOOK_DOWN))
            f(220, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
            f(260, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.FOCUSED), CAMERA_READY)
            f(500, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.FOCUSED, ears = Ears.ALERT))
            f(160, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.CLOSED), PHOTO_TAKEN)
            f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.HAPPY))
            f(200, S.copy(rightArm = Arm.FORWARD_DOWN), PHONE_PUT)
            f(300, S.copy(eyes = Eyes.HAPPY))
        }

        // Olha à esquerda → à direita → para cima → orelha → feliz.
        clip(AnimationId.LEISURE_LOOK, loop = false, policy = FINISH_FRAME) {
            f(600, S.copy(eyes = Eyes.LOOK_LEFT, ears = Ears.RELAXED))
            f(600, S.copy(eyes = Eyes.LOOK_RIGHT, headDy = 1))
            f(700, S.copy(eyes = Eyes.LOOK_UP))
            f(260, S.copy(eyes = Eyes.LOOK_UP, ears = Ears.TWITCH_RIGHT))
            f(500, S.copy(eyes = Eyes.HAPPY, ears = Ears.ALERT, blush = true))
        }


}.clips.toMap()


