package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Farmácia: uma parada breve. O Hoodie olha a prateleira, examina uma caixinha genérica e a
 * devolve (ITEM_PICKED → ITEM_RETURNED); comprar é opcional (SHOP_PAY no caixa). Nada aqui
 * representa um remédio, sintoma ou motivo da visita.
 */
internal fun pharmacyAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    clip(AnimationId.PHARMACY_EXAMINE, loop = false, policy = FINISH_CYCLE) {
        f(500, S.copy(eyes = Eyes.LOOK_UP))
        f(300, S.copy(eyes = Eyes.LOOK_RIGHT, ears = Ears.TWITCH_LEFT))
        f(240, S.copy(rightArm = Arm.UP, eyes = Eyes.LOOK_UP))
        f(220, S.copy(rightArm = Arm.UP, item = Item.PRODUCT, eyes = Eyes.LOOK_UP), ITEM_PICKED)
        f(600, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.LOOK_DOWN, headTilt = 1))
        f(500, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.FOCUSED, headDy = 1))
        f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PRODUCT, eyes = Eyes.OPEN, mouth = Mouth.FLAT))
        f(240, S.copy(rightArm = Arm.UP, item = Item.PRODUCT, eyes = Eyes.LOOK_UP))
        f(220, S.copy(rightArm = Arm.UP, eyes = Eyes.LOOK_UP), ITEM_RETURNED)
        f(300, S.copy(eyes = Eyes.OPEN))
    }
}.clips.toMap()
