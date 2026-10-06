package com.hoodie.app.engine.diary

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.diary.journey.JourneyLeg
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.journey.LinkKind
import com.hoodie.app.engine.diary.journey.OverworldLayout

/** Onde a jornada está num instante do replay. */
data class JourneyReplayState(
    val timestamp: Long?,
    /** Parada em que o Hoodie está agora (null em trânsito ou antes da primeira chegada). */
    val activeNodeIndex: Int?,
    /** Trecho em andamento (null parado). */
    val activeSegmentIndex: Int?,
    /** Quanto do trecho atual já foi andado (0..1). */
    val segmentProgress: Float,
    /** Última parada alcançada (-1 = nenhuma ainda). Sem replay: o dia todo. */
    val reachedIndex: Int,
    /** PLAYING/PAUSED: nós e trechos mostram visitado / atual / ainda não. */
    val replaying: Boolean,
) {
    companion object {
        fun idle(data: JourneyMapData) = JourneyReplayState(null, null, null, 0f, data.nodes.lastIndex, replaying = false)
    }
}

enum class JourneyEventKind { ARRIVE, DEPART }

/** Marco do dia para os botões ⏮ / ⏭: chegadas e saídas, na ordem. */
data class JourneyEvent(val timestamp: Long, val kind: JourneyEventKind, val nodeIndex: Int)

/**
 * Instante → estado da jornada, e a navegação entre eventos. Puro: o mesmo
 * timestamp sempre dá o mesmo estado (o replay pode pular para qualquer ponto).
 */
object JourneyReplayAssembler {

    /** Apertar ⏮ logo depois de um evento volta para o anterior, não para o mesmo. */
    const val PREVIOUS_GRACE_MS = 1_000L

    fun stateAt(data: JourneyMapData, timestamp: Long?, replaying: Boolean): JourneyReplayState {
        if (timestamp == null || data.isEmpty) return JourneyReplayState.idle(data).copy(replaying = replaying && !data.isEmpty)
        val t = timestamp.coerceIn(data.startAt, maxOf(data.startAt, data.endAt))
        data.segments.forEach { s ->
            if (t >= s.startedAt && t < s.endedAt) {
                val p = ((t - s.startedAt).toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                return JourneyReplayState(t, null, s.index, p, s.index, replaying)
            }
        }
        val current = data.nodes.indexOfLast { it.arrivalAt <= t }
        return JourneyReplayState(t, current.takeIf { it >= 0 }, null, 0f, current, replaying)
    }

    fun events(data: JourneyMapData): List<JourneyEvent> =
        (data.nodes.map { JourneyEvent(it.arrivalAt, JourneyEventKind.ARRIVE, it.visitIndex) } +
            data.segments.map { JourneyEvent(it.startedAt, JourneyEventKind.DEPART, it.index) })
            .filter { it.timestamp in data.startAt..data.endAt }
            .sortedWith(compareBy({ it.timestamp }, { it.kind }))

    /** Próximo evento depois de [timestamp]; sem mais eventos, o fim do dia. */
    fun next(data: JourneyMapData, timestamp: Long?): Long {
        val t = timestamp ?: return events(data).firstOrNull()?.timestamp ?: data.startAt
        return events(data).firstOrNull { it.timestamp > t }?.timestamp ?: data.endAt
    }

    /** Evento anterior; sem eventos antes, o começo do dia. */
    fun previous(data: JourneyMapData, timestamp: Long?): Long {
        val t = timestamp ?: return data.startAt
        return events(data).lastOrNull { it.timestamp < t - PREVIOUS_GRACE_MS }?.timestamp ?: data.startAt
    }

    // ───── Jornada 3.0 (overworld) ─────

    /** Fração do trecho já andada em [t] (0..1). */
    fun legFraction(leg: JourneyLeg, t: Long): Float = when {
        t >= leg.endedAt -> 1f
        t < leg.startedAt -> 0f
        else -> ((t - leg.startedAt).toFloat() / (leg.endedAt - leg.startedAt).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /**
     * Estado de um mapa do overworld (o dia inteiro ou um capítulo) num instante.
     * Um trecho entre capítulos vale metade em cada um: sai pelo portal de baixo e
     * entra pelo de cima. Mesmo timestamp → mesmo estado nas duas visualizações.
     */
    fun overworld(plan: JourneyPlan, layout: OverworldLayout, timestamp: Long?, replaying: Boolean): OverworldReplayState {
        val t = timestamp
        val phases = layout.stops.associate { s -> s.stopId to phaseOf(plan.stop(s.stopId), t, replaying) }
        val progress = layout.links.associate { link ->
            val leg = plan.leg(link.legId)
            val f = when {
                !replaying || t == null -> 1f
                leg == null -> if ((plan.stop(link.toStopId)?.startAt ?: Long.MAX_VALUE) <= t) 1f else 0f
                else -> legFraction(leg, t)
            }
            link.id to when (link.kind) {
                LinkKind.PORTAL_OUT -> (f * 2f).coerceIn(0f, 1f)
                LinkKind.PORTAL_IN -> ((f - 0.5f) * 2f).coerceIn(0f, 1f)
                else -> f
            }
        }
        return OverworldReplayState(phases, progress, hoodieAt(plan, layout, t, replaying), replaying)
    }

    private fun phaseOf(stop: JourneyStop?, t: Long?, replaying: Boolean): StopPhase = when {
        stop is JourneyStop.Ghost -> StopPhase.GHOST
        stop == null || !replaying || t == null -> StopPhase.VISITED
        stop is JourneyStop.Visit -> when {
            stop.arrivalAt > t -> StopPhase.FUTURE
            stop.departureAt == null || t < stop.departureAt -> StopPhase.CURRENT
            else -> StopPhase.VISITED
        }
        stop is JourneyStop.QuickCluster -> when {
            stop.startAt > t -> StopPhase.FUTURE
            t < stop.endAt -> StopPhase.CURRENT
            else -> StopPhase.VISITED
        }
        else -> StopPhase.VISITED
    }

    /** Onde o Hoodie está neste mapa (null se está em outro capítulo). */
    private fun hoodieAt(plan: JourneyPlan, layout: OverworldLayout, t: Long?, replaying: Boolean): HoodieAt? {
        if (layout.isEmpty) return null
        if (t == null || !replaying) {
            // Sem replay: onde o dia terminou, se for neste mapa.
            val lastVisit = plan.stops.filterIsInstance<JourneyStop.Visit>().maxByOrNull { it.arrivalAt } ?: return null
            return stopHolding(plan, layout, lastVisit.id)?.let { HoodieAt(null, 0f, it, null) }
        }
        plan.legs.firstOrNull { t >= it.startedAt && t < it.endedAt }?.let { leg ->
            val f = legFraction(leg, t)
            layout.links.firstOrNull { it.legId == leg.id }?.let { link ->
                return when (link.kind) {
                    LinkKind.PORTAL_OUT -> if (f < 0.5f) HoodieAt(link.id, f * 2f, null, leg.mode) else null
                    LinkKind.PORTAL_IN -> if (f >= 0.5f) HoodieAt(link.id, (f - 0.5f) * 2f, null, leg.mode) else null
                    else -> HoodieAt(link.id, f, null, leg.mode)
                }
            }
        }
        val current = plan.stops.filterIsInstance<JourneyStop.Visit>().filter { it.arrivalAt <= t }.maxByOrNull { it.arrivalAt }
            ?: return layout.stops.firstOrNull()?.let { HoodieAt(null, 0f, it.stopId, null) }?.takeIf { plan is JourneyPlan.Single }
        return stopHolding(plan, layout, current.id)?.let { HoodieAt(null, 0f, it, null) }
    }

    /** Parada deste mapa que representa a visita: ela mesma, o grupo que a contém ou o fantasma dela. */
    fun stopHolding(plan: JourneyPlan, layout: OverworldLayout, visitId: String): String? {
        layout.stop(visitId)?.let { return it.stopId }
        layout.stops.firstOrNull { (plan.stop(it.stopId) as? JourneyStop.QuickCluster)?.stopIds?.contains(visitId) == true }?.let { return it.stopId }
        layout.stops.firstOrNull { (plan.stop(it.stopId) as? JourneyStop.Ghost)?.ofStopId == visitId }?.let { return it.stopId }
        return null
    }
}

/** Fase visual de uma parada no replay. */
enum class StopPhase { FUTURE, VISITED, CURRENT, GHOST }

/** Hoodie andando num link (com o meio) ou parado numa parada. */
data class HoodieAt(val linkId: String?, val linkProgress: Float, val stopId: String?, val mode: MovementMode?)

data class OverworldReplayState(
    val phases: Map<String, StopPhase>,
    /** Quanto de cada link já é rastro dourado (0..1). */
    val linkProgress: Map<String, Float>,
    val hoodie: HoodieAt?,
    val replaying: Boolean,
)

