package com.hoodie.app.domain.diary.model

import com.hoodie.app.core.mobility.MovementMode

/**
 * Deslocamento entre duas paradas consecutivas. [movementMode] é o meio
 * dominante no intervalo (null = não detectado → estilo genérico).
 */
data class JourneySegment(
    val id: String,
    /** Índice da visita de origem: o trecho [index] liga o nó [index] ao [index] + 1 ("edge-<i>"). */
    val index: Int,
    val fromNodeId: String,
    val toNodeId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val movementMode: MovementMode?,
)
