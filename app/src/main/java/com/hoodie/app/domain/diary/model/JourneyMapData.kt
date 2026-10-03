package com.hoodie.app.domain.diary.model

/**
 * Mapa do Dia 2.0 — Jornada Pixel. Não é geografia: é a ordem do dia.
 * Cada visita vira um nó próprio (lugares repetidos aparecem repetidos) e
 * cada deslocamento entre visitas consecutivas vira um trecho.
 */
data class JourneyMapData(
    val nodes: List<JourneyNode>,
    val segments: List<JourneySegment>,
    /** Janela do replay (mesma do Diário). */
    val startAt: Long,
    val endAt: Long,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun node(id: String?): JourneyNode? = nodes.firstOrNull { it.id == id }

    /** Trecho que sai do nó [visitIndex] (null no último). */
    fun segmentAfter(visitIndex: Int): JourneySegment? = segments.firstOrNull { it.index == visitIndex }

    /** Tempo total em deslocamento no dia. */
    val travelMs: Long get() = segments.sumOf { it.durationMs }

    companion object {
        val EMPTY = JourneyMapData(emptyList(), emptyList(), 0, 0)
    }
}
