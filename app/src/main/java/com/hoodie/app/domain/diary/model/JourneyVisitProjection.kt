package com.hoodie.app.domain.diary.model

import com.hoodie.app.core.model.UserContextType

/**
 * Um evento de contexto canônico (Room) recortado no dia: a evidência de onde o usuário estava.
 * Vem sempre do ContextEventEntity — nunca de horários ou textos da timeline.
 */
data class VisitSourceInterval(
    val contextEventId: Long,
    val placeId: Long?,
    val contextType: UserContextType,
    /** Início do intervalo semifechado [startAt, endAt). */
    val startAt: Long,
    /** Fim visível (já limitado ao dia ativo e ao "agora"). */
    val endAt: Long,
    /** O evento ainda está em andamento (sem saída registrada). */
    val open: Boolean = false,
    /** O usuário corrigiu este evento no Diário. */
    val corrected: Boolean = false,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0)
}

/** Uma permanência física da Jornada e os eventos canônicos que a formam (1 ou mais). */
data class ConsolidatedJourneyVisit(
    val visit: PlaceVisit,
    val sourceIntervals: List<VisitSourceInterval>,
) {
    val sourceContextEventIds: List<Long> get() = sourceIntervals.map { it.contextEventId }
    val isMerged: Boolean get() = sourceIntervals.size > 1
}

/**
 * Visitas como a Jornada as mostra (mapa, replay, Relógio). Representação derivada: nada daqui é
 * gravado no Room nem no backup; é recalculada a cada carregamento do Diário.
 */
data class JourneyVisitProjection(val visits: List<ConsolidatedJourneyVisit>) {
    /** Houve agrupamento (alguma permanência formada por mais de um evento). */
    val hasMerges: Boolean get() = visits.any { it.isMerged }

    /** Permanência que contém o evento canônico [contextEventId]. */
    fun visitOf(contextEventId: Long): ConsolidatedJourneyVisit? = visits.firstOrNull { contextEventId in it.sourceContextEventIds }

    companion object {
        val EMPTY = JourneyVisitProjection(emptyList())
    }
}
