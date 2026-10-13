package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.events.SceneEventDirector
import com.hoodie.app.pixel.npc.events.SceneStoryEvent
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.npc.events.SceneStorySnapshot
import com.hoodie.app.pixel.npc.events.StoryObjectTimeline

/** Um microevento já planejado na sessão: evento e objetos (o roteiro está nos segmentos dos NPCs). */
data class VenueStoryRecord(
    val event: SceneStoryEvent,
    val objects: List<StoryObjectTimeline> = emptyList(),
    val lingerMs: Long = 0L,
) {
    fun covers(t: Long) = t >= event.startAt && t < event.endsAt + lingerMs
}

/**
 * Microeventos de um estabelecimento. Ficam dentro da própria simulação (os roteiros usam as
 * mesmas reservas, pedidos e falas da sessão), então seguem a mesma regra de determinismo:
 * mesma chave + mesmo instante = mesmo evento, e voltar no tempo refaz tudo do zero.
 */
internal class VenueStoryLog {
    private val records = ArrayList<VenueStoryRecord>()

    val size: Int get() = records.size

    fun all(): List<VenueStoryRecord> = records

    /** Orçamento (2 a cada 10 min), cooldown do tipo e ninguém em dois eventos ao mesmo tempo. */
    fun canStart(type: SceneStoryEventType, at: Long, participants: List<String>): Boolean {
        val events = records.map { it.event }
        if (!SceneEventDirector.budgetAllows(events, at) || !SceneEventDirector.cooldownAllows(events, type, at)) return false
        return records.none { r -> at < r.event.endsAt && r.event.participants.any { it in participants } }
    }

    fun record(record: VenueStoryRecord) {
        records.removeAll { it.event.id == record.event.id }
        records += record
        records.sortBy { it.event.startAt }
        // Teto de memória: o orçamento só olha 10 min para trás.
        while (records.size > MAX_RECORDS) records.removeAt(0)
    }

    private companion object { const val MAX_RECORDS = 64 }

    fun find(id: String): VenueStoryRecord? = records.firstOrNull { it.event.id == id }

    fun snapshotAt(t: Long): SceneStorySnapshot? = records.lastOrNull { it.covers(t) }?.let { r ->
        SceneStorySnapshot(r.event, SceneEventDirector.phaseAt(r.event, t), r.objects.mapNotNull { it.stateAt(t) })
    }

    fun prune(before: Long) {
        records.removeAll { it.event.endsAt + it.lingerMs < before && it.event.startAt < before - SceneEventDirector.WINDOW_MS }
    }

    fun clear() = records.clear()
}
