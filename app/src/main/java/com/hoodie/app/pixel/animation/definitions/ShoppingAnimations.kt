package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/** Compras: prateleira → produto na mão → carrinho → caixa. Os eventos levam o produto pela cena. */
internal fun shoppingAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Parado na prateleira: olha à esquerda, à direita e inclina a cabeça.
    clip(AnimationId.SHOP_LOOK, policy = IMMEDIATE) {
        f(700, S.copy(eyes = Eyes.LOOK_LEFT))
        f(500, S.copy(eyes = Eyes.LOOK_LEFT, headDy = 1))
        f(700, S.copy(eyes = Eyes.LOOK_RIGHT))
        f(600, S.copy(eyes = Eyes.LOOK_UP, ears = Ears.TWITCH_RIGHT))
        f(500, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_RIGHT))
    }

    // Estende o braço → pega (ITEM_PICKED) → aproxima do rosto → analisa → põe no carrinho (ITEM_IN_CART).
    clip(AnimationId.SHOP_PICK, loop = false, policy = FINISH_CYCLE) {
        f(260, S.copy(eyes = Eyes.LOOK_UP))
        f(220, S.copy(rightArm = Arm.UP, eyes = Eyes.LOOK_UP))
        f(200, S.copy(rightArm = Arm.UP, item = Item.PRODUCT, eyes = Eyes.LOOK_UP), ITEM_PICKED)
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.LOOK_DOWN))
        f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.FOCUSED, ears = Ears.ALERT))
        f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.HAPPY))
        f(240, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.PRODUCT, eyes = Eyes.LOOK_DOWN))
        f(200, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN), ITEM_IN_CART)
        f(200, S)
    }

    // Duas mãos na barra do carrinho; pernas na base locomotiva do WALK (andar no lugar, atrás do carrinho).
    clip(AnimationId.SHOP_CART, policy = IMMEDIATE) {
        val bob = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
        for (i in 0 until 8) {
            f(
                120,
                HoodiePose(
                    legs = Legs.WALK, stride = i, bob = bob[i], headDy = bob[(i + 7) % 8] - bob[i],
                    leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN,
                    eyes = if (i in 4..5) Eyes.LOOK_LEFT else Eyes.OPEN,
                ),
                *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray()),
            )
        }
    }

    // Diante do caixa: produtos na esteira → pega o celular → aproxima do terminal → confirma → guarda.
    clip(AnimationId.SHOP_PAY, loop = false, policy = FINISH_CYCLE) {
        f(300, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN), ITEM_AT_CHECKOUT)
        f(400, S.copy(eyes = Eyes.LOOK_RIGHT))
        f(200, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
        f(260, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
        f(420, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.LOOK_RIGHT))
        f(380, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.HAPPY, ears = Ears.ALERT), PAYMENT_DONE)
        f(240, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE))
        f(200, S.copy(rightArm = Arm.FORWARD_DOWN), PHONE_PUT)
        f(300, S.copy(eyes = Eyes.HAPPY))
    }
}.clips.toMap()
