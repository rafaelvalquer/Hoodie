package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.sprite.HoodiePose

/**
 * Eventos marcados no próprio frame, para props e efeitos sincronizarem sem o
 * código externo adivinhar "em qual frame a caneca é pega".
 */
enum class AnimationEvent {
    FOOTSTEP, SIT, STAND,
    MUG_PICKUP, MUG_PUT,
    PHONE_PICK, PHONE_PUT,
    BED_ENTER, BED_EXIT,
    FOOD_SERVED, FOOD_DONE,
    ITEM_PICK, ITEM_PUT,
    SPARKLE,

    // Só eventos que mudam o estado visual da cena (props), nunca cada movimento.
    BOOK_OPEN, PAGE_TURN,
    ITEM_PICKED, ITEM_IN_CART, ITEM_AT_CHECKOUT, PAYMENT_DONE,
    SNACK_PICKED, SNACK_FINISHED,
    CAMERA_READY, PHOTO_TAKEN,
}

/** Como uma animação reage quando o estado muda no meio dela. */
enum class InterruptPolicy {
    /** Pode cortar a qualquer momento (idle). */
    IMMEDIATE,

    /** Espera o frame atual terminar. */
    FINISH_FRAME,

    /** Espera o ciclo completo (digitar, beber) — nada de corte no meio do gesto. */
    FINISH_CYCLE,

    /** Precisa tocar a sequência de saída do estado (dormir → acordar → levantar). */
    PLAY_EXIT,
}

data class AnimationFrame(
    val pose: HoodiePose,
    val durationMs: Long,
    val events: Set<AnimationEvent> = emptySet(),
)

/** Um clip com tempo por frame — muito mais orgânico do que "todos os frames = 125 ms". */
data class AnimationClip(
    val id: AnimationId,
    val frames: List<AnimationFrame>,
    val loop: Boolean,
    val interruptPolicy: InterruptPolicy,
    /** Usa a direção pedida (frente/costas/lado); senão é sempre frontal. */
    val directional: Boolean = false,
) {
    val totalMs: Long = frames.sumOf { it.durationMs }
    val durations: LongArray = frames.map { it.durationMs }.toLongArray()
}

/** Matemática de tempo compartilhada por clips procedurais e sprite sheets. */
object ClipTiming {
    fun total(durations: LongArray): Long = durations.sum().coerceAtLeast(1)

    /** Índice do frame em [elapsed]; clips sem loop seguram o último frame. */
    fun indexAt(durations: LongArray, elapsed: Long, loop: Boolean): Int {
        if (durations.isEmpty()) return 0
        val total = total(durations)
        var t = if (loop) elapsed.mod(total) else elapsed.coerceAtMost(total - 1)
        if (t < 0) t = 0
        for (i in durations.indices) {
            if (t < durations[i]) return i
            t -= durations[i]
        }
        return durations.size - 1
    }

    /** Instante (relativo ao início) em que o frame [index] termina, dentro do ciclo atual. */
    fun frameEnd(durations: LongArray, elapsed: Long, loop: Boolean): Long {
        val total = total(durations)
        val cycleStart = if (loop) elapsed - elapsed.mod(total) else 0
        var acc = cycleStart
        val idx = indexAt(durations, elapsed, loop)
        for (i in 0..idx) acc += durations[i]
        return acc
    }

    fun cycleEnd(durations: LongArray, elapsed: Long, loop: Boolean): Long {
        val total = total(durations)
        return if (loop) elapsed - elapsed.mod(total) + total else total
    }
}
