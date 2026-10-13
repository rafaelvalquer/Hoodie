package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

/**
 * Encontros entre visitantes: aproximação → virar um para o outro → cumprimento → resposta →
 * assunto → despedida → cada um volta à sua rotina. Regras:
 *
 * - um encontro por vez na cena;
 * - os dois precisam estar livres (nem sentados, nem saindo, sem cooldown social);
 * - as duas posições de conversa são reservadas antes de qualquer um andar;
 * - só um balão por vez, alternando quem fala;
 * - depois do encontro, cada participante espera [COOLDOWN_MIN_MS]–[COOLDOWN_MAX_MS] para outro.
 */
class LeisureSocialCoordinator(private val daySeed: Int, private val variant: Int) {
    data class Encounter(val id: Int, val initiator: String, val partner: String, val meetAt: Long, val endAt: Long)

    /** Uma fala do roteiro; quem não fala escuta virado para o outro. */
    data class Line(val byInitiator: Boolean, val text: String, val durationMs: Long)

    private val encounters = ArrayList<Encounter>()
    private var nextId = 1

    fun reset() { encounters.clear(); nextId = 1 }

    /** Há encontro (planejado ou acontecendo) que se sobrepõe a [from, until)? */
    fun busy(from: Long, until: Long): Boolean = encounters.any { it.meetAt - APPROACH_MARGIN_MS < until && from < it.endAt + RELEASE_MS }

    fun register(initiator: String, partner: String, meetAt: Long, endAt: Long): Encounter =
        Encounter(nextId++, initiator, partner, meetAt, endAt).also {
            encounters += it
            if (encounters.size > MAX_HISTORY) encounters.removeAt(0)
        }

    fun activeAt(t: Long): Encounter? = encounters.firstOrNull { t >= it.meetAt && t < it.endAt }

    fun all(): List<Encounter> = encounters.toList()

    fun cooldownMs(npcId: String, index: Long): Long =
        COOLDOWN_MIN_MS + (NpcDeterministicRandom.value(npcId, daySeed, index * 13 + 5) * (COOLDOWN_MAX_MS - COOLDOWN_MIN_MS)).toLong()

    /** Cumprimento, resposta, assunto do lugar e despedida. */
    fun script(initiator: String, partner: String, index: Long): List<Line> = listOf(
        Line(true, LeisureSpeechLibrary.line(LeisureSpeechLibrary.Situation.GREETING, initiator, daySeed, index), 1_600),
        Line(false, LeisureSpeechLibrary.line(LeisureSpeechLibrary.Situation.REPLY, partner, daySeed, index + 1), 1_500),
        Line(true, LeisureSpeechLibrary.line(LeisureSpeechLibrary.topic(variant), initiator, daySeed, index + 2), 1_700),
        Line(false, LeisureSpeechLibrary.line(LeisureSpeechLibrary.Situation.FAREWELL, partner, daySeed, index + 3), 1_500),
    )

    companion object {
        const val COOLDOWN_MIN_MS = 60_000L
        const val COOLDOWN_MAX_MS = 100_000L
        /** Tempo máximo que o iniciador aceita esperar o outro terminar o que está fazendo. */
        const val MAX_PARTNER_WAIT_MS = 6_000L
        /** Pausa entre uma fala e a próxima (respiro entre balões). */
        const val LINE_GAP_MS = 250L
        /** Reservas seguem até os dois se afastarem fisicamente. */
        const val RELEASE_MS = 1_200L
        private const val APPROACH_MARGIN_MS = 8_000L
        private const val MAX_HISTORY = 32
    }
}
