package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Família: sentado no sofá, conversando com alguém fora da cena (à esquerda).
 * Não há NPC desenhado — o olhar e os gestos sugerem a outra pessoa.
 */
internal fun visitAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    val talk = SIT.copy(eyes = Eyes.LOOK_LEFT)

    // Olha para a pessoa → mexe a cabeça → gesto de mão → cordões balançam.
    clip(AnimationId.VISIT_CHAT, policy = FINISH_FRAME) {
        f(500, talk)
        f(300, talk.copy(mouth = Mouth.OPEN))
        f(260, talk.copy(mouth = Mouth.OPEN, leftArm = Arm.FORWARD_UP, stringSwing = 1))
        f(300, talk.copy(headDy = 1, leftArm = Arm.FORWARD_UP))
        f(260, talk.copy(mouth = Mouth.OPEN, stringSwing = -1))
        f(500, talk.copy(eyes = Eyes.HAPPY))
    }

    // Cabeça inclinada → orelha orientada → pisca → pequeno aceno.
    clip(AnimationId.VISIT_LISTEN, policy = IMMEDIATE) {
        f(700, talk.copy(headDy = 1, mouth = Mouth.FLAT))
        f(500, talk.copy(headDy = 1, mouth = Mouth.FLAT, ears = Ears.TWITCH_LEFT))
        f(130, talk.copy(headDy = 1, eyes = Eyes.CLOSED, mouth = Mouth.FLAT))
        f(500, talk.copy(headDy = 1, mouth = Mouth.FLAT))
        f(260, talk.copy(headDy = 2))
        f(400, talk.copy(headDy = 1))
    }

    // Olhos fecham → boca feliz → corpo sobe 1 px → cordões reagem.
    clip(AnimationId.VISIT_LAUGH, loop = false, policy = FINISH_CYCLE) {
        f(200, talk.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN))
        f(260, SIT.copy(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 1, ears = Ears.ALERT, stringSwing = 1))
        f(220, SIT.copy(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, stringSwing = -1))
        f(260, SIT.copy(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 1, ears = Ears.ALERT, stringSwing = 1))
        f(400, SIT.copy(eyes = Eyes.HAPPY, blush = true))
        f(300, talk)
    }

    // Pega o petisco do prato (SNACK_PICKED) → leva à boca → mastiga → acaba (SNACK_FINISHED).
    clip(AnimationId.VISIT_SNACK, loop = false, policy = FINISH_CYCLE) {
        f(240, SIT.copy(eyes = Eyes.LOOK_DOWN))
        f(200, SIT.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
        f(220, SIT.copy(rightArm = Arm.HOLD_CHEST, item = Item.SNACK), SNACK_PICKED)
        f(240, SIT.copy(rightArm = Arm.HOLD_MOUTH, item = Item.SNACK, mouth = Mouth.OPEN))
        f(260, SIT.copy(rightArm = Arm.HOLD_CHEST, item = Item.SNACK, mouth = Mouth.CHEW, eyes = Eyes.HAPPY))
        f(260, SIT.copy(rightArm = Arm.HOLD_MOUTH, item = Item.SNACK, mouth = Mouth.OPEN))
        f(300, SIT.copy(mouth = Mouth.CHEW, eyes = Eyes.HAPPY), SNACK_FINISHED)
        f(300, SIT.copy(eyes = Eyes.HAPPY, blush = true))
    }
}.clips.toMap()
