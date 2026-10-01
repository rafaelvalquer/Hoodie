package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory

/** Como o Hoodie reage ao dia digital: uma fala curta + o "humor" para escolher a animação. */
enum class DigitalMood { CALM, CURIOUS, MUSICAL, PLAYFUL, BUSY, TIRED }

data class DigitalReaction(val mood: DigitalMood, val text: String)

/**
 * Regras determinísticas (sem IA, como o resto do Hoodie). Nunca julga: o gato
 * comenta o dia, não dá bronca.
 */
object HoodieDigitalReactions {

    fun react(insights: DailyPhoneInsights?, catName: String = "Hoodie"): DigitalReaction {
        if (insights == null || insights.isEmpty) {
            return DigitalReaction(DigitalMood.CALM, "Celular quietinho hoje. $catName aprova.")
        }
        val s = insights.summary
        val top = insights.categoryUsage.firstOrNull()
        val longest = s.longestSessionMs
        return when {
            s.screenTimeMs >= 6 * HOUR_MS ->
                DigitalReaction(DigitalMood.TIRED, "Muita tela hoje... que tal uma pausa? Eu tirei três cochilos.")
            longest >= 90 * MINUTE_MS ->
                DigitalReaction(DigitalMood.CURIOUS, "Teve uma sessão longa de ${longest / MINUTE_MS} min! Era coisa boa?")
            top?.category == HoodieAppCategory.MUSIC ->
                DigitalReaction(DigitalMood.MUSICAL, "Curti a trilha sonora de hoje. 🎵")
            top?.category == HoodieAppCategory.GAMES ->
                DigitalReaction(DigitalMood.PLAYFUL, "Jogou bastante, hein? Me chama na próxima! 🎮")
            top?.category == HoodieAppCategory.WORK ->
                DigitalReaction(DigitalMood.BUSY, "Dia corrido no celular também. Bom trabalho!")
            top?.category == HoodieAppCategory.SOCIAL ->
                DigitalReaction(DigitalMood.PLAYFUL, "Muita conversa hoje! Mandou um oi por mim?")
            top?.category == HoodieAppCategory.VIDEO ->
                DigitalReaction(DigitalMood.CURIOUS, "Vi que teve vídeo hoje. Algum de gatinho?")
            s.screenTimeMs < HOUR_MS ->
                DigitalReaction(DigitalMood.CALM, "Pouca tela hoje. Mais tempo pro mundo lá fora!")
            else ->
                DigitalReaction(DigitalMood.CALM, "Um dia digital equilibrado. Gostei.")
        }
    }
}
