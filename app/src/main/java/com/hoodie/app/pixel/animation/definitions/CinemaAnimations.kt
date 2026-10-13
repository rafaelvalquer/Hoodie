package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Cinema: BUY_POPCORN → WALK_TO_SEAT → SIT_DOWN → WATCH_MOVIE ↔ EAT_POPCORN ↔ REACT → STAND_UP.
 * A sala é vista da tela: a plateia fica de frente para nós, iluminada pelo filme.
 */
internal fun cinemaAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    // No balcão: escolhe, paga com o celular e recebe o balde (POPCORN_TAKEN).
    clip(AnimationId.CINEMA_BUY_POPCORN, loop = false, policy = FINISH_CYCLE) {
        f(400, S.copy(eyes = Eyes.LOOK_UP))
        f(300, S.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_UP, mouth = Mouth.OPEN))
        f(240, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
        f(420, S.copy(rightArm = Arm.FORWARD_UP, item = Item.PHONE, eyes = Eyes.LOOK_UP))
        f(200, S.copy(rightArm = Arm.FORWARD_DOWN), PHONE_PUT)
        f(300, S.copy(rightArm = Arm.FORWARD_UP, eyes = Eyes.WIDE, ears = Ears.ALERT))
        f(300, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.POPCORN, eyes = Eyes.HAPPY), POPCORN_TAKEN)
        f(500, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.POPCORN, eyes = Eyes.HAPPY, lift = 1))
    }

    // Sentado, olhos na tela; a luz do filme pisca no rosto (a cena cuida da luz).
    clip(AnimationId.CINEMA_WATCH, policy = IMMEDIATE) {
        val watch = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.POPCORN, eyes = Eyes.FOCUSED)
        f(1_400, watch)
        f(900, watch.copy(bob = 1))
        f(140, watch.copy(eyes = Eyes.CLOSED))
        f(1_200, watch.copy(ears = Ears.TWITCH_LEFT))
        f(800, watch.copy(eyes = Eyes.LOOK_LEFT))
    }

    // Pega um punhado, leva à boca, mastiga sem tirar os olhos da tela.
    clip(AnimationId.CINEMA_EAT_POPCORN, policy = FINISH_CYCLE) {
        val hold = SIT.copy(leftArm = Arm.HOLD_CHEST, item = Item.POPCORN, eyes = Eyes.FOCUSED)
        f(400, hold.copy(rightArm = Arm.HOLD_CHEST))
        f(300, hold.copy(rightArm = Arm.HOLD_MOUTH, mouth = Mouth.OPEN))
        f(300, hold.copy(rightArm = Arm.HOLD_CHEST, mouth = Mouth.CHEW))
        f(300, hold.copy(rightArm = Arm.HOLD_CHEST, mouth = Mouth.CHEW, bob = 1))
        f(500, hold.copy(rightArm = Arm.HOLD_CHEST))
    }

    // Susto ou cena engraçada: arregala, pula na cadeira e ri.
    clip(AnimationId.CINEMA_REACT, loop = false, policy = FINISH_CYCLE) {
        val hold = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.POPCORN)
        f(200, hold.copy(eyes = Eyes.FOCUSED))
        f(300, hold.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, ears = Ears.ALERT, lift = 1))
        f(400, hold.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, ears = Ears.ALERT))
        f(300, hold.copy(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, bob = 1))
        f(300, hold.copy(eyes = Eyes.HAPPY, mouth = Mouth.SMILE))
        f(400, hold.copy(eyes = Eyes.HAPPY, blush = true))
    }
}.clips.toMap()
