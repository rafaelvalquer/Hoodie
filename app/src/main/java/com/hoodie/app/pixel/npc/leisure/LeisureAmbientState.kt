package com.hoodie.app.pixel.npc.leisure

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Estado dos elementos vivos do Passeio num instante. A cena desenha a partir daqui em vez de
 * decidir sozinha: assim o pato vem à margem quando alguém observa o lago, o chafariz sobe
 * quando alguém o fotografa e a borboleta rodeia quem para entre as flores.
 *
 * [attraction] vai de 0 (ninguém olhando) a 1 (atração no auge), com rampa de entrada e saída
 * de [RAMP_MS] para nada pular entre quadros.
 */
data class LeisureAmbientState(
    val variant: Int,
    val timeMs: Long,
    val attraction: Float,
    /** Visitante que está interagindo com a atração (null = ninguém). */
    val watcherId: String?,
    val watcherX: Int,
    val watcherFloorY: Int,
    /** Chafariz: altura extra do jato (px) e o "clique" da foto. */
    val fountainBoost: Int,
    val photoFlash: Boolean,
) {
    /** Deslocamento dos patos em direção à margem onde está o visitante. */
    val duckOffsetX: Int get() = if (variant.mod(3) != 0) 0 else ((watcherX + 4 - DUCK_HOME_X) * 0.55f * attraction).roundToInt()

    /** Os patos levantam a cabeça e fazem uma marolinha quando alguém está na margem. */
    val duckAlert: Boolean get() = variant.mod(3) == 0 && attraction >= 0.5f

    /** Borboleta: voo livre de sempre, puxado para um círculo em volta da cabeça de quem observa. */
    val butterfly: Pair<Int, Int>
        get() {
            val freeX = 60 + ((timeMs / 50) % 120).toInt()
            val freeY = 196 + ((timeMs / 300) % 4).toInt() - 2
            if (variant.mod(3) != 2 || attraction <= 0f) return freeX to freeY
            val angle = timeMs / 420.0
            val orbitX = watcherX + (cos(angle) * 11).roundToInt()
            val orbitY = watcherFloorY - HEAD_ABOVE_FEET + (sin(angle) * 5).roundToInt()
            val k = attraction
            return (freeX + (orbitX - freeX) * k).roundToInt() to (freeY + (orbitY - freeY) * k).roundToInt()
        }

    val butterflyWingUp: Boolean get() = (timeMs / 140) % 2 == 0L

    companion object {
        const val RAMP_MS = 1_800L
        const val DUCK_HOME_X = 58
        const val HEAD_ABOVE_FEET = 46

        fun idle(variant: Int, timeMs: Long) = LeisureAmbientState(variant, timeMs, 0f, null, 0, 0, 0, false)
    }
}
