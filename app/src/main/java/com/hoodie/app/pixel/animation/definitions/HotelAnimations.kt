package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Hotel: chega com a mala (WALK_SUITCASE), faz o check-in (a mala desce ao lado dele e volta para
 * a mão), espera olhando o relógio, descansa no sofá (SIT_DOWN/IDLE_SIT) e pega o elevador (a
 * porta fecha na frente dele e abre de novo).
 */
internal fun hotelAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Caminhada puxando a mala de rodinhas pela alça.
    clip(AnimationId.WALK_SUITCASE, policy = IMMEDIATE, directional = true) {
        val bob = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
        for (i in 0 until 8) {
            f(
                120,
                HoodiePose(
                    legs = Legs.WALK, stride = i, bob = bob[i], headDy = bob[(i + 7) % 8] - bob[i],
                    stringSwing = if (i < 4) 1 else -1,
                    leftArm = if (i < 4) Arm.SWING_BACK else Arm.SWING_FRONT,
                    rightArm = Arm.FORWARD_DOWN, item = Item.SUITCASE,
                ),
                *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray()),
            )
        }
    }

    // De costas para nós, no balcão: solta a mala, conversa, recebe o cartão, pega a mala de volta.
    clip(AnimationId.HOTEL_CHECK_IN, loop = false, policy = FINISH_CYCLE) {
        val back = S.copy(facing = Facing.BACK)
        f(400, back.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SUITCASE))
        f(300, back.copy(rightArm = Arm.FORWARD_DOWN), SUITCASE_PUT_DOWN)
        f(700, back.copy(ears = Ears.ALERT))
        f(600, back.copy(headDy = 1))
        f(600, back.copy(rightArm = Arm.FORWARD_UP, ears = Ears.TWITCH_LEFT))
        f(500, back.copy(headDy = 1))
        f(300, back.copy(rightArm = Arm.FORWARD_UP))
        f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.KEYCARD, eyes = Eyes.HAPPY, ears = Ears.ALERT), KEYCARD_RECEIVED)
        f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.KEYCARD, eyes = Eyes.HAPPY, blush = true))
        f(300, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SUITCASE, eyes = Eyes.OPEN), SUITCASE_PICKED_UP)
    }

    // Esperando no saguão: ergue o pulso, olha o relógio, suspira e olha a porta.
    clip(AnimationId.HOTEL_CHECK_WATCH, loop = false, policy = FINISH_FRAME) {
        f(300, S.copy(eyes = Eyes.LOOK_LEFT))
        f(300, S.copy(leftArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_DOWN))
        f(700, S.copy(leftArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_DOWN, headDy = 1))
        f(300, S.copy(leftArm = Arm.HOLD_CHEST, eyes = Eyes.HALF, mouth = Mouth.FLAT))
        f(500, S.copy(eyes = Eyes.LOOK_LEFT, ears = Ears.TWITCH_RIGHT))
        f(400, S.copy(eyes = Eyes.OPEN))
    }

    // Aperta o botão (ELEVATOR_CALLED), a porta abre, entra (ELEVATOR_ENTERED: some atrás da
    // porta), sobe e desce, a porta abre e ele sai (ELEVATOR_LEFT); a porta fecha (ELEVATOR_CLOSED).
    clip(AnimationId.HOTEL_ELEVATOR, loop = false, policy = FINISH_CYCLE) {
        val back = S.copy(facing = Facing.BACK)
        f(300, back)
        f(260, back.copy(rightArm = Arm.FORWARD_UP), ELEVATOR_CALLED)
        f(700, back.copy(ears = Ears.ALERT))
        f(300, back.copy(lift = 1), ELEVATOR_ENTERED)
        f(1_400, back)
        f(900, back.copy(ears = Ears.TWITCH_LEFT))
        f(300, S.copy(eyes = Eyes.WIDE), ELEVATOR_LEFT)
        f(500, S.copy(eyes = Eyes.HAPPY, ears = Ears.ALERT))
        f(400, S.copy(eyes = Eyes.HAPPY), ELEVATOR_CLOSED)
    }
}.clips.toMap()
