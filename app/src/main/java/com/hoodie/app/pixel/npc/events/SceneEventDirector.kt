package com.hoodie.app.pixel.npc.events

import com.hoodie.app.pixel.scene.SceneId

/** Microeventos de NPC: pequenas histórias que acontecem ao redor do Hoodie, sem envolvê-lo. */
enum class SceneStoryEventType(val scene: SceneId) {
    OFFICE_DOCUMENT_DELIVERY(SceneId.OFFICE),
    OFFICE_COFFEE_CHAT(SceneId.OFFICE),
    RESTAURANT_SPILL_HELP(SceneId.RESTAURANT),
    CAFE_ORDER_HANDOFF(SceneId.BAKERY),
    LEISURE_SHARED_VIEW(SceneId.LEISURE),
    BEACH_SHARED_PHOTO(SceneId.BEACH),
    SHOPPING_DROPPED_BAG(SceneId.SHOPPING),
    HOTEL_LUGGAGE_HELP(SceneId.HOTEL),
}

enum class StoryEventPhase { PLANNED, APPROACHING, INTERACTING, RESOLVING, COMPLETED, CANCELLED }

/**
 * Um evento planejado. [startAt]..[endsAt] cobre aproximação, interação e volta: os participantes
 * só são liberados para a rotina deles depois de voltarem ao lugar de origem.
 */
data class SceneStoryEvent(
    val id: String,
    val type: SceneStoryEventType,
    val scene: SceneId,
    val participants: List<String>,
    val requiredSpots: List<String>,
    val startAt: Long,
    val durationMs: Long,
    val priority: Int,
    val cooldownMs: Long,
    /** Marcos internos (fim da aproximação, fim da interação) para a fase em cada instante. */
    val interactAt: Long = startAt,
    val resolveAt: Long = startAt + durationMs,
) {
    val endsAt: Long get() = startAt + durationMs
    init {
        require(durationMs > 0)
        require(startAt <= interactAt && interactAt <= resolveAt && resolveAt <= endsAt)
    }
}

/** Quem está com um objeto da história. Um objeto tem um único dono por vez: nunca há cópias. */
enum class StoryObjectHolder { ON_DESK, HELD_BY_RABBIT, HELD_BY_BULLDOG, CARRIED_OUT, ON_TABLE, ON_FLOOR, HELD_BY_OWNER, HELD_BY_HELPER }

data class StoryObjectState(val objectId: String, val holder: StoryObjectHolder)

data class SceneStorySnapshot(
    val event: SceneStoryEvent,
    val phase: StoryEventPhase,
    val objects: List<StoryObjectState> = emptyList(),
) {
    fun holderOf(objectId: String): StoryObjectHolder? = objects.firstOrNull { it.objectId == objectId }?.holder
}

/** Contrato de cada cena que encena microeventos. */
interface SceneStoryController {
    /** O evento pode começar em [at]? (participantes livres, pontos e caminhos disponíveis, sem conflito). */
    fun canStart(type: SceneStoryEventType, at: Long): Boolean
    /** Estado do evento ativo (ou do objeto ainda em cena) no instante; null = nada acontecendo. */
    fun snapshotAt(timeMs: Long): SceneStorySnapshot?
    fun dispose()
}

/**
 * Regras comuns a todas as cenas: orçamento de eventos especiais (no máximo [maxEvents] a cada
 * [windowMs]), ordem de prioridade e fase de um evento em cada instante.
 *
 * Execução de um evento: disponibilidade → reservas → caminhos → conflitos → reservar
 * participantes e objetos → executar → liberar só depois da volta. Cancelar devolve estados
 * válidos (o NPC volta andando; ninguém é teletransportado).
 */
object SceneEventDirector {
    const val MAX_EVENTS_PER_WINDOW = 2
    const val WINDOW_MS = 10 * 60_000L

    /** O orçamento permite começar mais um evento em [at], dados os já aceitos? */
    fun budgetAllows(accepted: List<SceneStoryEvent>, at: Long, maxEvents: Int = MAX_EVENTS_PER_WINDOW, windowMs: Long = WINDOW_MS): Boolean =
        accepted.count { it.startAt > at - windowMs && it.startAt <= at } < maxEvents

    /** Respeita o cooldown do mesmo tipo de evento. */
    fun cooldownAllows(accepted: List<SceneStoryEvent>, type: SceneStoryEventType, at: Long): Boolean =
        accepted.lastOrNull { it.type == type }?.let { at >= it.endsAt + it.cooldownMs } != false

    /**
     * Aceita candidatos em ordem de tempo (e prioridade no mesmo instante), sem sobreposição de
     * participantes, dentro do orçamento e dos cooldowns. Determinístico: mesma lista, mesmo resultado.
     */
    fun admit(candidates: List<SceneStoryEvent>, maxEvents: Int = MAX_EVENTS_PER_WINDOW, windowMs: Long = WINDOW_MS): List<SceneStoryEvent> {
        val accepted = mutableListOf<SceneStoryEvent>()
        for (event in candidates.sortedWith(compareBy<SceneStoryEvent> { it.startAt }.thenByDescending { it.priority }.thenBy { it.id })) {
            val overlapping = accepted.any { other ->
                other.startAt < event.endsAt && event.startAt < other.endsAt && other.participants.any { it in event.participants }
            }
            if (overlapping) continue
            if (!budgetAllows(accepted, event.startAt, maxEvents, windowMs)) continue
            if (!cooldownAllows(accepted, event.type, event.startAt)) continue
            accepted += event
        }
        return accepted
    }

    fun phaseAt(event: SceneStoryEvent, timeMs: Long, cancelledAt: Long? = null): StoryEventPhase = when {
        cancelledAt != null && timeMs >= cancelledAt -> StoryEventPhase.CANCELLED
        timeMs < event.startAt -> StoryEventPhase.PLANNED
        timeMs < event.interactAt -> StoryEventPhase.APPROACHING
        timeMs < event.resolveAt -> StoryEventPhase.INTERACTING
        timeMs < event.endsAt -> StoryEventPhase.RESOLVING
        else -> StoryEventPhase.COMPLETED
    }
}
