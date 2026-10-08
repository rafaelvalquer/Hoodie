package com.hoodie.app.pixel.npc.office

/** Fases comuns para diagnóstico e composição de sequências determinísticas do escritório. */
enum class OfficeActionPhase { IDLE, STANDING_UP, TURNING, WALKING, INTERACTING, RETURNING, SITTING_DOWN }

data class OfficeNpcAction(
    val id: String,
    val npcId: String,
    val intent: com.hoodie.app.pixel.npc.brain.NpcIntent,
    val origin: OfficeNpcSpot,
    val destination: OfficeNpcSpot,
    val startedAt: Long,
    val arrivedAt: Long,
    val interactionEndsAt: Long,
    val finishedAt: Long,
    val route: List<OfficeSpot>,
)

data class OfficeSpotReservation(val npcId: String, val spot: OfficeNpcSpot, val reservedFrom: Long, val reservedUntil: Long)
data class OfficeSpeechEvent(val npcId: String, val line: String, val startedAt: Long, val durationMs: Long, val targetNpcId: String? = null)
