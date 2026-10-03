package com.hoodie.app.engine.diary

import com.hoodie.app.domain.diary.model.JourneyMapData

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
}
