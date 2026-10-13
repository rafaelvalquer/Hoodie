package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

/**
 * Perfis de personalidade. Os números são PESOS de sorteio, não cotas: só as atividades válidas
 * para o lugar e o estado atual entram no sorteio determinístico.
 */
enum class LeisurePersonality(val weights: Map<LeisureActivity, Int>, val visitLength: IntRange) {
    /** Coelho: 35% exploração, 25% observação, 20% foto, 10% descanso, 10% social. */
    EXPLORER(
        mapOf(
            LeisureActivity.EXPLORE to 35, LeisureActivity.OBSERVE to 25, LeisureActivity.PHOTO to 20,
            LeisureActivity.STRETCH to 10, LeisureActivity.SOCIAL to 10,
        ),
        visitLength = 6..9,
    ),
    /** Gato: 30% foto, 25% exploração, 20% observação, 15% social, 10% descanso. */
    TOURIST(
        mapOf(
            LeisureActivity.PHOTO to 30, LeisureActivity.EXPLORE to 25, LeisureActivity.OBSERVE to 20,
            LeisureActivity.SOCIAL to 15, LeisureActivity.STRETCH to 10,
        ),
        visitLength = 6..9,
    ),
    /** Rato: 35% leitura, 25% descanso, 20% celular, 10% observação, 10% caminhada. */
    CALM(
        mapOf(
            LeisureActivity.READ to 35, LeisureActivity.BENCH_REST to 25, LeisureActivity.PHONE to 20,
            LeisureActivity.OBSERVE to 10, LeisureActivity.STROLL to 10,
        ),
        visitLength = 8..12,
    ),
}

/** O que o cérebro precisa saber para decidir (nada aqui depende do quadro desenhado). */
data class LeisureDecisionContext(
    val now: Long,
    val seated: Boolean,
    val night: Boolean,
    val benchAvailable: Boolean,
    val observeSpotAvailable: Boolean,
    val photoReadyAt: Long,
    val socialReadyAt: Long,
    val socialPossible: Boolean,
    /** Atividades mais recentes, a última no fim. */
    val recent: List<LeisureActivity>,
    val visitDone: Boolean,
)

object LeisureNpcBrain {
    /** Nenhuma atividade aparece mais de duas vezes seguidas. */
    const val MAX_REPEAT = 2

    fun validWeights(personality: LeisurePersonality, ctx: LeisureDecisionContext): Map<LeisureActivity, Int> {
        val repeated = ctx.recent.takeLast(MAX_REPEAT).let { last -> if (last.size == MAX_REPEAT && last.distinct().size == 1) last.first() else null }
        return personality.weights.filter { (activity, weight) ->
            weight > 0 && activity != repeated && when (activity) {
                LeisureActivity.SOCIAL -> !ctx.seated && ctx.socialPossible && ctx.now >= ctx.socialReadyAt
                LeisureActivity.PHOTO -> !ctx.seated && !ctx.night && ctx.now >= ctx.photoReadyAt && ctx.observeSpotAvailable
                LeisureActivity.OBSERVE -> ctx.observeSpotAvailable
                LeisureActivity.READ, LeisureActivity.BENCH_REST -> ctx.seated || ctx.benchAvailable
                LeisureActivity.STRETCH -> !ctx.seated
                LeisureActivity.EXPLORE, LeisureActivity.STROLL, LeisureActivity.PHONE -> true
                LeisureActivity.EXIT -> false
            }
        }
    }

    /**
     * Próxima atividade. Fim de visita vira saída de cena; sem nenhuma opção válida, um passeio
     * curto (sempre possível).
     */
    fun choose(npcId: String, daySeed: Int, decisionIndex: Long, personality: LeisurePersonality, ctx: LeisureDecisionContext): LeisureActivity {
        if (ctx.visitDone) return LeisureActivity.EXIT
        val valid = validWeights(personality, ctx)
        if (valid.isEmpty()) return LeisureActivity.STROLL
        val total = valid.values.sum()
        var pick = NpcDeterministicRandom.value(npcId, daySeed, decisionIndex * 7 + 1) * total
        for ((activity, weight) in valid) {
            if (pick < weight) return activity
            pick -= weight
        }
        return valid.keys.last()
    }

    /** Quanto tempo a atividade dura parada (sem contar o trajeto). */
    fun holdMs(activity: LeisureActivity, seated: Boolean, npcId: String, daySeed: Int, decisionIndex: Long): Long {
        fun between(min: Long, max: Long) = min + (NpcDeterministicRandom.value(npcId, daySeed, decisionIndex * 7 + 3) * (max - min)).toLong()
        return when (activity) {
            LeisureActivity.EXPLORE, LeisureActivity.STROLL -> between(2_000, 4_000)
            LeisureActivity.OBSERVE -> between(2_600, 4_000)
            LeisureActivity.PHOTO -> PHOTO_MS
            LeisureActivity.STRETCH -> STRETCH_MS
            LeisureActivity.PHONE -> if (seated) between(3_000, 4_000) else between(2_000, 4_000)
            LeisureActivity.READ, LeisureActivity.BENCH_REST -> between(5_000, 12_000)
            LeisureActivity.SOCIAL, LeisureActivity.EXIT -> 0
        }
    }

    /** Uma foto completa: enquadra, ergue o celular, clica e abaixa (ciclo de STAND_PHOTO). */
    const val PHOTO_MS = 3_800L
    /** Três ciclos de alongamento (descanso dentro da faixa de 5–12 s). */
    const val STRETCH_MS = 7_200L
}
