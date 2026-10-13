package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Shopping: vitrines no fundo do corredor, entrar numa loja (a porta fecha na frente dele) e
 * voltar com a sacola, que segue na mão durante a caminhada (WALK_BAG). Banco e "observar
 * pessoas" reaproveitam LEISURE_BENCH, LOOK_AROUND e IDLE_LOOK.
 */
internal fun mallAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Caminhada com a sacola pendendo da mão direita; só o braço livre balança.
    clip(AnimationId.WALK_BAG, policy = IMMEDIATE, directional = true) {
        val bob = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
        for (i in 0 until 8) {
            f(
                110,
                HoodiePose(
                    legs = Legs.WALK, stride = i, bob = bob[i], headDy = bob[(i + 7) % 8] - bob[i],
                    stringSwing = if (i < 4) 1 else -1,
                    leftArm = if (i < 4) Arm.SWING_BACK else Arm.SWING_FRONT,
                    rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG,
                ),
                *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray()),
            )
        }
    }

    // De costas para nós, diante da vitrine: olha os produtos, aproxima, vira o rosto e gosta.
    clip(AnimationId.MALL_WINDOW_SHOP, policy = FINISH_CYCLE) {
        val back = S.copy(facing = Facing.BACK)
        f(900, back)
        f(600, back.copy(headDy = 1, ears = Ears.TWITCH_LEFT))
        f(700, back.copy(rightArm = Arm.FORWARD_UP))
        f(500, back.copy(rightArm = Arm.FORWARD_UP, ears = Ears.ALERT))
        f(400, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_UP))
        f(600, S.copy(eyes = Eyes.HAPPY, rightArm = Arm.CHIN))
    }

    // Entra na loja de costas (STORE_ENTERED: a porta fecha na frente dele), compra lá dentro e
    // sai de frente com a sacola (STORE_LEFT).
    clip(AnimationId.MALL_ENTER_STORE, loop = false, policy = FINISH_CYCLE) {
        f(400, S.copy(facing = Facing.BACK, ears = Ears.ALERT))
        f(300, S.copy(facing = Facing.BACK, rightArm = Arm.FORWARD_UP), STORE_ENTERED)
        f(1_200, S.copy(facing = Facing.BACK))
        f(1_000, S.copy(facing = Facing.BACK, ears = Ears.TWITCH_RIGHT))
        f(300, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG, eyes = Eyes.WIDE), STORE_LEFT)
        f(500, S.copy(rightArm = Arm.UP, item = Item.SHOPPING_BAG, eyes = Eyes.HAPPY, ears = Ears.ALERT, lift = 1), SPARKLE)
        f(400, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG, eyes = Eyes.HAPPY))
    }
}.clips.toMap()
