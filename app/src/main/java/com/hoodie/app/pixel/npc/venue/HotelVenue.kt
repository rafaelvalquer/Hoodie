package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/** Estados da porta do elevador dos hóspedes (plano, seção 8). */
enum class ElevatorDoor { CLOSED, OPENING, OPEN, CLOSING }

/**
 * Porta do elevador planejada com os hóspedes: cada uso é um intervalo [aberta, fecha]. A porta
 * abre antes de alguém atravessar e só começa a fechar depois que a pessoa está toda dentro (ou
 * toda fora) — nunca fecha com alguém na soleira.
 */
object ElevatorController {
    const val OPEN_MS = 600L
    const val CLOSE_MS = 600L
    /** Espera depois de apertar o botão até a cabine chegar. */
    const val ARRIVE_MS = 2_400L
    /** Folga depois de passar pela soleira antes de começar a fechar. */
    const val CLEAR_MS = 400L
    const val NAME = "elevator"

    data class State(val door: ElevatorDoor, val openness: Float)

    fun stateAt(intervals: List<VenueInteractionCoordinator.Interval>, t: Long): State {
        for (i in intervals) {
            if (t < i.start - OPEN_MS || t > i.end + CLOSE_MS) continue
            return when {
                t < i.start -> State(ElevatorDoor.OPENING, (t - (i.start - OPEN_MS)).toFloat() / OPEN_MS)
                t <= i.end -> State(ElevatorDoor.OPEN, 1f)
                else -> State(ElevatorDoor.CLOSING, 1f - (t - i.end).toFloat() / CLOSE_MS)
            }
        }
        return State(ElevatorDoor.CLOSED, 0f)
    }
}

/**
 * Mala do hóspede: na mão (alça) enquanto anda; no chão, ao lado dele, durante o atendimento
 * no balcão; de volta à mão ao terminar o check-in.
 */
object LuggageAttachment {
    const val FLOOR_DX = 14

    /** Onde a mala está no chão em [t] (null = na mão ou longe). */
    fun floorPosition(s: VenueNpcSession, guest: String, t: Long): Pair<Int, Int>? {
        val last = s.coordinator.lastEvent(t, setOf(VenueEvent.SUITCASE_DOWN, VenueEvent.SUITCASE_PICKED), null)
            ?.takeIf { it.actorId == guest } ?: return null
        if (last.event != VenueEvent.SUITCASE_DOWN) return null
        // Deixada pelo visitante na frente do elevador (microevento) ou no chão do check-in.
        if (last.tag == ELEVATOR_TAG) return s.spot(HotelVenue.ELEV_FRONT).let { it.x - FLOOR_DX to it.floorY }
        val spot = s.spot(HotelVenue.DESK_FRONT)
        return spot.x + FLOOR_DX to spot.floorY
    }

    const val ELEVATOR_TAG = "elevator"
}

/** Objetos do hotel: porta do elevador dos hóspedes, mala no chão e a recepção. */
data class HotelAmbient(
    override val timeMs: Long,
    val elevator: ElevatorController.State,
    /** Mala apoiada no chão durante o check-in (x, y dos pés). */
    val suitcaseOnFloor: Pair<Int, Int>?,
    /** Recepcionista digitando no computador: a tela acende. */
    val computerBusy: Boolean,
    /** Alguém passando pela porta de entrada. */
    val doorOpen: Boolean,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.HOTEL

    companion object {
        fun idle(t: Long) = HotelAmbient(t, ElevatorController.State(ElevatorDoor.CLOSED, 0f), null, false, false)
    }
}

/**
 * Hotel (Sprint 6): saguão e recepção, sem quartos. Recepcionista (atende, consulta o computador,
 * entrega o cartão, organiza o balcão), hóspede (chega com a mala, espera, faz check-in e sobe
 * pelo elevador — que funciona como entrada e saída da cena) e visitante (senta, espera alguém,
 * às vezes pergunta algo na recepção). [HotelCheckInCoordinator] = fila + um atendimento por vez.
 */
internal object HotelVenue : VenueBlueprint(SceneId.HOTEL) {
    const val SERVICE = "recepcao"
    const val CHECK_IN = "check_in"
    const val QUESTION = "question"

    const val DOOR = "door"
    const val ENTRY = "entry"
    const val MID = "mid"
    const val DESK_FRONT = "desk_front"
    const val QUEUE = "queue"
    const val ELEV_FRONT = "elevator_front"
    const val ELEV_IN = "elevator_in"
    const val SOFA_NPC = "sofa_npc"
    const val ARMCHAIR = "armchair"
    const val R_DESK = "r_desk"
    const val R_KEYS = "r_keys"
    const val R_COMPUTER = "r_computer"
    const val HELP_SPOT = "luggage_help"
    const val HELP_X = 74
    const val HELP_Y = 238

    /** Pés da recepcionista atrás do balcão (o tampo em y 176 a mostra da cintura para cima). */
    const val CLERK_Y = 198
    const val DESK_FRONT_Y = 204
    const val DESK_TOP = 176

    /** Pontos do Hoodie (HotelScene). */
    const val HOODIE_RECEPTION_X = 88
    const val HOODIE_RECEPTION_Y = 224
    const val HOODIE_SOFA_X = 44
    const val SOFA_Y = 282
    const val HOODIE_ELEVATOR_X = 170
    const val ELEVATOR_Y = 162
    const val NPC_ELEVATOR_X = 214

    private val graph = VenueNavigationGraph(
        "hotel",
        spots = listOf(
            VenueSpot(DOOR, 130, 160, capacity = 4, offstage = true),
            VenueSpot(ENTRY, 130, 196, capacity = 2),
            VenueSpot(MID, 130, 228, capacity = 3),
            VenueSpot(DESK_FRONT, 40, 222, interaction = "balcão da recepção"),
            VenueSpot(QUEUE, 40, 254, interaction = "fila"),
            VenueSpot(ELEV_FRONT, NPC_ELEVATOR_X, 200, interaction = "botão do elevador"),
            VenueSpot(ELEV_IN, NPC_ELEVATOR_X, 160, capacity = 2, interaction = "elevador"),
            VenueSpot(SOFA_NPC, 80, SOFA_Y, interaction = "sofá"),
            VenueSpot(ARMCHAIR, 196, 284, interaction = "poltrona"),
            VenueSpot(R_DESK, 44, CLERK_Y, interaction = "recepção por dentro"),
            VenueSpot(R_KEYS, 20, CLERK_Y, interaction = "quadro de chaves"),
            VenueSpot(R_COMPUTER, 84, CLERK_Y, interaction = "computador"),
            // Ao lado do balcão: onde o visitante recebe a mala do hóspede (HOTEL_LUGGAGE_HELP).
            VenueSpot(HELP_SPOT, HELP_X, HELP_Y, interaction = "ajuda com a mala"),
        ),
        edges = listOf(
            DOOR to ENTRY, ENTRY to MID, MID to DESK_FRONT, DESK_FRONT to QUEUE, MID to QUEUE, MID to HELP_SPOT,
            MID to ELEV_FRONT, ELEV_FRONT to ELEV_IN, MID to SOFA_NPC, MID to ARMCHAIR,
            R_KEYS to R_DESK, R_DESK to R_COMPUTER,
        ),
        obstacles = listOf(
            VenueObstacle("parede", 0, 0, 239, 150),
            VenueObstacle("balcão da recepção", 8, DESK_FRONT_Y, 110, 214),
            VenueObstacle("sofá", 20, 288, 100, 300),
            VenueObstacle("poltrona", 184, 290, 208, 300),
            VenueObstacle("mesa de centro", 112, 270, 150, 288),
        ),
    )

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        val receptionist = VenueActorSpec(
            NpcCharacterRegistry.CAT_COLLEAGUE,
            VenueNpcProfile("receptionist", VenueRole.RECEPTIONIST, mapOf(VenueAction.CLEAN to 25, VenueAction.PHONE to 15, VenueAction.WAIT to 60), 0f, 1_800, 3_600),
            slotSeed = 701, start = VenueStart.POST, startSpot = R_DESK,
        )
        val guest = VenueActorSpec(
            NpcCharacterRegistry.RACCOON_COMMUTER,
            VenueNpcProfile("hotel_guest", VenueRole.HOTEL_GUEST, mapOf(VenueAction.CHECK_IN to 100), 0f, 2_000, 4_000),
            slotSeed = 709, start = VenueStart.OFFSTAGE, startDelayMs = 1_500L + Math.floorMod(key.daySeed, 4) * 500L,
        )
        val visitor = VenueActorSpec(
            NpcCharacterRegistry.DOG_WORKER,
            VenueNpcProfile("hotel_visitor", VenueRole.HOTEL_VISITOR, mapOf(VenueAction.WAIT to 45, VenueAction.PHONE to 30, VenueAction.ORDER to 25), 0.2f, 5_000, 9_000),
            slotSeed = 719, start = VenueStart.OFFSTAGE, startDelayMs = 9_000L + Math.floorMod(key.daySeed, 5) * 800L,
        )
        return if (key.night) listOf(receptionist, guest) else listOf(receptionist, guest, visitor)
    }

    override fun initialize(s: VenueNpcSession, c: VenueCursor) {
        super.initialize(s, c)
        if (c.role == VenueRole.RECEPTIONIST) c.front = true
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        when (c.role) {
            VenueRole.RECEPTIONIST -> receptionist(s, c)
            VenueRole.HOTEL_GUEST -> guest(s, c)
            else -> visitor(s, c)
        }
        c.decisions++
    }

    // ───── Recepcionista ─────

    private fun receptionist(s: VenueNpcSession, c: VenueCursor) {
        s.coordinator.nextPending(SERVICE, c.time)?.let { order ->
            val checkIn = order.product == CHECK_IN
            VenueScripts.serveOrder(s, c, order, R_DESK, VenueScripts.line(if (checkIn) WELCOME else HELP, c), if (checkIn) VenueEvent.KEYCARD_TAKEN else null) {
                // Consulta o computador; no check-in, pega o cartão no quadro de chaves.
                s.walkTo(c, R_COMPUTER, VenueNpcState.NAVIGATING, VenueAction.PREPARE)
                s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE)
                s.stay(c, if (checkIn) 2_000 else 1_300, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.PREPARE, Facing.FRONT)
                if (checkIn) {
                    s.walkTo(c, R_KEYS, VenueNpcState.NAVIGATING, VenueAction.PREPARE)
                    s.turnSide(c, false, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE)
                    s.stay(c, 700, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.STAND_PICK_PRODUCT)
                    c.held = Item.KEYCARD
                }
                R_DESK
            }
            return
        }
        val calm = VenueScripts.calmFor(s, c, SERVICE)
        when (if (calm > 6_000) s.choose(c, setOf(VenueAction.CLEAN, VenueAction.PHONE, VenueAction.WAIT)) else VenueAction.WAIT) {
            VenueAction.CLEAN -> {
                s.walkTo(c, R_COMPUTER, VenueNpcState.NAVIGATING, VenueAction.CLEAN)
                s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN)
                s.stay(c, 2_400, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN, NpcAnimation.WIPE, Facing.FRONT)
                c.remember(VenueAction.CLEAN)
            }
            VenueAction.PHONE -> {
                // Atende o telefone da recepção.
                s.walkTo(c, R_DESK, VenueNpcState.NAVIGATING, VenueAction.PHONE)
                s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.PHONE)
                s.stay(c, 2_800, VenueNpcState.PERFORMING_ACTION, VenueAction.PHONE, NpcAnimation.STAND_PHONE)
                c.remember(VenueAction.PHONE)
            }
            else -> VenueScripts.waitAtPost(s, c, R_DESK, calm)
        }
    }

    // ───── Hóspede ─────

    private object Phase {
        const val ARRIVED = 1
        const val QUEUE = 2
        const val WAITING_KEY = 3
        const val TO_ELEVATOR = 4
        const val UPSTAIRS = 5
        const val DOWNSTAIRS = 6
    }

    private fun guest(s: VenueNpcSession, c: VenueCursor) {
        if (c.offstage) {
            // Chega da rua com a mala (já na mão desde o primeiro passo pela porta).
            c.held = Item.SUITCASE
            s.enter(c, DOOR, ENTRY, VenueAction.CHECK_IN)
            c.phase = Phase.QUEUE; c.orderId = -1; c.target = null
            return
        }
        when (c.phase) {
            Phase.QUEUE -> {
                val free = !VenueScripts.counterBusy(s, c, SERVICE) &&
                    s.isFree(c, DESK_FRONT, c.time, c.time + s.estimateRouteMs(c, DESK_FRONT) + 4_000)
                if (!free) {
                    // Balcão ocupado: espera na fila, mala na mão.
                    if (c.node != QUEUE && s.isFree(c, QUEUE, c.time, c.time + 4_000)) {
                        s.endWait(c)
                        s.reserveFor(c, QUEUE, 3_000)
                        s.walkTo(c, QUEUE, VenueNpcState.NAVIGATING, VenueAction.WAIT)
                    }
                    s.waitSlice(c, VenueAction.WAIT, NpcAnimation.CHECKOUT_WAIT)
                    return
                }
                s.endWait(c)
                s.reserveFor(c, DESK_FRONT, 4_000)
                s.walkTo(c, DESK_FRONT, VenueNpcState.NAVIGATING, VenueAction.CHECK_IN)
                s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.CHECK_IN)
                // A mala desce para o chão ao lado dele antes de falar com a recepção.
                s.stay(c, 500, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
                c.held = Item.NONE
                s.coordinator.emit(c.time, VenueEvent.SUITCASE_DOWN, c.npcId)
                s.say(c, VenueScripts.line(GUEST_LINES, c), VenueScripts.ORDER_MS, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, Facing.FRONT)
                val order = s.coordinator.placeOrder(SERVICE, c.npcId, c.time, CHECK_IN)
                s.coordinator.emit(c.time, VenueEvent.ORDER_PLACED, c.npcId, CHECK_IN)
                c.orderId = order.id
                c.phase = Phase.WAITING_KEY
            }
            Phase.WAITING_KEY -> if (VenueScripts.collect(s, c, DESK_FRONT, Item.KEYCARD, VenueEvent.KEYCARD_TAKEN)) {
                // Cartão no bolso, mala de volta na mão.
                s.stay(c, 600, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
                c.held = Item.SUITCASE
                s.coordinator.emit(c.time, VenueEvent.SUITCASE_PICKED, c.npcId)
                s.say(c, VenueScripts.line(THANKS, c), 1_300, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, Facing.FRONT)
                c.phase = Phase.TO_ELEVATOR
            }
            Phase.TO_ELEVATOR -> if (!luggageHelp(s, c)) rideUp(s, c)
            Phase.UPSTAIRS -> rideDown(s, c)
            else -> {
                s.leave(c, DOOR, 30_000 + (s.rand(c, 41) * 40_000).toLong())
            }
        }
    }

    /** Aperta o botão, espera a cabine, a porta abre, entra (some) e a porta fecha depois dele. */
    private fun rideUp(s: VenueNpcSession, c: VenueCursor) {
        s.walkTo(c, ELEV_FRONT, VenueNpcState.NAVIGATING, VenueAction.ELEVATOR)
        s.turnIntoFront(c, VenueNpcState.WAITING, VenueAction.ELEVATOR)
        s.stay(c, 400, VenueNpcState.PERFORMING_ACTION, VenueAction.ELEVATOR, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
        s.coordinator.emit(c.time, VenueEvent.ELEVATOR_CALLED, c.npcId, "up")
        val open = c.time + ElevatorController.ARRIVE_MS
        s.stay(c, open - c.time, VenueNpcState.WAITING, VenueAction.ELEVATOR, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT)
        s.walkTo(c, ELEV_IN, VenueNpcState.NAVIGATING, VenueAction.ELEVATOR)
        val inside = c.time
        s.coordinator.addInterval(ElevatorController.NAME, open, inside + ElevatorController.CLEAR_MS)
        // Lá dentro: sobe e fica no quarto um bom tempo (fora de cena, atrás da porta fechada).
        s.stay(c, 45_000 + (s.rand(c, 7) * 60_000).toLong(), VenueNpcState.OFFSTAGE, VenueAction.ELEVATOR, NpcAnimation.IDLE, hidden = true)
        c.held = Item.NONE
        c.phase = Phase.UPSTAIRS
    }

    /** A cabine chega, a porta abre, ele sai sem a mala; a porta fecha depois que ele saiu. */
    private fun rideDown(s: VenueNpcSession, c: VenueCursor) {
        val open = c.time + ElevatorController.OPEN_MS
        s.stay(c, ElevatorController.OPEN_MS, VenueNpcState.OFFSTAGE, VenueAction.ELEVATOR, NpcAnimation.IDLE, hidden = true)
        s.walkTo(c, ELEV_FRONT, VenueNpcState.NAVIGATING, VenueAction.ELEVATOR)
        s.coordinator.addInterval(ElevatorController.NAME, open, c.time + ElevatorController.CLEAR_MS)
        s.stay(c, 900, VenueNpcState.PERFORMING_ACTION, VenueAction.ELEVATOR, NpcAnimation.LOOK)
        c.phase = Phase.DOWNSTAIRS
    }

    // ───── Visitante ─────

    private fun visitor(s: VenueNpcSession, c: VenueCursor) {
        if (c.offstage) {
            s.enter(c, DOOR, ENTRY, VenueAction.WAIT)
            c.phase = 1; c.visits = 0; c.orderId = -1
            return
        }
        if (c.phase == 1 && helpGuest(s, c)) return
        when (c.phase) {
            Phase.QUEUE -> { if (VenueScripts.queueAndOrder(s, c, SERVICE, DESK_FRONT, listOf(QUEUE), QUESTION, VenueScripts.line(VISITOR_LINES, c))) c.phase = Phase.WAITING_KEY; return }
            Phase.WAITING_KEY -> { if (VenueScripts.collect(s, c, DESK_FRONT, Item.NONE, VenueEvent.ORDER_TAKEN)) c.phase = 1; return }
        }
        if (c.visits >= 4) { s.leave(c, DOOR, 25_000 + (s.rand(c, 43) * 35_000).toLong()); return }
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val available = buildSet {
            add(VenueAction.WAIT); add(VenueAction.PHONE)
            if (!VenueScripts.counterBusy(s, c, SERVICE) && c.visits >= 1) add(VenueAction.ORDER)
        }
        val action = s.choose(c, available) ?: VenueAction.WAIT
        c.remember(action); c.visits++
        if (action == VenueAction.ORDER) { c.phase = Phase.QUEUE; return }
        val seat = c.node?.takeIf { it == SOFA_NPC || it == ARMCHAIR } ?: s.chooseFree(c, listOf(SOFA_NPC, ARMCHAIR), hold + 2_000)
        if (seat == null) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.LOOK); return }
        s.endWait(c)
        if (c.node != seat) {
            s.reserveFor(c, seat, hold + 2_000)
            s.walkTo(c, seat, VenueNpcState.NAVIGATING, action)
            s.sit(c, action)
        }
        // Espera alguém: olha a porta e o elevador, ou o celular.
        s.stay(c, hold, VenueNpcState.WAITING, action, if (action == VenueAction.PHONE) NpcAnimation.SIT_PHONE else NpcAnimation.SIT_LOOK, Facing.FRONT)
    }

    // ───── Microevento: ajuda com a mala (HOTEL_LUGGAGE_HELP) ─────

    private fun helpId(s: VenueNpcSession, requestAt: Long) = "hotel-luggage:${s.seed}:$requestAt"
    private fun helpTag(guest: String, requestAt: Long) = "$guest:$requestAt"

    /**
     * Hóspede com a mala depois do check-in: às vezes pede ajuda e espera um pouco. Se o visitante
     * aceitar, entrega a mala, espera ele deixá-la na frente do elevador e sair do caminho, e só
     * então vai até lá, pega a mala e sobe. Ninguém aceitou: sobe sozinho. true = planejou algo.
     */
    private fun luggageHelp(s: VenueNpcSession, c: VenueCursor): Boolean {
        val requestAt = c.target?.removePrefix("help:")?.toLongOrNull()
        if (requestAt == null) {
            if (c.target == "help:done") { c.target = null; return false }
            val visitor = s.allCursors().firstOrNull { it.role == VenueRole.HOTEL_VISITOR } ?: return false
            if (visitor.offstage || visitor.phase != 1 || (visitor.node != SOFA_NPC && visitor.node != ARMCHAIR)) return false
            if (s.rand(c, 83) >= HELP_CHANCE) return false
            if (!s.stories.canStart(com.hoodie.app.pixel.npc.events.SceneStoryEventType.HOTEL_LUGGAGE_HELP, c.time, listOf(c.npcId, visitor.npcId))) return false
            s.coordinator.emit(c.time, VenueEvent.HELP_REQUESTED, c.npcId)
            c.target = "help:${c.time}"
            s.waitSlice(c, VenueAction.CHECK_IN, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT)
            return true
        }
        val accepted = s.coordinator.events().any { it.event == VenueEvent.HELP_ACCEPTED && it.tag == helpTag(c.npcId, requestAt) }
        val record = s.stories.find(helpId(s, requestAt))
        if (!accepted || record == null) {
            if (c.time - requestAt > HELP_WAIT_MS) { c.target = "help:done"; s.endWait(c); return false }
            s.waitSlice(c, VenueAction.CHECK_IN, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT)
            return true
        }
        s.endWait(c)
        val handAt = record.event.interactAt
        val half = HAND_MS / 2
        if (c.time < handAt - half) s.stay(c, handAt - half - c.time, VenueNpcState.WAITING, VenueAction.CHECK_IN, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT)
        s.stay(c, half, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, NpcAnimation.SERVE, Facing.FRONT)
        c.held = Item.NONE
        s.stay(c, half, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, NpcAnimation.SERVE, Facing.FRONT, localOffsetMs = half)
        if (s.coordinator.speechFree(c.time, c.time + 1_300)) s.say(c, VenueScripts.line(HELP_THANKS, c), 1_300, VenueNpcState.INTERACTING, VenueAction.CHECK_IN, Facing.FRONT)
        val clear = record.event.resolveAt
        if (c.time < clear) s.stay(c, clear - c.time, VenueNpcState.WAITING, VenueAction.CHECK_IN, NpcAnimation.LOOK, Facing.FRONT)
        s.walkTo(c, ELEV_FRONT, VenueNpcState.NAVIGATING, VenueAction.ELEVATOR)
        s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.ELEVATOR)
        s.stay(c, 500, VenueNpcState.PERFORMING_ACTION, VenueAction.ELEVATOR, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
        c.held = Item.SUITCASE
        s.coordinator.emit(c.time, VenueEvent.SUITCASE_PICKED, c.npcId, LuggageAttachment.ELEVATOR_TAG)
        val suitcase = record.objects.first()
        s.stories.record(record.copy(
            event = record.event.copy(durationMs = c.time - record.event.startAt),
            objects = listOf(suitcase.copy(steps = suitcase.steps + com.hoodie.app.pixel.npc.events.StoryObjectStep(c.time, com.hoodie.app.pixel.npc.events.StoryObjectHolder.HELD_BY_OWNER))),
        ))
        c.target = null
        rideUp(s, c)
        return true
    }

    /** Visitante sentado vê o pedido de ajuda: leva a mala até a frente do elevador e volta. */
    private fun helpGuest(s: VenueNpcSession, c: VenueCursor): Boolean {
        val request = s.coordinator.events().lastOrNull { it.event == VenueEvent.HELP_REQUESTED && it.at <= c.time } ?: return false
        if (c.time - request.at > HELP_ACCEPT_MS) return false
        val tag = helpTag(request.actorId, request.at)
        if (s.coordinator.events().any { it.event == VenueEvent.HELP_ACCEPTED && it.tag == tag }) return false
        val guest = s.cursor(request.actorId) ?: return false
        val horizon = c.time + s.estimateRouteMs(c, HELP_SPOT) + 8_000
        if (!s.isFree(c, HELP_SPOT, c.time, horizon) || !s.isFree(c, ELEV_FRONT, c.time, horizon + 4_000)) return false
        val start = c.time
        s.coordinator.emit(start, VenueEvent.HELP_ACCEPTED, c.npcId, tag)
        s.reserveFor(c, HELP_SPOT, 4_000)
        s.walkTo(c, HELP_SPOT, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
        s.turnSide(c, false, VenueNpcState.INTERACTING, VenueAction.SOCIAL)
        s.say(c, VenueScripts.line(HELP_OFFERS, c), 1_300, VenueNpcState.INTERACTING, VenueAction.SOCIAL)
        val half = HAND_MS / 2
        val handAt = c.time + half
        s.stay(c, half, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.STAND_PICK_PRODUCT)
        c.held = Item.SUITCASE
        s.stay(c, half, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.STAND_PICK_PRODUCT, localOffsetMs = half)
        s.reserveFor(c, ELEV_FRONT, 2_000)
        s.walkTo(c, ELEV_FRONT, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
        s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.SOCIAL)
        s.stay(c, 500, VenueNpcState.PERFORMING_ACTION, VenueAction.SOCIAL, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
        c.held = Item.NONE
        val downAt = c.time
        s.coordinator.emit(downAt, VenueEvent.SUITCASE_DOWN, guest.npcId, LuggageAttachment.ELEVATOR_TAG)
        s.walkTo(c, MID, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
        val clearAt = c.time + 400
        s.stories.record(VenueStoryRecord(
            com.hoodie.app.pixel.npc.events.SceneStoryEvent(
                id = helpId(s, request.at), type = com.hoodie.app.pixel.npc.events.SceneStoryEventType.HOTEL_LUGGAGE_HELP, scene = SceneId.HOTEL,
                participants = listOf(guest.npcId, c.npcId), requiredSpots = listOf(HELP_SPOT, ELEV_FRONT),
                startAt = start, durationMs = clearAt - start + 6_000, priority = 2, cooldownMs = HELP_COOLDOWN_MS,
                interactAt = handAt, resolveAt = clearAt,
            ),
            listOf(com.hoodie.app.pixel.npc.events.StoryObjectTimeline("suitcase:${request.at}", Item.SUITCASE, listOf(
                com.hoodie.app.pixel.npc.events.StoryObjectStep(start, com.hoodie.app.pixel.npc.events.StoryObjectHolder.HELD_BY_OWNER),
                com.hoodie.app.pixel.npc.events.StoryObjectStep(handAt, com.hoodie.app.pixel.npc.events.StoryObjectHolder.HELD_BY_HELPER),
                com.hoodie.app.pixel.npc.events.StoryObjectStep(downAt, com.hoodie.app.pixel.npc.events.StoryObjectHolder.ON_FLOOR),
            ))),
        ))
        c.remember(VenueAction.SOCIAL)
        c.visits++
        return true
    }

    private const val HELP_CHANCE = 0.5f
    private const val HELP_WAIT_MS = 3_600L
    private const val HELP_ACCEPT_MS = 3_000L
    private const val HAND_MS = 800L
    private const val HELP_COOLDOWN_MS = 120_000L
    private val HELP_OFFERS = listOf("Deixa que eu levo!", "Eu ajudo com a mala!")
    private val HELP_THANKS = listOf("Muito obrigado!", "Que gentileza!")

    override fun ambient(s: VenueNpcSession, t: Long): HotelAmbient {
        var computer = false; var door = false
        s.allCursors().forEach { c ->
            val seg = s.segmentAt(c.index, t) ?: return@forEach
            if (c.role == VenueRole.RECEPTIONIST && seg.animation == NpcAnimation.PREPARE) computer = true
            if (!seg.offstage && (seg.fromNode == DOOR || seg.toNode == DOOR)) door = true
        }
        return HotelAmbient(
            t, ElevatorController.stateAt(s.coordinator.intervals(ElevatorController.NAME), t),
            LuggageAttachment.floorPosition(s, "hotel_guest", t), computer, door,
        )
    }

    private val WELCOME = listOf("Bem vindo", "Boa noite, seja bem vindo", "Um momento")
    private val HELP = listOf("Vou verificar", "Pois nao", "Um instante")
    private val GUEST_LINES = listOf("Tenho uma reserva", "Check in, por favor")
    private val THANKS = listOf("Obrigado", "Perfeito, valeu")
    private val VISITOR_LINES = listOf("Pode avisar o quarto", "Estou esperando alguem")
}
