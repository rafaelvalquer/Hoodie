package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.npc.brain.NpcIntent
import com.hoodie.app.pixel.npc.events.SceneEventDirector
import com.hoodie.app.pixel.npc.events.SceneStoryController
import com.hoodie.app.pixel.npc.events.SceneStoryEvent
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.npc.events.SceneStorySnapshot
import com.hoodie.app.pixel.npc.events.StoryObjectHolder
import com.hoodie.app.pixel.npc.events.StoryObjectState
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import kotlin.math.abs

/** Uma entrega planejada: os instantes de cada passo, em tempo da cena. */
data class OfficeDocumentDelivery(
    val event: SceneStoryEvent,
    val cycle: Long,
    val documentId: String,
    val cycleStart: Long,
    val meetX: Int,
    val meetY: Int,
    val bulldogSpot: OfficeNpcSpot,
    /** O coelho se levanta; a folha sai da mesa quando ele termina de levantar. */
    val startAt: Long,
    val pickupAt: Long,
    val walkOutAt: Long,
    val arriveAt: Long,
    val handoffAt: Long,
    val returnAt: Long,
    val deskArriveAt: Long,
    val sitAt: Long,
    /** O bulldog lê a folha parado até aqui e sai do escritório com ela. */
    val bulldogReadUntil: Long,
    val bulldogExitAt: Long,
) {
    val endsAt: Long get() = event.endsAt
}

/**
 * OFFICE_DOCUMENT_DELIVERY: durante uma visita do bulldog, o coelho leva uma folha da mesa dele até
 * o chefe. A folha é um objeto único (ON_DESK → HELD_BY_RABBIT → HELD_BY_BULLDOG → CARRIED_OUT):
 * nunca existem duas cópias. A cena não tem mesa para o bulldog, então ele sai do escritório com
 * ela. Integrado ao [OfficeSocialSession]: só acontece quando o coelho e o gato estão sentados nas
 * próprias mesas pela entrega inteira, o bulldog está parado num ponto livre e nenhuma conversa
 * da rotina está marcada. A rotina-base do coelho não pausa: ele volta à mesma ação sentada.
 */
class OfficeDocumentDeliveries internal constructor(
    private val session: OfficeSocialSession,
    private val daySeed: Int,
) : SceneStoryController {
    private val planned = ArrayList<OfficeDocumentDelivery?>()
    private val accepted = ArrayList<SceneStoryEvent>()
    private var visitWindows: List<Pair<OfficeNpcSpot, LongRange>>? = null
    @Volatile private var disposed = false

    override fun canStart(type: SceneStoryEventType, at: Long): Boolean =
        type == SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY && !disposed &&
            SceneEventDirector.budgetAllows(accepted, at) && SceneEventDirector.cooldownAllows(accepted, type, at)

    @Synchronized override fun dispose() {
        disposed = true
        planned.clear(); accepted.clear(); visitWindows = null
    }

    /** Entrega do ciclo de visita que contém [timeMs] (null = não há entrega nesse ciclo). */
    @Synchronized fun deliveryAt(timeMs: Long): OfficeDocumentDelivery? {
        if (disposed || timeMs < 0) return null
        val cycle = timeMs / OfficeExecutiveTimeline.CYCLE_MS
        // Ciclos são planejados em ordem: o orçamento de eventos olha os anteriores.
        while (planned.size <= cycle) planned += plan(planned.size.toLong())
        return planned[cycle.toInt()]
    }

    override fun snapshotAt(timeMs: Long): SceneStorySnapshot? {
        val d = deliveryAt(timeMs) ?: return null
        if (timeMs < d.cycleStart) return null
        val holder = when {
            timeMs < d.pickupAt -> StoryObjectHolder.ON_DESK
            timeMs < d.handoffAt -> StoryObjectHolder.HELD_BY_RABBIT
            timeMs < d.bulldogExitAt -> StoryObjectHolder.HELD_BY_BULLDOG
            else -> StoryObjectHolder.CARRIED_OUT
        }
        return SceneStorySnapshot(d.event, SceneEventDirector.phaseAt(d.event, timeMs), listOf(StoryObjectState(d.documentId, holder)))
    }

    /** Movimento do participante durante a entrega (null = segue a rotina normal). */
    fun movementAt(npcId: String, timeMs: Long): NpcMovement? {
        val d = deliveryAt(timeMs) ?: return null
        return when (npcId) {
            RABBIT -> if (timeMs in d.startAt until d.endsAt) rabbitMovement(d, timeMs) else null
            BULLDOG -> if (timeMs in d.arriveAt until d.bulldogReadUntil) bulldogMovement(d, timeMs) else null
            else -> null
        }
    }

    /** O objeto na mão de [npcId] neste instante, se for a folha da entrega. */
    fun heldItemAt(npcId: String, timeMs: Long): Item {
        val holder = snapshotAt(timeMs)?.objects?.firstOrNull()?.holder ?: return Item.NONE
        return when {
            npcId == RABBIT && holder == StoryObjectHolder.HELD_BY_RABBIT -> Item.DOCUMENT
            npcId == BULLDOG && holder == StoryObjectHolder.HELD_BY_BULLDOG -> Item.DOCUMENT
            else -> Item.NONE
        }
    }

    private fun rabbitMovement(d: OfficeDocumentDelivery, t: Long): NpcMovement {
        val desk = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_LEFT)
        val walkMs = d.arriveAt - d.walkOutAt
        val distance = maxOf(abs(d.meetX - desk.x), abs(d.meetY - desk.floorY))
        return when {
            t < d.pickupAt -> NpcMovement(desk.x, desk.floorY, false, NpcAnimation.STAND_UP, t - d.startAt,
                phase = PathPhase.STOP, facing = Facing.FRONT)
            t < d.walkOutAt -> NpcMovement(desk.x, desk.floorY, false, NpcAnimation.TURN_RIGHT, t - d.pickupAt,
                phase = PathPhase.TURN, facing = Facing.FRONT, heldItem = Item.DOCUMENT)
            t < d.arriveAt -> walk(desk.x, desk.floorY, d.meetX, d.meetY, t - d.walkOutAt, walkMs, distance, Item.DOCUMENT)
            t < d.returnAt -> NpcMovement(d.meetX, d.meetY, true, NpcAnimation.IDLE, t - d.arriveAt, phase = PathPhase.STOP,
                facing = Facing.SIDE, heldItem = if (t < d.handoffAt) Item.DOCUMENT else Item.NONE)
            t < d.deskArriveAt -> walk(d.meetX, d.meetY, desk.x, desk.floorY, t - d.returnAt, walkMs, distance, Item.NONE)
            t < d.sitAt -> NpcMovement(desk.x, desk.floorY, false, NpcAnimation.TURN_LEFT, t - d.deskArriveAt,
                phase = PathPhase.TURN, facing = Facing.FRONT)
            else -> NpcMovement(desk.x, desk.floorY, false, NpcAnimation.SIT, t - d.sitAt, phase = PathPhase.STOP,
                seated = true, facing = Facing.FRONT)
        }
    }

    private fun walk(fromX: Int, fromY: Int, toX: Int, toY: Int, elapsed: Long, durationMs: Long, distance: Int, item: Item): NpcMovement {
        val f = (elapsed.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        return NpcMovement((fromX + (toX - fromX) * f).toInt(), (fromY + (toY - fromY) * f).toInt(), toX >= fromX,
            NpcAnimation.WALK, elapsed.coerceAtLeast(0), distance * f, PathPhase.WALK, heldItem = item)
    }

    /** Parado no ponto dele: espera virado para o coelho, recebe e lê a folha. */
    private fun bulldogMovement(d: OfficeDocumentDelivery, t: Long): NpcMovement {
        val spot = OfficeNavigationGraph.spots.getValue(d.bulldogSpot)
        return if (t < d.handoffAt) NpcMovement(spot.x, spot.floorY, false, NpcAnimation.IDLE, t - d.arriveAt,
            phase = PathPhase.STOP, facing = Facing.SIDE)
        else NpcMovement(spot.x, spot.floorY, false, NpcAnimation.READ_DOCUMENT, t - d.handoffAt,
            phase = PathPhase.STOP, facing = Facing.SIDE, heldItem = Item.DOCUMENT)
    }

    private fun plan(cycle: Long): OfficeDocumentDelivery? {
        if (NpcDeterministicRandom.value("office-delivery", daySeed, cycle) >= CHANCE) return null
        val cycleStart = cycle * OfficeExecutiveTimeline.CYCLE_MS
        val visitStart = cycleStart + OfficeExecutiveTimeline.ENTRY_MS
        for ((spot, window) in windows()) {
            val bulldog = OfficeNavigationGraph.spots.getValue(spot)
            val desk = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_LEFT)
            val meetX = bulldog.x - HANDOFF_GAP_PX
            val meetY = bulldog.floorY
            val walkMs = maxOf(abs(meetX - desk.x), abs(meetY - desk.floorY)) * MS_PER_PX
            val arriveVisit = window.first + SETTLE_MS
            val handoffEndVisit = arriveVisit + HANDOFF_MS
            if (handoffEndVisit > window.last) continue
            if (SPEECH_WINDOWS.any { it.first < handoffEndVisit && arriveVisit < it.last }) continue
            val arriveAt = visitStart + arriveVisit
            val pickupAt = arriveAt - walkMs - NpcPoseLibrary.TURN_MS
            val startAt = pickupAt - STAND_MS
            val returnAt = arriveAt + HANDOFF_MS
            val deskArriveAt = returnAt + walkMs
            val sitAt = deskArriveAt + NpcPoseLibrary.TURN_MS
            val endsAt = sitAt + SIT_MS
            if (startAt <= cycleStart) continue
            // Primeiro as conversas da rotina até o fim da entrega: elas definem o relógio-base dos colegas.
            if (session.activeEventAt(endsAt) != null || session.activeEventAt(startAt) != null) continue
            if (!seatedThrough(RABBIT, OfficeNpcSpot.DESK_LEFT, startAt, endsAt)) continue
            if (!seatedThrough(CAT, OfficeNpcSpot.DESK_RIGHT, startAt, endsAt)) continue
            if (!canStart(SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY, startAt)) return null
            val readUntilVisit = minOf(window.last, SPEECH_WINDOWS.firstOrNull { it.first >= handoffEndVisit }?.first ?: window.last)
            val event = SceneStoryEvent(
                id = "office-doc:$daySeed:$cycle", type = SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY, scene = SceneId.OFFICE,
                participants = listOf(RABBIT, BULLDOG), requiredSpots = listOf(OfficeNpcSpot.DESK_LEFT.name, spot.name),
                startAt = startAt, durationMs = endsAt - startAt, priority = 2, cooldownMs = COOLDOWN_MS,
                interactAt = arriveAt, resolveAt = returnAt,
            )
            accepted += event
            return OfficeDocumentDelivery(
                event, cycle, "document:$daySeed:$cycle", cycleStart, meetX, meetY, spot,
                startAt, startAt + STAND_MS, pickupAt + NpcPoseLibrary.TURN_MS, arriveAt, arriveAt + HANDOFF_MS / 2,
                returnAt, deskArriveAt, sitAt, maxOf(returnAt, visitStart + readUntilVisit),
                cycleStart + OfficeExecutiveTimeline.EXIT_END_MS,
            )
        }
        return null
    }

    /** Coelho/gato sentado na própria mesa, na mesma ação, do começo ao fim da entrega. */
    private fun seatedThrough(npcId: String, desk: OfficeNpcSpot, from: Long, until: Long): Boolean {
        val brain = session.brain(npcId) ?: return false
        val baseFrom = session.baseTimeAt(npcId, from)
        val first = brain.baseActionAt(baseFrom)
        val last = brain.baseActionAt(session.baseTimeAt(npcId, until))
        // Mesma ação do começo ao fim, já sentado (a chegada à mesa inclui virar e sentar).
        if (first.id != last.id || first.destination != desk || baseFrom < first.arrivedAt || first.intent !in SEATED_INTENTS) return false
        // Confere no estado que de fato vai para a tela.
        return listOf(from, (from + until) / 2, until).all { t ->
            val state = session.stateAt(brain, t)
            state.currentSpot == desk && state.targetSpot == desk && state.currentIntent in SEATED_INTENTS
        }
    }

    /**
     * Onde o bulldog fica parado em cada visita. A visita repete a mesma rotina-base a cada ciclo,
     * então os pontos e janelas valem para todos os ciclos.
     */
    private fun windows(): List<Pair<OfficeNpcSpot, LongRange>> = visitWindows ?: run {
        val bulldog = session.brain(BULLDOG) ?: return emptyList()
        val visitEnd = OfficeExecutiveTimeline.RETURN_START_MS - OfficeExecutiveTimeline.ENTRY_MS
        val result = mutableListOf<Pair<OfficeNpcSpot, LongRange>>()
        var e = 0L
        while (e < visitEnd) {
            val action = bulldog.baseActionAt(e)
            val from = maxOf(action.arrivedAt, EARLIEST_VISIT_MS)
            val until = minOf(action.finishedAt, visitEnd - 1)
            if (action.destination in MEETING_SPOTS && until - from >= SETTLE_MS + HANDOFF_MS) result += action.destination to (from..until)
            e = maxOf(action.finishedAt, e + 1)
        }
        result.also { visitWindows = it }
    }

    companion object {
        const val RABBIT = "rabbit_analyst"
        const val CAT = "cat_colleague"
        const val BULLDOG = "bulldog_exec"
        const val CHANCE = 0.6f
        const val HANDOFF_MS = 1_600L
        const val SETTLE_MS = 300L
        const val STAND_MS = 640L
        const val SIT_MS = 640L
        const val MS_PER_PX = 24L
        /** Distância entre coelho e chefe na entrega: perto o bastante para o braço, sem sobrepor os corpos. */
        const val HANDOFF_GAP_PX = 44
        const val EARLIEST_VISIT_MS = 4_500L
        const val COOLDOWN_MS = 60_000L
        /** Pontos onde o coelho chega pela esquerda sem encostar nas mesas. */
        val MEETING_SPOTS = setOf(OfficeNpcSpot.CENTER_LEFT, OfficeNpcSpot.CENTER, OfficeNpcSpot.CENTER_RIGHT,
            OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.PRINTER)
        /** Falas do bulldog (tempo da visita); a entrega não cobre nenhuma delas. */
        val SPEECH_WINDOWS = listOf(2_500L..4_300L, 27_000L..28_800L)
        val SEATED_INTENTS = setOf(NpcIntent.WORK, NpcIntent.IDLE, NpcIntent.CHECK_PHONE)
    }
}
