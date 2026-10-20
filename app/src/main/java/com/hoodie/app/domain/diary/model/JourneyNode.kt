package com.hoodie.app.domain.diary.model

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType

/**
 * Uma parada da jornada (uma visita). O id segue o índice da visita no dia
 * ("journey-<i>"), igual ao replay ("visit-<i>").
 */
data class JourneyNode(
    val id: String,
    val visitIndex: Int,
    val placeId: Long?,
    val placeName: String,
    val placeType: PlaceType,
    val arrivalAt: Long,
    val departureAt: Long?,
    val durationMs: Long,
    val hoodieActivity: HoodieActivity?,
    val contextType: UserContextType?,
    /** Celular medido dentro desta visita (não o agregado do contexto). */
    val phoneUsageMs: Long = 0L,
    /** Quantas vezes este lugar já tinha aparecido antes hoje (0 = primeira vez). */
    val revisitCount: Int = 0,
    /** Quantas vezes o lugar aparece no dia inteiro. */
    val placeOccurrences: Int = 1,
    /** Tempo somado de todas as visitas a este lugar no dia. */
    val placeTotalMs: Long = durationMs,
    /**
     * Eventos canônicos que formam esta permanência (vários quando a Jornada uniu visitas
     * consecutivas ao mesmo lugar). Edição e detalhes usam estes IDs — nunca o índice visual.
     */
    val sourceIntervals: List<VisitSourceInterval> = emptyList(),
) {
    /** Permanência formada por mais de um evento de contexto. */
    val isConsolidated: Boolean get() = sourceIntervals.size > 1

    /** "Retorno #2" a partir da segunda aparição. */
    val isReturn: Boolean get() = revisitCount > 0
    val returnNumber: Int get() = revisitCount + 1

    /** Mesmo lugar físico (por id; sem id, pelo nome + tipo). */
    val placeKey: String get() = placeId?.let { "id:$it" } ?: "name:${placeName.lowercase()}|$placeType"
}
