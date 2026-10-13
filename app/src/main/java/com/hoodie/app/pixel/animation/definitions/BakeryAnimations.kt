package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Padaria / cafeteria: vitrine → pedido no balcão → salgado na mão → mesa. Café, leitura,
 * sentar e levantar reaproveitam DRINK/REACH_MUG/PUT_MUG, READING e SIT_DOWN/STAND_UP.
 */
internal fun bakeryAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Diante da vitrine: olha as bandejas de cima a baixo, decide, aponta e pede (HOODIE_ORDER).
    clip(AnimationId.BAKERY_CHOOSE, loop = false, policy = FINISH_CYCLE) {
        f(500, S.copy(eyes = Eyes.LOOK_DOWN, headDy = 1))
        f(450, S.copy(eyes = Eyes.LOOK_LEFT, headDy = 1))
        f(450, S.copy(eyes = Eyes.LOOK_RIGHT, headDy = 1, ears = Ears.TWITCH_LEFT))
        f(400, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_DOWN))
        f(300, S.copy(rightArm = Arm.CHIN, eyes = Eyes.WIDE, ears = Ears.ALERT))
        f(420, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.HAPPY, mouth = Mouth.OPEN), HOODIE_ORDER)
        f(500, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.HAPPY))
        f(400, S.copy(eyes = Eyes.HAPPY, blush = true))
    }

    // No balcão: olha a atendente, pega o celular enquanto espera, recebe o salgado (ORDER_RECEIVED).
    clip(AnimationId.BAKERY_WAIT_ORDER, loop = false, policy = FINISH_CYCLE) {
        f(500, S.copy(eyes = Eyes.LOOK_UP))
        f(400, S.copy(eyes = Eyes.LOOK_UP, ears = Ears.TWITCH_RIGHT))
        f(260, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
        f(700, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN, headDy = 1))
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN, mouth = Mouth.FLAT))
        f(220, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_UP, ears = Ears.ALERT), PHONE_PUT)
        f(300, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.WIDE))
        f(280, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PASTRY, eyes = Eyes.HAPPY), ORDER_RECEIVED)
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PASTRY, eyes = Eyes.HAPPY, blush = true))
    }

    // Sentado à mesa: segura o salgado, morde, mastiga e termina (PASTRY_FINISHED no último gesto).
    clip(AnimationId.BAKERY_EAT_PASTRY, loop = false, policy = FINISH_CYCLE) {
        val hold = SIT.copy(rightArm = Arm.HOLD_CHEST, item = Item.PASTRY)
        f(500, hold.copy(eyes = Eyes.LOOK_DOWN))
        for (bite in 0 until 3) {
            f(260, hold.copy(rightArm = Arm.HOLD_MOUTH, mouth = Mouth.OPEN, eyes = Eyes.CLOSED))
            f(200, hold.copy(mouth = Mouth.CHEW, eyes = Eyes.HAPPY))
            f(200, hold.copy(mouth = Mouth.SMILE, eyes = Eyes.HAPPY, bob = 1))
            f(240, hold.copy(mouth = Mouth.CHEW, eyes = if (bite == 2) Eyes.HAPPY else Eyes.LOOK_DOWN))
        }
        f(300, SIT.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.HAPPY, mouth = Mouth.SMILE), PASTRY_FINISHED)
        f(400, SIT.copy(eyes = Eyes.HAPPY, blush = true))
    }

    // Sentado observando o salão: clientes à esquerda, balcão à direita, janela.
    clip(AnimationId.BAKERY_PEOPLE_WATCH, policy = IMMEDIATE) {
        val seat = SIT.copy(ears = Ears.RELAXED)
        f(1_100, seat.copy(eyes = Eyes.LOOK_LEFT))
        f(700, seat.copy(eyes = Eyes.LOOK_LEFT, headDy = 1))
        f(140, seat.copy(eyes = Eyes.CLOSED))
        f(1_000, seat.copy(eyes = Eyes.LOOK_RIGHT))
        f(900, seat.copy(eyes = Eyes.LOOK_UP, ears = Ears.TWITCH_LEFT))
        f(700, seat.copy(eyes = Eyes.HAPPY, bob = 1))
    }
}.clips.toMap()
