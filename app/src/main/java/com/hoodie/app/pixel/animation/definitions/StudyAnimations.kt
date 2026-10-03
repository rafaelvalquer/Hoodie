package com.hoodie.app.pixel.animation.definitions

import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.*

/**
 * Escola: o Hoodie sentado à mesa com livro e caderno. O corpo quase não se mexe;
 * quem trabalha são antebraço, mão, olhos e orelhas.
 */
internal fun studyAnimations(): Map<AnimationId, AnimationClip> = ClipDefinitions().apply {
    val book = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN)

    // Livro aberto → olhos descem → cabeça acompanha a linha → piscada → volta.
    clip(AnimationId.STUDY_READ, policy = FINISH_FRAME) {
        f(900, book, BOOK_OPEN)
        f(700, book.copy(headDy = 1))
        f(800, book.copy(eyes = Eyes.LOOK_LEFT, headDy = 1))
        f(140, book.copy(eyes = Eyes.CLOSED, headDy = 1))
        f(800, book)
    }

    // Mão direita no caderno: micro movimento horizontal, pausa, continua.
    val write = SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.PENCIL, eyes = Eyes.LOOK_DOWN, mouth = Mouth.FLAT)
    clip(AnimationId.STUDY_WRITE, policy = FINISH_CYCLE) {
        f(180, write, BOOK_OPEN)
        f(160, write.copy(rightArm = Arm.FORWARD_UP))
        f(180, write)
        f(160, write.copy(rightArm = Arm.FORWARD_UP))
        f(520, write.copy(eyes = Eyes.FOCUSED))
        f(180, write.copy(rightArm = Arm.FORWARD_UP))
        f(200, write)
    }

    // Para de escrever → levanta a cabeça → olha para cima → orelha mexe → volta ao material.
    clip(AnimationId.STUDY_THINK, loop = false, policy = FINISH_CYCLE) {
        f(220, write)
        f(300, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.CHIN, eyes = Eyes.OPEN, mouth = Mouth.FLAT))
        f(700, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT))
        f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, ears = Ears.TWITCH_LEFT))
        f(500, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.CHIN, eyes = Eyes.HAPPY, ears = Ears.ALERT))
        f(260, write)
    }

    // Mão alcança a página → vira (PAGE_TURN atualiza o livro na mesa) → mão volta.
    clip(AnimationId.STUDY_PAGE_TURN, loop = false, policy = FINISH_CYCLE) {
        f(300, book)
        f(180, book.copy(rightArm = Arm.FORWARD_UP))
        f(160, book.copy(rightArm = Arm.FORWARD_UP, headDy = 1), PAGE_TURN)
        f(200, book.copy(rightArm = Arm.FORWARD_DOWN, headDy = 1))
        f(300, book)
    }
}.clips.toMap()
