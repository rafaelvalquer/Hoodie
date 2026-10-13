package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Praia: relaxar sob o guarda-sol, olhar o mar (de costas para nós, o mar fica no fundo da cena)
 * e tomar sorvete. Foto, celular e acenar reaproveitam LEISURE_PHOTO, PHONE_* e WAVE.
 */
internal fun beachAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // Sentado na cadeira de praia, mãos atrás da cabeça, olhos meio fechados ao sol.
    clip(AnimationId.BEACH_LOUNGE, policy = IMMEDIATE) {
        val lounge = SIT.copy(leftArm = Arm.HEAD, rightArm = Arm.HEAD, ears = Ears.RELAXED)
        f(1_400, lounge.copy(eyes = Eyes.HALF))
        f(1_000, lounge.copy(eyes = Eyes.CLOSED, bob = 1))
        f(1_200, lounge.copy(eyes = Eyes.CLOSED, mouth = Mouth.SMILE))
        f(700, lounge.copy(eyes = Eyes.HALF, ears = Ears.TWITCH_LEFT))
        f(900, lounge.copy(eyes = Eyes.HAPPY))
    }

    // De costas, mão na testa protegendo do sol, olha o horizonte de um lado ao outro.
    clip(AnimationId.BEACH_LOOK_SEA, loop = false, policy = FINISH_FRAME) {
        val back = S.copy(facing = Facing.BACK, ears = Ears.RELAXED)
        f(700, back)
        f(800, back.copy(rightArm = Arm.HEAD))
        f(700, back.copy(rightArm = Arm.HEAD, headDy = 1))
        f(600, back.copy(rightArm = Arm.HEAD, ears = Ears.TWITCH_RIGHT))
        f(400, S.copy(facing = Facing.SIDE, eyes = Eyes.LOOK_UP))
        f(500, S.copy(eyes = Eyes.HAPPY, blush = true))
    }

    // Recebe a casquinha (ICE_CREAM_TAKEN), lambe devagar e termina (ICE_CREAM_FINISHED).
    clip(AnimationId.BEACH_ICE_CREAM, loop = false, policy = FINISH_CYCLE) {
        f(300, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.WIDE))
        f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.ICE_CREAM, eyes = Eyes.HAPPY), ICE_CREAM_TAKEN)
        for (lick in 0 until 3) {
            f(300, S.copy(rightArm = Arm.HOLD_MOUTH, item = Item.ICE_CREAM, mouth = Mouth.OPEN, eyes = Eyes.CLOSED))
            f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.ICE_CREAM, eyes = Eyes.HAPPY, bob = lick % 2))
        }
        f(400, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.ICE_CREAM, eyes = Eyes.HAPPY, blush = true))
        f(300, S.copy(rightArm = Arm.HOLD_MOUTH, mouth = Mouth.CHEW, eyes = Eyes.HAPPY), ICE_CREAM_FINISHED)
        f(400, S.copy(eyes = Eyes.HAPPY))
    }
}.clips.toMap()
