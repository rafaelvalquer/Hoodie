package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

/**
 * Falas curtas do Passeio (≤ 22 caracteres, o limite do balão). O balão compartilhado só
 * desenha letras, números, vírgula e ponto, então nada de "!" ou "?".
 */
object LeisureSpeechLibrary {
    const val MAX_CHARS = 22

    enum class Situation { GREETING, REPLY, TOPIC_LAKE, TOPIC_FOUNTAIN, TOPIC_FLOWERS, TOPIC_VIEW, FAREWELL, LAKE_COMMENT, FOUNTAIN_COMMENT, FLOWER_COMMENT }

    private val lines: Map<Situation, List<String>> = mapOf(
        Situation.GREETING to listOf("Bom dia", "Oi, tudo bem", "Que bom te ver"),
        Situation.REPLY to listOf("Ola", "Tudo otimo", "Oi, que dia bonito"),
        Situation.TOPIC_LAKE to listOf("Olha os patos", "O lago esta calmo"),
        Situation.TOPIC_FOUNTAIN to listOf("Que vista", "O chafariz ligou"),
        Situation.TOPIC_FLOWERS to listOf("Olha as flores", "Viu a borboleta"),
        Situation.TOPIC_VIEW to listOf("Que vista", "Dia bonito"),
        Situation.FAREWELL to listOf("Ate logo", "Bom passeio", "Ate mais"),
        Situation.LAKE_COMMENT to listOf("Olha os patos"),
        Situation.FOUNTAIN_COMMENT to listOf("Que vista"),
        Situation.FLOWER_COMMENT to listOf("Olha a borboleta"),
    )

    init {
        lines.values.flatten().forEach { line ->
            require(line.length <= MAX_CHARS) { "fala longa demais para o balão: $line" }
            require(line.none { it == '!' || it == '?' }) { "o balão não desenha ! nem ?: $line" }
        }
    }

    fun line(situation: Situation, npcId: String, daySeed: Int, index: Long): String {
        val options = lines.getValue(situation)
        return options[NpcDeterministicRandom.choose(npcId, daySeed, index, options.size)]
    }

    fun topic(variant: Int): Situation = when (variant.mod(3)) {
        0 -> Situation.TOPIC_LAKE
        1 -> Situation.TOPIC_FOUNTAIN
        else -> Situation.TOPIC_FLOWERS
    }

    fun natureComment(variant: Int): Situation = when (variant.mod(3)) {
        0 -> Situation.LAKE_COMMENT
        1 -> Situation.FOUNTAIN_COMMENT
        else -> Situation.FLOWER_COMMENT
    }

    fun all(): List<String> = lines.values.flatten()
}
