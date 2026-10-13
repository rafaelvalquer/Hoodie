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
    private var chatWindowsCache: List<Pair<OfficeNpcSpot, LongRange>>? = null
    @Volatile private var disposed = false

    private val chats = ArrayList<OfficeCoffeeChat?>()

    override fun canStart(type: SceneStoryEventType, at: Long): Boolean =
        type in setOf(SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY, SceneStoryEventType.OFFICE_COFFEE_CHAT) && !disposed &&
            SceneEventDirector.budgetAllows(accepted, at) && SceneEventDirector.cooldownAllows(accepted, type, at)

    @Synchronized override fun dispose() {
        disposed = true
        planned.clear(); chats.clear(); accepted.clear(); visitWindows = null
    }

    /** Ciclos são planejados em ordem: o orçamento de eventos olha os anteriores. */
    private fun ensure(cycle: Long) {
        while (planned.size <= cycle) {
            val k = planned.size.toLong()
            val delivery = plan(k)
            planned += delivery
            // Visita sem entrega: às vezes o chefe para com o café e puxa conversa com o Hoodie.
            chats += if (delivery == null) planChat(k) else null
        }
    }

    /** Entrega do ciclo de visita que contém [timeMs] (null = não há entrega nesse ciclo). */
    @Synchronized fun deliveryAt(timeMs: Long): OfficeDocumentDelivery? {
        if (disposed || timeMs < 0) return null
        val cycle = timeMs / OfficeExecutiveTimeline.CYCLE_MS
        ensure(cycle)
        return planned[cycle.toInt()]
    }

    /** Conversa do café do ciclo que contém [timeMs] (null = não há). */
    @Synchronized fun chatAt(timeMs: Long): OfficeCoffeeChat? {
        if (disposed || timeMs < 0) return null
        val cycle = timeMs / OfficeExecutiveTimeline.CYCLE_MS
        ensure(cycle)
        return chats[cycle.toInt()]
    }

    /** Fala do microevento (o chefe falando com o Hoodie). */
    fun speechAt(npcId: String, timeMs: Long): OfficeSpeechEvent? {
        if (npcId != BULLDOG) return null
        val chat = chatAt(timeMs) ?: return null
        return if (timeMs in chat.talkAt until chat.talkEndsAt) OfficeSpeechEvent(BULLDOG, chat.line, chat.talkAt, chat.talkEndsAt - chat.talkAt, HOODIE) else null
    }

    override fun snapshotAt(timeMs: Long): SceneStorySnapshot? {
        chatAt(timeMs)?.takeIf { timeMs in it.event.startAt until it.event.endsAt }?.let { chat ->
            return SceneStorySnapshot(chat.event, SceneEventDirector.phaseAt(chat.event, timeMs),
                listOf(StoryObjectState("mug:${chat.event.id}", StoryObjectHolder.HELD_BY_OWNER, Item.MUG)))
        }
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
        if (npcId == BULLDOG) chatAt(timeMs)?.let { chat -> chatMovement(chat, timeMs)?.let { return it } }
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

    /**
     * OFFICE_COFFEE_CHAT: parado num ponto, com a caneca na mão, o chefe vira de frente para a mesa
     * do Hoodie, puxa conversa e volta ao que fazia. Nunca por cima das falas dele da visita.
     */
    private fun planChat(cycle: Long): OfficeCoffeeChat? {
        if (NpcDeterministicRandom.value("office-coffee-chat", daySeed, cycle) >= CHAT_CHANCE) return null
        val visitStart = cycle * OfficeExecutiveTimeline.CYCLE_MS + OfficeExecutiveTimeline.ENTRY_MS
        for ((spot, window) in chatWindows()) {
            val start = window.first + SETTLE_MS
            val end = start + CHAT_MS
            if (end > window.last) continue
            if (SPEECH_WINDOWS.any { it.first < end && start < it.last }) continue
            val at = visitStart + start
            if (!canStart(SceneStoryEventType.OFFICE_COFFEE_CHAT, at)) return null
            val event = SceneStoryEvent(
                id = "office-chat:$daySeed:$cycle", type = SceneStoryEventType.OFFICE_COFFEE_CHAT, scene = SceneId.OFFICE,
                participants = listOf(BULLDOG, HOODIE), requiredSpots = listOf(spot.name),
                startAt = at, durationMs = CHAT_MS, priority = 1, cooldownMs = COOLDOWN_MS,
                interactAt = at + NpcPoseLibrary.TURN_MS, resolveAt = at + CHAT_MS - NpcPoseLibrary.TURN_MS,
            )
            accepted += event
            val talkAt = at + NpcPoseLibrary.TURN_MS + CHAT_SIP_MS
            return OfficeCoffeeChat(event, spot, talkAt, talkAt + CHAT_TALK_MS, CHAT_LINES[Math.floorMod(cycle.toInt() + daySeed, CHAT_LINES.size)])
        }
        return null
    }

    /** Janelas paradas do chefe em qualquer ponto livre (inclusive o do café), exceto mesas e porta. */
    private fun chatWindows(): List<Pair<OfficeNpcSpot, LongRange>> {
        windows()
        return chatWindowsCache ?: emptyList()
    }

    private fun chatMovement(chat: OfficeCoffeeChat, t: Long): NpcMovement? {
        val e = chat.event
        if (t !in e.startAt until e.endsAt) return null
        val p = OfficeNavigationGraph.spots.getValue(chat.spot)
        val local = t - e.startAt
        val back = e.endsAt - NpcPoseLibrary.TURN_MS
        return when {
            local < NpcPoseLibrary.TURN_MS -> NpcMovement(p.x, p.floorY, false, NpcAnimation.TURN_LEFT, local,
                phase = PathPhase.TURN, facing = Facing.FRONT, heldItem = Item.MUG)
            t in chat.talkAt until chat.talkEndsAt -> NpcMovement(p.x, p.floorY, false, NpcAnimation.TALK, t - chat.talkAt,
                phase = PathPhase.STOP, facing = Facing.FRONT, heldItem = Item.MUG)
            t < back -> NpcMovement(p.x, p.floorY, false, NpcAnimation.IDLE, local, phase = PathPhase.STOP,
                facing = Facing.FRONT, heldItem = Item.MUG)
            else -> NpcMovement(p.x, p.floorY, false, NpcAnimation.TURN_RIGHT, t - back, phase = PathPhase.TURN,
                facing = Facing.FRONT, heldItem = Item.MUG)
        }
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
        val chat = mutableListOf<Pair<OfficeNpcSpot, LongRange>>()
        var e = 0L
        while (e < visitEnd) {
            val action = bulldog.baseActionAt(e)
            val from = maxOf(action.arrivedAt, EARLIEST_VISIT_MS)
            val until = minOf(action.finishedAt, visitEnd - 1)
            if (action.destination in MEETING_SPOTS && until - from >= SETTLE_MS + HANDOFF_MS) result += action.destination to (from..until)
            if (action.destination in CHAT_SPOTS && until - from >= SETTLE_MS + CHAT_MS) chat += action.destination to (from..until)
            e = maxOf(action.finishedAt, e + 1)
        }
        // O café primeiro: é onde a conversa faz mais sentido.
        chatWindowsCache = chat.sortedBy { if (it.first == OfficeNpcSpot.COFFEE) 0 else 1 }
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

        const val HOODIE = "hoodie"
        const val CHAT_CHANCE = 0.5f
        const val CHAT_SIP_MS = 700L
        const val CHAT_TALK_MS = 1_900L
        val CHAT_MS: Long = 2 * NpcPoseLibrary.TURN_MS + CHAT_SIP_MS + CHAT_TALK_MS + 800L
        val CHAT_SPOTS = setOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.WINDOW, OfficeNpcSpot.CENTER_LEFT, OfficeNpcSpot.CENTER,
            OfficeNpcSpot.CENTER_RIGHT, OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.PRINTER)
        val CHAT_LINES = listOf("E aí, Hoodie! Um café?", "Bom trabalho por aí!", "Esse café está ótimo.")
    }
}

/** Conversa do café: o chefe, de frente para a mesa do Hoodie, com a caneca na mão. */
data class OfficeCoffeeChat(
    val event: SceneStoryEvent,
    val spot: OfficeNpcSpot,
    val talkAt: Long,
    val talkEndsAt: Long,
    val line: String,
)
