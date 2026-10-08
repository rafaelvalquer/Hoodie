package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.detection.*
import com.hoodie.app.engine.detection.ConfidenceEngine
import com.hoodie.app.engine.detection.EvidenceEngine

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

    fun detection(e: MobilityEvidence, timestamp: Long = 0L): DetectionResult = EvidenceEngine.evaluate(buildList {
        if (e.movement) add(DetectionEvidence(EvidenceType.MOVEMENT, .30f, timestamp))
        if (e.geofenceExit) add(DetectionEvidence(EvidenceType.GEOFENCE_EXIT, .40f, timestamp))
        if (e.sustained) add(DetectionEvidence(EvidenceType.SUSTAINED_ACTIVITY, .15f, timestamp))
        if (e.timeMatch) add(DetectionEvidence(EvidenceType.TIME_MATCH, .10f, timestamp))
        if (e.history) add(DetectionEvidence(EvidenceType.HISTORY, .15f, timestamp))
    }, timestamp)

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
        if (HoodieConfig.UNIFIED_CONFIDENCE_ENGINE) return when (ConfidenceEngine.decide(detection(e))) {
            DetectionDecision.UNKNOWN -> MobilityDecision.IGNORE
            DetectionDecision.ASK_USER -> MobilityDecision.ASK
            DetectionDecision.PROVISIONAL, DetectionDecision.AUTO_ACCEPT -> MobilityDecision.APPLY
        }
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
