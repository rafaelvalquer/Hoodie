package com.hoodie.app.pixel.npc.office


enum class NpcSpeechTopic { GREETING, WORK, COFFEE, LUNCH, AFTERNOON, SMALL_TALK, END_OF_DAY }

data class NpcSpeechLine(val text: String, val topic: NpcSpeechTopic, val weight: Int = 1)

object OfficeSpeechLibrary {
    private val morning = listOf(
        NpcSpeechLine("Bom dia.", NpcSpeechTopic.GREETING),
        NpcSpeechLine("Cafe agora?", NpcSpeechTopic.COFFEE),
        NpcSpeechLine("Dia cheio hoje.", NpcSpeechTopic.WORK),
        NpcSpeechLine("Comecou cedo.", NpcSpeechTopic.SMALL_TALK),
    )
    private val lunch = listOf(
        NpcSpeechLine("Vai almocar?", NpcSpeechTopic.LUNCH),
        NpcSpeechLine("Pausa para cafe.", NpcSpeechTopic.COFFEE),
        NpcSpeechLine("Como vai o projeto?", NpcSpeechTopic.WORK),
    )
    private val afternoon = listOf(
        NpcSpeechLine("Falta pouco.", NpcSpeechTopic.AFTERNOON),
        NpcSpeechLine("Relatorio pronto?", NpcSpeechTopic.WORK),
        NpcSpeechLine("Mais um cafe?", NpcSpeechTopic.COFFEE),
    )
    private val endOfDay = listOf(
        NpcSpeechLine("Ate amanha.", NpcSpeechTopic.END_OF_DAY),
        NpcSpeechLine("Bom descanso.", NpcSpeechTopic.END_OF_DAY),
    )

    fun linesAt(clockMinute: Int): List<NpcSpeechLine> = when (clockMinute.coerceIn(0, 1439)) {
        in 0..10 * 60 + 59 -> morning
        in 11 * 60..13 * 60 + 29 -> lunch
        in 13 * 60 + 30..17 * 60 + 29 -> afternoon
        else -> endOfDay
    }
}
