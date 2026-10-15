package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ClipTiming

/** Efeitos sonoros das interações (arquivos em res/raw, gerados em assets-source/hoodie/audio/v2). */
enum class InteractionSound { TAP_1, TAP_2, TAP_3, PET_HEAD_1, PET_HEAD_2, PURR, NUZZLE }

/** Um som e quando ele toca, em ms depois do início real da reação. */
data class InteractionSoundCue(val offsetMs: Long, val sound: InteractionSound)

/**
 * Que som toca em cada reação, sincronizado com a reprodução apresentada:
 *
 * - Toque: um "pop" no início, alternando entre três afinações (nunca o mesmo duas vezes seguidas).
 * - Carinho: cada etapa toca no início da sua animação (PET_HEAD → PURR → NUZZLE), com o mesmo
 *   ajuste de tempo da reprodução quando a sequência é encurtada pelo limite de duração. Só toca
 *   o som das etapas que de fato fazem parte da resposta.
 *
 * Função pura: os mesmos dados produzem sempre o mesmo plano.
 */
object InteractionSoundPlan {
    private val TAPS = listOf(InteractionSound.TAP_1, InteractionSound.TAP_2, InteractionSound.TAP_3)
    private val PETS = listOf(InteractionSound.PET_HEAD_1, InteractionSound.PET_HEAD_2)

    fun cues(response: InteractionResponse, requestId: Long): List<InteractionSoundCue> {
        if (response.animations.isEmpty()) return emptyList()
        if (response.gesture == InteractionGesture.TAP && response.animations.none { it in PETTING }) {
            return listOf(InteractionSoundCue(0, TAPS[Math.floorMod(requestId, TAPS.size.toLong()).toInt()]))
        }
        val durations = response.animations.map { ClipTiming.total(it.clip.durations) }
        val natural = durations.sum().coerceAtLeast(1)
        val played = natural.coerceAtMost(response.maxDurationMs)
        var cursor = 0L
        return buildList {
            response.animations.forEachIndexed { i, animation ->
                val offset = cursor * played / natural
                soundFor(animation, requestId)?.let { add(InteractionSoundCue(offset, it)) }
                cursor += durations[i]
            }
        }.filter { it.offsetMs < played }
    }

    private fun soundFor(animation: AnimationId, requestId: Long): InteractionSound? = when (animation) {
        AnimationId.PET_HEAD -> PETS[Math.floorMod(requestId, PETS.size.toLong()).toInt()]
        AnimationId.PURR -> InteractionSound.PURR
        AnimationId.NUZZLE -> InteractionSound.NUZZLE
        else -> null
    }

    private val PETTING = setOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE)
}
