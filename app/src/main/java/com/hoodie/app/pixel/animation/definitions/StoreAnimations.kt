package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Loja de roupas: arara → peça na mão → espelho / provador → sacola no caixa.
 * Os eventos levam a peça pela cena (some da arara, cortina fecha, sacola sai do balcão).
 */
internal fun storeAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Passa os cabides um a um, para numa peça, olha de perto e tira da arara (GARMENT_PICKED).
    clip(AnimationId.STORE_BROWSE_RACK, loop = false, policy = FINISH_CYCLE) {
        f(400, S.copy(eyes = Eyes.LOOK_RIGHT))
        f(260, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
        f(220, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT, headDy = 1))
        f(260, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
        f(220, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_LEFT, headDy = 1))
        f(500, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.FOCUSED, ears = Ears.ALERT))
        f(300, S.copy(rightArm = Arm.UP, eyes = Eyes.WIDE, ears = Ears.ALERT))
        f(260, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, eyes = Eyes.HAPPY), GARMENT_PICKED)
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, eyes = Eyes.LOOK_DOWN, headTilt = 1))
        f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, eyes = Eyes.HAPPY))
    }

    // Diante do espelho: põe a peça na frente do corpo, vira de lado, de costas, de frente e gosta.
    clip(AnimationId.STORE_HOLD_GARMENT, policy = FINISH_CYCLE) {
        f(500, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, itemInBothHands = true, eyes = Eyes.LOOK_DOWN))
        f(420, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, itemInBothHands = true, facing = Facing.SIDE))
        f(360, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, itemInBothHands = true, facing = Facing.BACK))
        f(420, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, itemInBothHands = true, eyes = Eyes.HAPPY, headTilt = 1))
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, eyes = Eyes.FOCUSED, ears = Ears.TWITCH_LEFT, headDy = 1))
        f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.GARMENT, eyes = Eyes.HAPPY, blush = true, ears = Ears.ALERT))
    }

    // Entra no provador de costas, a cortina fecha (o Hoodie some atrás dela), abre e ele sai feliz.
    clip(AnimationId.STORE_FITTING_ROOM, loop = false, policy = FINISH_CYCLE) {
        f(400, S.copy(facing = Facing.BACK, rightArm = Arm.HOLD_CHEST, item = Item.GARMENT))
        f(300, S.copy(facing = Facing.BACK, rightArm = Arm.FORWARD_UP), CURTAIN_CLOSE)
        f(900, S.copy(facing = Facing.BACK, ears = Ears.TWITCH_RIGHT))
        f(700, S.copy(facing = Facing.BACK, ears = Ears.TWITCH_LEFT))
        f(300, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.WIDE), CURTAIN_OPEN)
        f(500, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, blush = true, ears = Ears.ALERT, lift = 1), SPARKLE)
        f(400, S.copy(eyes = Eyes.HAPPY, facing = Facing.SIDE))
        f(300, S.copy(eyes = Eyes.HAPPY))
    }

    // No balcão: recebe a sacola (BAG_TAKEN), ergue com orgulho e se despede.
    clip(AnimationId.STORE_BAG_EXIT, loop = false, policy = FINISH_CYCLE) {
        f(400, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
        f(300, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
        f(260, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG, eyes = Eyes.HAPPY), BAG_TAKEN)
        f(500, S.copy(rightArm = Arm.UP, item = Item.SHOPPING_BAG, eyes = Eyes.HAPPY, ears = Ears.ALERT, lift = 1))
        f(400, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG, leftArm = Arm.WAVE, eyes = Eyes.HAPPY))
        f(400, S.copy(rightArm = Arm.FORWARD_DOWN, item = Item.SHOPPING_BAG, eyes = Eyes.HAPPY))
    }
}.clips.toMap()
