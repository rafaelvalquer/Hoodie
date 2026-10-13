package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

/** O que a sessão sabe ao pedir uma decisão: restrições do mundo, não da personalidade. */
data class VenueDecisionContext(
    val now: Long,
    /** Ações possíveis agora (ponto livre, parceiro disponível, balcão aberto…). */
    val available: Set<VenueAction>,
    /** Últimas ações, mais recente no fim. */
    val recent: List<VenueAction>,
    /** Instante a partir do qual cada ação pode se repetir. */
    val readyAt: Map<VenueAction, Long> = emptyMap(),
)

/**
 * Escolhe a próxima atividade pelos pesos do perfil, respeitando restrições e histórico:
 *  - só ações disponíveis agora e fora de cooldown;
 *  - nunca mais de [MAX_REPEAT] vezes seguidas a mesma;
 *  - sorteio determinístico por (personagem, semente, número da decisão).
 */
object VenueNpcBrain {
    const val MAX_REPEAT = 2

    fun validWeights(profile: VenueNpcProfile, ctx: VenueDecisionContext): Map<VenueAction, Int> =
        profile.preferredActions.filter { (action, weight) ->
            weight > 0 && action in ctx.available &&
                (ctx.readyAt[action] ?: 0L) <= ctx.now &&
                !(ctx.recent.size >= MAX_REPEAT && ctx.recent.takeLast(MAX_REPEAT).all { it == action })
        }

    /** null = nada possível agora (a sessão espera um pouco e pergunta de novo). */
    fun choose(profile: VenueNpcProfile, seed: Int, decision: Long, ctx: VenueDecisionContext): VenueAction? {
        val weights = validWeights(profile, ctx)
        val total = weights.values.sum()
        if (total <= 0) return null
        var roll = NpcDeterministicRandom.value(profile.id, seed, decision * 31 + 5) * total
        for ((action, weight) in weights.entries.sortedBy { it.key.ordinal }) {
            roll -= weight
            if (roll < 0f) return action
        }
        return weights.keys.maxBy { it.ordinal }
    }

    /** Pausa entre ações do perfil (ritmos diferentes por personagem). */
    fun idleMs(profile: VenueNpcProfile, seed: Int, decision: Long): Long {
        val r = NpcDeterministicRandom.value(profile.id, seed, decision * 31 + 11)
        return profile.minIdleMs + (r * (profile.maxIdleMs - profile.minIdleMs)).toLong()
    }
}
