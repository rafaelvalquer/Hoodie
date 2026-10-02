package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig

/** O que se sabe sobre um possível deslocamento. */
data class MobilityEvidence(
    /** Activity Recognition viu movimento (andar, correr, pedalar, veículo). */
    val movement: Boolean,
    /** Saiu da geofence do lugar onde estava. */
    val geofenceExit: Boolean,
    /** Movimento contínuo pelo tempo mínimo do modo. */
    val sustained: Boolean,
    /** Horário típico de saída pela rotina (dia de trabalho, perto da entrada/saída). */
    val timeMatch: Boolean,
    /** Já fez esse trajeto, nesse horário, outras vezes. */
    val history: Boolean,
    /** Estava dentro de um lugar conhecido e a geofence ainda não disse que saiu. */
    val insideKnownPlace: Boolean = false,
)

enum class MobilityDecision { IGNORE, ASK, APPLY }

/**
 * Mesma filosofia do ContextScorer: pontos por evidência, depois faixas.
 *
 *     movimento +30 · saída da geofence +40 · contínuo +15 · horário +10 · histórico +15
 *     0–39 ignora · 40–79 pergunta · 80+ aplica
 *
 * Dentro de um lugar conhecido sem saída da geofence nada acontece: andar pela casa
 * não é sair de casa — quem decide a saída de um lugar conhecido é a geofence.
 */
object MobilityConfidenceScorer {

    fun score(e: MobilityEvidence): Int {
        var s = 0
        if (e.movement) s += HoodieConfig.MOBILITY_SCORE_MOVEMENT
        if (e.geofenceExit) s += HoodieConfig.MOBILITY_SCORE_GEOFENCE_EXIT
        if (e.sustained) s += HoodieConfig.MOBILITY_SCORE_SUSTAINED
        if (e.timeMatch) s += HoodieConfig.MOBILITY_SCORE_TIME_MATCH
        if (e.history) s += HoodieConfig.MOBILITY_SCORE_HISTORY
        return s.coerceAtMost(100)
    }

    fun decide(e: MobilityEvidence): MobilityDecision {
        // Só geofence (sem movimento reconhecido) é o fluxo anterior do ContextEngine: nada a fazer aqui.
        if (!e.movement) return MobilityDecision.IGNORE
        if (e.insideKnownPlace && !e.geofenceExit) return MobilityDecision.IGNORE
        val s = score(e)
        return when {
            s >= HoodieConfig.MOBILITY_APPLY_SCORE -> MobilityDecision.APPLY
            s >= HoodieConfig.MOBILITY_ASK_SCORE -> MobilityDecision.ASK
            else -> MobilityDecision.IGNORE
        }
    }

    /** 0..1 para guardar na sessão. */
    fun confidence(e: MobilityEvidence): Float = score(e) / 100f
}
