package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcReaction
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/** Fases da sessão (plano, seção 6): chegada → luzes baixando → filme → luzes acendendo → saída. */
enum class CinemaPhase { LOBBY, DIMMING, SCREENING, EXIT_LIGHTS }

/**
 * Programação e iluminação da sala, em função do tempo da cena (deterministas). Cada sessão dura
 * [CYCLE_MS]; os "momentos do filme" (riso, susto) caem em horários fixos por sessão, e cada
 * espectador reage com o seu atraso — nunca todos no mesmo instante.
 */
object CinemaLightingController {
    const val CYCLE_MS = 300_000L
    const val DIM_AT = 70_000L
    const val SCREEN_AT = 76_000L
    const val LIGHTS_AT = 240_000L
    const val LOBBY_AT = 250_000L
    /** Luz mínima da sala no filme: os personagens continuam legíveis. */
    const val SCREENING_LIGHT = 0.6f

    fun cycleStart(t: Long) = Math.floorDiv(t, CYCLE_MS) * CYCLE_MS

    fun phaseAt(t: Long): CinemaPhase {
        val k = Math.floorMod(t, CYCLE_MS)
        return when {
            k < DIM_AT -> CinemaPhase.LOBBY
            k < SCREEN_AT -> CinemaPhase.DIMMING
            k < LIGHTS_AT -> CinemaPhase.SCREENING
            k < LOBBY_AT -> CinemaPhase.EXIT_LIGHTS
            else -> CinemaPhase.LOBBY
        }
    }

    /** Intensidade da luz da sala (1 = acesa). Muda suave, em rampas. */
    fun lightAt(t: Long): Float {
        val k = Math.floorMod(t, CYCLE_MS)
        return when (phaseAt(t)) {
            CinemaPhase.LOBBY -> 1f
            CinemaPhase.DIMMING -> 1f - (1f - SCREENING_LIGHT) * (k - DIM_AT) / (SCREEN_AT - DIM_AT).toFloat()
            CinemaPhase.SCREENING -> SCREENING_LIGHT
            CinemaPhase.EXIT_LIGHTS -> SCREENING_LIGHT + (1f - SCREENING_LIGHT) * (k - LIGHTS_AT) / (LOBBY_AT - LIGHTS_AT).toFloat()
        }
    }

    /** Momentos do filme da sessão que começa em [cycleStart]: (instante, tipo). */
    fun beats(cycleStart: Long, seed: Int): List<Pair<Long, NpcReaction>> = BEAT_OFFSETS.mapIndexed { i, base ->
        val jitter = (NpcDeterministicRandom.value("film", seed, cycleStart / CYCLE_MS * 7 + i) * 6_000).toLong()
        cycleStart + base + jitter to if ((i + cycleStart / CYCLE_MS).toInt() % 2 == 0) NpcReaction.SMILE else NpcReaction.SURPRISED
    }

    /** Cor abstrata da "tela" que banha a plateia (muda a cada cena do filme). */
    fun screenColorAt(t: Long): Int = SCREEN_COLORS[Math.floorMod(t / 1_700, SCREEN_COLORS.size.toLong()).toInt()]

    private val BEAT_OFFSETS = longArrayOf(98_000L, 132_000L, 171_000L, 208_000L)
    private val SCREEN_COLORS = intArrayOf(0xFF9FC4FF.toInt(), 0xFFFFD9A0.toInt(), 0xFFB8F0C8.toInt(), 0xFFF2B5E0.toInt(), 0xFFFFFFFF.toInt())
}

/** O que o cinema mostra num instante. */
data class CinemaAmbient(
    override val timeMs: Long,
    val phase: CinemaPhase,
    val light: Float,
    val screenColor: Int,
    /** Atendente enchendo um balde: pipoca pulando na máquina. */
    val popping: Boolean,
    /** Alguém atravessando a porta do saguão. */
    val doorOpen: Boolean,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.CINEMA

    companion object {
        fun atTime(t: Long, popping: Boolean = false, door: Boolean = false) =
            CinemaAmbient(t, CinemaLightingController.phaseAt(t), CinemaLightingController.lightAt(t), CinemaLightingController.screenColorAt(t), popping, door)
    }
}

/**
 * Cinema (Sprint 5): saguão no térreo (bilheteria, porta, bomboniere com máquina de pipoca) e a
 * sala no andar de cima, vista a partir da tela — a plateia fica de frente para nós, iluminada
 * pelo filme. Uma escada central liga os dois. Espectador A (chega e assiste), B (compra pipoca,
 * come e reage), C (chega atrasado, já no escuro, e procura o lugar) e a atendente.
 */
internal object CinemaVenue : VenueBlueprint(SceneId.CINEMA) {
    const val SERVICE = "pipoca"
    const val BUCKET = "bucket"

    const val DOOR = "door"
    const val LOBBY_MID = "lobby_mid"
    const val BUY = "buy"
    const val BUY_QUEUE = "buy_queue"
    const val STAIR_BOTTOM = "stair_bottom"
    const val STAIR_TOP = "stair_top"
    const val ATT_REG = "att_reg"
    const val ATT_MACHINE = "att_machine"

    /** Fileiras da sala (y do quadril de quem senta): 1 é a da frente. */
    const val ROW_1 = 134
    const val ROW_2 = 104
    const val ROW_3 = 74
    const val AISLE_X = 134
    val SEAT_XS_LEFT = intArrayOf(100, 73, 46)
    val SEAT_XS_RIGHT = intArrayOf(148, 175, 202)

    /** Pontos do Hoodie (CinemaScene): poltrona na fileira 1, à direita do corredor. */
    const val HOODIE_SEAT_X = 148
    const val HOODIE_POPCORN_X = 204
    const val HOODIE_POPCORN_Y = 302
    const val HOODIE_BOX_OFFICE_X = 28
    const val HOODIE_BOX_OFFICE_Y = 250

    const val COUNTER_X0 = 164
    const val COUNTER_TOP = 240

    private fun seatId(row: Int, x: Int) = "seat_${row}_$x"
    private fun aisleId(row: Int) = "aisle_$row"

    /** Lugares que os NPCs podem usar (o da fileira 1 à direita do corredor é do Hoodie). */
    val NPC_SEATS: List<String> = listOf(seatId(ROW_1, 100), seatId(ROW_1, 73), seatId(ROW_2, 100), seatId(ROW_2, 46), seatId(ROW_2, 175), seatId(ROW_3, 73), seatId(ROW_3, 175), seatId(ROW_3, 202))

    private val graph: VenueNavigationGraph = run {
        val spots = mutableListOf(
            VenueSpot(DOOR, 76, 232, capacity = 4, offstage = true),
            VenueSpot(LOBBY_MID, 100, 268, capacity = 3),
            VenueSpot(BUY, 150, 298, interaction = "bomboniere"),
            VenueSpot(BUY_QUEUE, 112, 300, interaction = "fila"),
            VenueSpot(STAIR_BOTTOM, AISLE_X, 240, capacity = 3),
            VenueSpot(STAIR_TOP, AISLE_X, 160, capacity = 3),
            VenueSpot(ATT_REG, 186, 262, interaction = "caixa da bomboniere"),
            VenueSpot(ATT_MACHINE, 218, 262, interaction = "máquina de pipoca"),
        )
        val edges = mutableListOf(
            DOOR to LOBBY_MID, LOBBY_MID to STAIR_BOTTOM, LOBBY_MID to BUY_QUEUE, BUY_QUEUE to BUY, BUY to STAIR_BOTTOM,
            STAIR_BOTTOM to STAIR_TOP, ATT_REG to ATT_MACHINE,
        )
        var below = STAIR_TOP
        for (row in intArrayOf(ROW_1, ROW_2, ROW_3)) {
            spots += VenueSpot(aisleId(row), AISLE_X, row, capacity = 3)
            edges += below to aisleId(row)
            below = aisleId(row)
            var prev = aisleId(row)
            for (x in SEAT_XS_LEFT) { val id = seatId(row, x); spots += VenueSpot(id, x, row, interaction = "poltrona"); edges += prev to id; prev = id }
            prev = aisleId(row)
            for (x in SEAT_XS_RIGHT) {
                // A poltrona do Hoodie não está no grafo: ninguém senta nem passa por ela.
                if (row == ROW_1) break
                val id = seatId(row, x); spots += VenueSpot(id, x, row, interaction = "poltrona"); edges += prev to id; prev = id
            }
        }
        VenueNavigationGraph(
            "cinema", spots, edges,
            obstacles = listOf(
                VenueObstacle("bomboniere", COUNTER_X0, 266, 238, 290),
                VenueObstacle("parede do saguão (esquerda)", 0, 176, 110, 228),
                VenueObstacle("parede do saguão (direita)", 160, 176, 239, 228),
            ),
        )
    }

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        val attendant = VenueActorSpec(
            NpcCharacterRegistry.RACCOON_COMMUTER,
            VenueNpcProfile("popcorn_attendant", VenueRole.POPCORN_ATTENDANT, mapOf(VenueAction.RESTOCK to 30, VenueAction.WAIT to 70), 0f, 1_800, 3_600),
            slotSeed = 601, start = VenueStart.POST, startSpot = ATT_REG,
        )
        val watcher = VenueActorSpec(
            NpcCharacterRegistry.BULLDOG_EXEC,
            VenueNpcProfile("moviegoer", VenueRole.MOVIEGOER, mapOf(VenueAction.WATCH to 80, VenueAction.PHONE to 20), 0f, 4_000, 8_000),
            slotSeed = 607, start = VenueStart.OFFSTAGE,
        )
        val snacker = VenueActorSpec(
            NpcCharacterRegistry.DOG_SHOPPER,
            VenueNpcProfile("moviegoer_snacker", VenueRole.MOVIEGOER_SNACKER, mapOf(VenueAction.WATCH to 55, VenueAction.CONSUME to 45), 0f, 3_000, 6_000),
            slotSeed = 613, start = VenueStart.OFFSTAGE,
        )
        val late = VenueActorSpec(
            NpcCharacterRegistry.RABBIT_READER,
            VenueNpcProfile("moviegoer_late", VenueRole.MOVIEGOER_LATE, mapOf(VenueAction.WATCH to 90, VenueAction.OBSERVE to 10), 0f, 4_000, 8_000),
            slotSeed = 617, start = VenueStart.OFFSTAGE,
        )
        return if (key.night) listOf(attendant, watcher, late) else listOf(attendant, watcher, snacker, late)
    }

    override fun initialize(s: VenueNpcSession, c: VenueCursor) {
        super.initialize(s, c)
        if (c.role == VenueRole.POPCORN_ATTENDANT) c.front = true
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        if (c.role == VenueRole.POPCORN_ATTENDANT) attendant(s, c) else moviegoer(s, c)
        c.decisions++
    }

    // ───── Atendente ─────

    private fun attendant(s: VenueNpcSession, c: VenueCursor) {
        s.coordinator.nextPending(SERVICE, c.time)?.let { order ->
            VenueScripts.serveOrder(s, c, order, ATT_REG, VenueScripts.line(ATTENDANT_LINES, c), VenueEvent.POPCORN_PICKED) {
                s.walkTo(c, ATT_MACHINE, VenueNpcState.NAVIGATING, VenueAction.PREPARE)
                s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE)
                c.held = Item.POPCORN
                s.stay(c, SCOOP_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.PREPARE)
                ATT_REG
            }
            return
        }
        val calm = VenueScripts.calmFor(s, c, SERVICE)
        if (calm > RESTOCK_MS + 3_000 && s.choose(c, setOf(VenueAction.RESTOCK, VenueAction.WAIT)) == VenueAction.RESTOCK) {
            // Repõe os baldes e saquinhos ao lado da máquina.
            s.walkTo(c, ATT_MACHINE, VenueNpcState.NAVIGATING, VenueAction.RESTOCK)
            s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK)
            s.stay(c, RESTOCK_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK, NpcAnimation.RESTOCK)
            c.remember(VenueAction.RESTOCK)
            return
        }
        VenueScripts.waitAtPost(s, c, ATT_REG, calm)
    }

    // ───── Espectadores ─────

    private object Phase {
        const val ARRIVED = 1
        const val TO_ORDER = 2
        const val WAITING = 3
        const val TO_SEAT = 4
        const val SEATED = 5
        const val LEAVING = 6
    }

    /** Janela de chegada desta sessão para o espectador (o atrasado chega já com a luz baixando). */
    private fun arrivalWindow(c: VenueCursor, cycle: Long): LongRange = when (c.role) {
        VenueRole.MOVIEGOER_LATE -> cycle + 72_000..cycle + 86_000
        VenueRole.MOVIEGOER_SNACKER -> cycle + 4_000..cycle + 28_000
        else -> cycle + 10_000..cycle + 44_000
    }

    private fun moviegoer(s: VenueNpcSession, c: VenueCursor) {
        if (c.offstage) { arrive(s, c); return }
        // A sessão é a da chegada (guardada em target), mesmo que o filme termine na seguinte.
        val cycle = c.target?.toLongOrNull() ?: CinemaLightingController.cycleStart(c.time)
        when (c.phase) {
            Phase.ARRIVED -> c.phase = if (c.role == VenueRole.MOVIEGOER_SNACKER && c.time < cycle + 52_000) Phase.TO_ORDER else Phase.TO_SEAT
            Phase.TO_ORDER -> {
                // Sem tempo para a fila: vai direto para a sala.
                if (c.time > cycle + 60_000) { s.endWait(c); c.phase = Phase.TO_SEAT; return }
                if (VenueScripts.queueAndOrder(s, c, SERVICE, BUY, listOf(BUY_QUEUE), BUCKET, VenueScripts.line(ORDER_LINES, c))) c.phase = Phase.WAITING
            }
            Phase.WAITING -> if (VenueScripts.collect(s, c, BUY, Item.POPCORN, VenueEvent.POPCORN_PICKED)) c.phase = Phase.TO_SEAT
            Phase.TO_SEAT -> takeSeat(s, c, cycle)
            Phase.SEATED -> watch(s, c, cycle)
            else -> {
                s.endWait(c)
                s.walkTo(c, LOBBY_MID, VenueNpcState.EXITING, VenueAction.EXIT)
                s.leave(c, DOOR, 1_000)
            }
        }
    }

    private fun arrive(s: VenueNpcSession, c: VenueCursor) {
        var cycle = CinemaLightingController.cycleStart(c.time)
        var window = arrivalWindow(c, cycle)
        if (c.time > window.last) { cycle += CinemaLightingController.CYCLE_MS; window = arrivalWindow(c, cycle) }
        val at = window.first + (s.rand(c, 3) * (window.last - window.first)).toLong()
        if (at > c.time) { s.offstageWait(c, at - c.time); return }
        s.enter(c, DOOR, LOBBY_MID, VenueAction.WATCH)
        c.target = cycle.toString()
        c.phase = Phase.ARRIVED; c.held = Item.NONE; c.visits = 0
    }

    /** Sobe a escada, segue pela fileira até a poltrona reservada nesta sessão e senta. */
    private fun takeSeat(s: VenueNpcSession, c: VenueCursor, cycle: Long) {
        val until = cycle + CinemaLightingController.LOBBY_AT
        val seat = c.node?.takeIf { it in NPC_SEATS } ?: s.chooseFree(c, NPC_SEATS, until - c.time, avoidRecent = false)
        if (seat == null) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.LOOK); return }
        s.endWait(c)
        s.reservation.reserve(seat, c.npcId, c.time, until)
        s.coordinator.emit(c.time, VenueEvent.SEAT_RESERVED, c.npcId, seat)
        val row = s.spot(seat).floorY
        s.walkTo(c, aisleId(row), VenueNpcState.NAVIGATING, VenueAction.WATCH)
        if (c.role == VenueRole.MOVIEGOER_LATE && CinemaLightingController.phaseAt(c.time) != CinemaPhase.LOBBY) {
            // No escuro, procura o lugar olhando a fileira antes de entrar nela.
            s.stay(c, 1_600, VenueNpcState.WAITING, VenueAction.OBSERVE, NpcAnimation.LOOK)
        }
        s.walkTo(c, seat, VenueNpcState.NAVIGATING, VenueAction.WATCH)
        s.sit(c, VenueAction.WATCH)
        c.phase = Phase.SEATED
    }

    /**
     * Na poltrona: antes do filme, olha em volta ou o celular; no filme, assiste (o da pipoca
     * come) e reage aos momentos do filme com atraso próprio; quando as luzes acendem, sai.
     */
    private fun watch(s: VenueNpcSession, c: VenueCursor, cycle: Long) {
        val t = c.time
        if (t >= cycle + CinemaLightingController.LIGHTS_AT + 1_500) {
            s.coordinator.emit(t, VenueEvent.SEAT_RELEASED, c.npcId, c.node)
            s.reservation.releaseAt(c.node ?: return, c.npcId, t)
            c.phase = Phase.LEAVING
            s.standUp(c, VenueAction.EXIT)
            s.walkTo(c, STAIR_TOP, VenueNpcState.EXITING, VenueAction.EXIT)
            return
        }
        if (t < cycle + CinemaLightingController.SCREEN_AT) {
            val untilFilm = cycle + CinemaLightingController.SCREEN_AT - t
            val anim = if (c.role == VenueRole.MOVIEGOER && s.rand(c, 5) < 0.4f) NpcAnimation.SIT_PHONE else NpcAnimation.SIT_LOOK
            s.stay(c, minOf(untilFilm, 4_000L + (s.rand(c, 7) * 3_000).toLong()), VenueNpcState.WAITING, VenueAction.WAIT, anim, Facing.FRONT)
            return
        }
        // Cada um reage no seu tempo (rápido, médio, demorado), com uma variação pequena por dia.
        val base = when (c.role) { VenueRole.MOVIEGOER -> 250L; VenueRole.MOVIEGOER_SNACKER -> 850L; else -> 1_450L }
        val delay = base + Math.floorMod(s.seed + c.spec.slotSeed * 137, 250)
        val next = CinemaLightingController.beats(cycle, s.seed).map { (at, kind) -> at + delay to kind }.firstOrNull { it.first >= t }
        val endOfFilm = cycle + CinemaLightingController.LIGHTS_AT + 1_500
        val chunkEnd = minOf(next?.first ?: endOfFilm, endOfFilm, t + 9_000)
        if (chunkEnd > t) {
            val eating = c.held == Item.POPCORN && s.choose(c, setOf(VenueAction.WATCH, VenueAction.CONSUME)) == VenueAction.CONSUME
            s.stay(c, chunkEnd - t, VenueNpcState.PERFORMING_ACTION, if (eating) VenueAction.CONSUME else VenueAction.WATCH,
                if (eating) NpcAnimation.SIT_POPCORN else NpcAnimation.SIT_WATCH, Facing.FRONT)
            c.remember(if (eating) VenueAction.CONSUME else VenueAction.WATCH)
        }
        if (next != null && c.time >= next.first && c.time < endOfFilm) {
            s.stay(c, NpcPoseLibrary.REACTION_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.WATCH, NpcAnimation.REACTION, Facing.FRONT, reaction = next.second)
        }
    }

    override fun ambient(s: VenueNpcSession, t: Long): CinemaAmbient {
        var popping = false; var door = false
        s.allCursors().forEach { c ->
            val seg = s.segmentAt(c.index, t) ?: return@forEach
            if (seg.animation == NpcAnimation.PREPARE) popping = true
            if (!seg.offstage && (seg.fromNode == DOOR || seg.toNode == DOOR)) door = true
        }
        return CinemaAmbient.atTime(t, popping, door)
    }

    private const val SCOOP_MS = 1_800L
    private const val RESTOCK_MS = 2_600L

    private val ORDER_LINES = listOf("Uma pipoca media", "Pipoca doce, por favor", "Uma grande")
    private val ATTENDANT_LINES = listOf("Saindo", "Bom filme", "Aqui esta")
}
