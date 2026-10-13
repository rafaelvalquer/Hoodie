package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcReaction
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import kotlin.math.abs
import kotlin.math.max

/**
 * Identidade de uma simulação de estabelecimento: mesma chave, mesma cena em qualquer instante
 * (seção 9.4 do plano). O tempo é relativo ao início da sessão visual — nunca ao relógio do sistema.
 */
data class VenueSessionKey(val scene: SceneId, val placeId: Long?, val daySeed: Int, val variant: Int, val night: Boolean)

/** Visão de um NPC dentro da sessão: o que o renderer consulta para este slot. */
class VenueNpcActor internal constructor(val session: VenueNpcSession, val index: Int, val npcId: String) {
    fun frameStateAt(timeMs: Long): VenueNpcFrameState = session.snapshotAt(timeMs).npcs[index]
    fun movementAt(timeMs: Long): NpcMovement = frameStateAt(timeMs).movement
}

/**
 * Personagem durante o planejamento. As cenas ([VenueBlueprint]) guardam aqui a fase do roteiro
 * (ex.: "na fila", "esperando o café") entre uma decisão e outra.
 */
internal class VenueCursor(val index: Int, val spec: VenueActorSpec) {
    val npcId: String get() = spec.profile.id
    val profile: VenueNpcProfile get() = spec.profile
    val role: VenueRole get() = spec.profile.role
    var time = 0L
    var x = 0; var y = 0
    var node: String? = null
    var legFrom: String? = null; var legTo: String? = null
    var facingRight = true
    var front = false
    var seated = false
    var offstage = true
    var walked = 0f
    var held: Item = Item.NONE
    var decisions = 0L
    val recent = ArrayDeque<VenueAction>()
    val readyAt = HashMap<VenueAction, Long>()
    /** Roteiro em andamento (cada cena define o significado). */
    var phase = 0
    var orderId = -1
    var target: String? = null
    var visits = 0
    var waitStartedAt = -1L
    /** Pontos de atividade recentes (evita o vai-e-vem A → B → A). */
    val recentSpots = ArrayDeque<String>()
    val msPerPixel: Long get() = SpeciesMotionProfiles.forCharacter(spec.style).msPerPixel

    fun remember(action: VenueAction) {
        recent.addLast(action)
        if (recent.size > 6) recent.removeFirst()
    }

    fun visited(spot: String) {
        if (recentSpots.lastOrNull() == spot) return
        recentSpots.addLast(spot)
        if (recentSpots.size > 2) recentSpots.removeFirst()
    }
}

/**
 * Roteiro de uma cena: grafo, elenco, pontos do Hoodie, o passo de cada função e o estado
 * dos objetos. A sessão só oferece movimento, tempo e coordenação genéricos.
 */
internal abstract class VenueBlueprint(val scene: SceneId) {
    abstract fun graph(variant: Int): VenueNavigationGraph

    /** Pontos que só o Hoodie usa (a cadeira dele, o balcão onde ele é atendido). */
    abstract fun hoodieSpots(variant: Int): Set<String>

    abstract fun actors(key: VenueSessionKey): List<VenueActorSpec>

    /** Decide e planeja o próximo trecho do personagem (sempre avança [VenueCursor.time]). */
    abstract fun step(s: VenueNpcSession, c: VenueCursor)

    /** Objetos e ambiente da cena no instante [t]. */
    abstract fun ambient(s: VenueNpcSession, t: Long): VenueAmbientState

    /** Posição inicial; o padrão cobre POST, INSIDE e OFFSTAGE pela primeira entrada. */
    open fun initialize(s: VenueNpcSession, c: VenueCursor) {
        val spec = c.spec
        when (spec.start) {
            VenueStart.POST, VenueStart.INSIDE -> {
                val p = s.spot(spec.startSpot ?: error("${c.npcId}: sem ponto inicial"))
                c.offstage = false; c.x = p.x; c.y = p.floorY; c.node = p.id
            }
            VenueStart.OFFSTAGE -> {
                val door = s.graph.spots.values.first { it.offstage }
                c.offstage = true; c.x = door.x; c.y = door.floorY; c.node = null
                if (spec.startDelayMs > 0) s.offstageWait(c, spec.startDelayMs)
            }
        }
    }
}

/**
 * Simulação dos personagens de um estabelecimento, pertencente a um único SceneRenderer.
 *
 * Todos os personagens são planejados JUNTOS numa linha do tempo de segmentos (andar, virar,
 * parar fazendo algo), gerada adiante a partir da semente e consultada por instante. Quem tem a
 * menor "hora livre" decide primeiro; assim a cena num instante depende só da chave e do instante
 * — nunca da frequência de quadros nem da ordem das consultas. Voltar no tempo além da janela
 * guardada refaz a simulação do zero, com o mesmo resultado (replay do Diário).
 *
 * Um personagem que espera outro (o cliente esperando o café) não trava os demais: ele espera em
 * fatias curtas ([waitSlice]) e, a cada fatia, olha de novo se o outro já planejou a parte dele.
 */
class VenueNpcSession internal constructor(val key: VenueSessionKey, internal val blueprint: VenueBlueprint) {
    internal val variant: Int = key.variant
    internal val seed: Int = key.daySeed * 41 + key.variant * 13 + key.scene.ordinal * 7 + (key.placeId?.toInt() ?: 0) * 3
    internal val graph: VenueNavigationGraph = blueprint.graph(variant)
    private val specs: List<VenueActorSpec> = blueprint.actors(key)

    // ───────────── Segmentos ─────────────

    internal enum class Kind { STAY, WALK, TURN, TURN_FRONT_IN, TURN_FRONT_OUT }

    internal class Segment(
        val start: Long,
        val end: Long,
        val kind: Kind,
        val state: VenueNpcState,
        val action: VenueAction?,
        val animation: NpcAnimation,
        val fromX: Int, val fromY: Int, val toX: Int, val toY: Int,
        val facingRight: Boolean,
        val facing: Facing?,
        val seated: Boolean,
        val walkedStart: Float,
        val spot: String?,
        val held: Item,
        val speech: String? = null,
        val hidden: Boolean = false,
        val offstage: Boolean = false,
        /** Desloca o relógio da pose (esperas em fatias continuam a mesma respiração). */
        val localOffsetMs: Long = 0,
        val fromNode: String? = null,
        val toNode: String? = null,
        val endsFront: Boolean = facing == Facing.FRONT,
        val endsSeated: Boolean = seated,
        /** Reação (riso, susto) quando a animação é REACTION. */
        val reaction: NpcReaction? = null,
    ) {
        val distance: Int get() = max(abs(toX - fromX), abs(toY - fromY))
    }

    private val timelines = List(specs.size) { ArrayList<Segment>() }
    private val retainedFrom = LongArray(specs.size)
    private var cursors: List<VenueCursor> = newCursors()
    internal val reservation = VenueSpotReservation(blueprint.hoodieSpots(variant))
    internal val coordinator = VenueInteractionCoordinator(seed)

    private var cachedAt = Long.MIN_VALUE
    private var cached: VenueFrameSnapshot? = null
    private var disposed = false

    val actors: List<VenueNpcActor> = specs.mapIndexed { i, s -> VenueNpcActor(this, i, s.profile.id) }

    val npcSlots: List<AmbientNpcSlot> = specs.mapIndexed { i, s ->
        AmbientNpcSlot(
            definition = AmbientNpcDefinition(
                s.profile.id, s.style,
                NpcBehaviorProfile(NpcAnimation.IDLE, SpeciesMotionProfiles.forCharacter(s.style), reactions = false),
            ),
            x = 0, floorY = 0, baseline = 0, seed = s.slotSeed,
            depth = NpcDepth.SCENE,
            venueActor = actors[i],
        )
    }

    private fun newCursors() = specs.mapIndexed { i, s -> VenueCursor(i, s) }

    init { cursors.forEach { blueprint.initialize(this, it) } }

    internal fun spot(id: String): VenueSpot = graph.spot(id)

    internal fun rand(c: VenueCursor, salt: Long): Float = NpcDeterministicRandom.value(c.npcId, seed, c.decisions * 97 + salt)

    internal fun cursor(id: String): VenueCursor? = cursors.firstOrNull { it.npcId == id }

    internal fun allCursors(): List<VenueCursor> = cursors

    // ───────────── Consulta ─────────────

    @Synchronized
    fun snapshotAt(timeMs: Long): VenueFrameSnapshot {
        check(!disposed) { "VenueNpcSession is disposed" }
        val t = timeMs.coerceAtLeast(0L)
        if (t == cachedAt) cached?.let { return it }
        if (retainedFrom.any { t < it }) resetSimulation()
        ensure(t)
        val snapshot = VenueFrameSnapshot(t, cursors.map { frameState(it, t) }, blueprint.ambient(this, t))
        cachedAt = t; cached = snapshot
        return snapshot
    }

    /** Linha do tempo de um personagem entre dois instantes (testes e folhas de revisão). */
    @Synchronized
    internal fun segmentsBetween(index: Int, from: Long, until: Long): List<Segment> {
        if (retainedFrom.any { from < it }) resetSimulation()
        ensure(until)
        return timelines[index].filter { it.end > from && it.start < until }
    }

    /** Segmento ativo de um personagem em [t] (já planejado). */
    internal fun segmentAt(index: Int, t: Long): Segment? {
        val list = timelines[index]
        var lo = 0; var hi = list.lastIndex; var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].start <= t) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return list.getOrNull(found)?.takeIf { t < it.end }
    }

    @Synchronized
    internal fun dispose() {
        disposed = true
        timelines.forEach { it.clear() }
        reservation.clear()
        coordinator.reset()
        cached = null
    }

    internal val retainedSize: Int get() = timelines.sumOf { it.size } + reservation.size + coordinator.retainedSize

    @Synchronized
    internal fun retainedSizeForTests(): Int = retainedSize

    /** Pedidos, encontros e eventos já planejados (testes e folhas de revisão). */
    @Synchronized
    internal fun coordinatorForTests(): VenueInteractionCoordinator = coordinator

    private fun frameState(c: VenueCursor, t: Long): VenueNpcFrameState {
        val seg = segmentAt(c.index, t) ?: error("sem segmento para ${c.npcId} em $t")
        val local = t - seg.start
        val duration = (seg.end - seg.start).coerceAtLeast(1)
        val p = local.toFloat() / duration
        val x = seg.fromX + ((seg.toX - seg.fromX) * p).toInt()
        val y = seg.fromY + ((seg.toY - seg.fromY) * p).toInt()
        val facingRight = if (seg.kind == Kind.TURN && local < NpcPoseLibrary.TURN_MS / 3) !seg.facingRight else seg.facingRight
        val phase = when (seg.kind) {
            Kind.WALK -> if (seg.fromNode?.let { spot(it).offstage } == true) PathPhase.ENTER
                else if (seg.toNode?.let { spot(it).offstage } == true) PathPhase.EXIT else PathPhase.WALK
            Kind.TURN, Kind.TURN_FRONT_IN, Kind.TURN_FRONT_OUT -> PathPhase.TURN
            Kind.STAY -> if (seg.offstage) PathPhase.IDLE else PathPhase.STOP
        }
        val movement = NpcMovement(
            x, y, facingRight, seg.animation, local + seg.localOffsetMs,
            walkedPx = seg.walkedStart + seg.distance * p,
            phase = phase, seated = seg.seated, facing = seg.facing, heldItem = seg.held, reaction = seg.reaction,
        )
        return VenueNpcFrameState(
            npcId = c.npcId,
            role = c.role,
            state = if (seg.offstage) VenueNpcState.OFFSTAGE else seg.state,
            action = seg.action,
            spot = seg.spot ?: seg.toNode,
            movement = movement,
            speech = seg.speech?.let { VenueSpeechEvent(it, seg.start, seg.end - seg.start) },
            held = seg.held,
            hidden = seg.hidden || seg.offstage,
            seated = seg.seated,
        )
    }

    // ───────────── Simulação ─────────────

    private fun resetSimulation() {
        timelines.forEach { it.clear() }
        retainedFrom.fill(0L)
        cursors = newCursors()
        reservation.clear()
        coordinator.reset()
        cachedAt = Long.MIN_VALUE
        cached = null
        cursors.forEach { blueprint.initialize(this, it) }
    }

    /** Gera até que todos tenham plano além de [t]. */
    private fun ensure(t: Long) {
        var guard = 0
        while (true) {
            val next = cursors.minBy { it.time }
            if (next.time > t) break
            val before = next.time
            blueprint.step(this, next)
            if (next.time <= before) idleHere(next, 500)
            check(++guard < MAX_STEPS_PER_QUERY) { "simulação de ${key.scene} não avança" }
        }
        prune()
    }

    private fun prune() {
        val minTime = cursors.minOf { it.time }
        reservation.prune(minTime - 30_000)
        timelines.forEachIndexed { i, list ->
            if (list.size > TIMELINE_LIMIT) {
                list.subList(0, TIMELINE_PRUNE).clear()
                retainedFrom[i] = list.first().start
            }
        }
        // Pedidos e eventos antigos só são descartados aquém da janela consultável.
        val queryable = retainedFrom.max()
        if (queryable > 0) coordinator.prune(queryable - VenueInteractionCoordinator.EVENT_MEMORY_MS)
    }

    // ───────────── Construção de segmentos (primitivas das cenas) ─────────────

    internal fun append(c: VenueCursor, seg: Segment) {
        timelines[c.index] += seg
        c.time = seg.end
        c.x = seg.toX; c.y = seg.toY
        c.facingRight = seg.facingRight
        c.front = seg.endsFront
        c.seated = seg.endsSeated
        c.offstage = seg.offstage
        if (seg.kind == Kind.WALK) {
            c.walked += seg.distance.toFloat()
            c.legFrom = seg.fromNode; c.legTo = seg.toNode
            c.node = seg.toNode
        }
    }

    /**
     * Parado fazendo algo. Sair da frente (sentado, foto) para uma pose de perfil sempre passa
     * por uma virada. Quem está parado num ponto o ocupa de fato: a reserva acompanha a parada real.
     */
    internal fun stay(
        c: VenueCursor, ms: Long, state: VenueNpcState, action: VenueAction?, animation: NpcAnimation,
        facing: Facing? = Facing.SIDE, spot: String? = c.node, speech: String? = null, localOffsetMs: Long = 0,
        seated: Boolean = c.seated, endsSeated: Boolean = seated, hidden: Boolean = false,
        endsFront: Boolean = facing == Facing.FRONT, facingRight: Boolean = c.facingRight,
        reaction: NpcReaction? = null,
    ) {
        if (c.front && facing != Facing.FRONT && !seated) turnOutOfFront(c, c.facingRight, state, action)
        val start = c.time
        append(
            c, Segment(
                start, start + ms.coerceAtLeast(1), Kind.STAY, state, action, animation, c.x, c.y, c.x, c.y,
                facingRight, facing, seated, c.walked, spot, c.held, speech, hidden = hidden,
                localOffsetMs = localOffsetMs, toNode = c.node, endsFront = endsFront, endsSeated = endsSeated,
                reaction = reaction,
            ),
        )
        val here = c.node
        if (here != null && here == spot && !reservation.isHoodieSpot(here) && spot(here).let { it.capacity == 1 && !it.offstage }) {
            reservation.reserve(here, c.npcId, start, c.time + STAY_HOLD_MARGIN_MS)
        }
    }

    /** Espera de uma fatia mantendo a pose contínua (o relógio da pose segue de onde parou). */
    internal fun waitSlice(c: VenueCursor, action: VenueAction?, animation: NpcAnimation = NpcAnimation.IDLE, facing: Facing? = Facing.SIDE, ms: Long = WAIT_SLICE_MS) {
        if (c.waitStartedAt < 0) c.waitStartedAt = c.time
        stay(c, ms, VenueNpcState.WAITING, action, animation, facing, localOffsetMs = c.time - c.waitStartedAt)
    }

    internal fun endWait(c: VenueCursor) { c.waitStartedAt = -1 }

    internal fun idleHere(c: VenueCursor, ms: Long) {
        if (c.offstage) offstageWait(c, ms)
        else stay(c, ms, VenueNpcState.WAITING, null, if (c.seated) NpcAnimation.SIT_LOOK else NpcAnimation.IDLE,
            if (c.front || c.seated) Facing.FRONT else Facing.SIDE, seated = c.seated)
    }

    internal fun offstageWait(c: VenueCursor, ms: Long) {
        append(
            c, Segment(
                c.time, c.time + ms.coerceAtLeast(1), Kind.STAY, VenueNpcState.OFFSTAGE, VenueAction.EXIT, NpcAnimation.IDLE,
                c.x, c.y, c.x, c.y, c.facingRight, Facing.SIDE, false, c.walked, null, Item.NONE, offstage = true,
            ),
        )
    }

    /** Vira de perfil para o outro lado (180°), nunca de um quadro para o outro. */
    internal fun turnSide(c: VenueCursor, right: Boolean, state: VenueNpcState, action: VenueAction?) {
        if (c.front) { turnOutOfFront(c, right, state, action); return }
        if (c.facingRight == right) return
        append(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN, state, action,
                if (right) NpcAnimation.TURN_RIGHT else NpcAnimation.TURN_LEFT,
                c.x, c.y, c.x, c.y, right, null, false, c.walked, c.node, c.held, toNode = c.node,
                endsFront = false, endsSeated = false,
            ),
        )
    }

    /** Perfil → frente (sentar, ser atendido, fotografar). */
    internal fun turnIntoFront(c: VenueCursor, state: VenueNpcState, action: VenueAction?) {
        if (c.front) return
        append(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN_FRONT_IN, state, action, NpcAnimation.TURN_LEFT,
                c.x, c.y, c.x, c.y, c.facingRight, Facing.FRONT, false, c.walked, c.node, c.held, toNode = c.node,
                endsFront = true, endsSeated = false,
            ),
        )
    }

    /** Frente → perfil para o lado em que vai andar. */
    internal fun turnOutOfFront(c: VenueCursor, right: Boolean, state: VenueNpcState, action: VenueAction?) {
        if (!c.front) { turnSide(c, right, state, action); return }
        append(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN_FRONT_OUT, state, action, NpcAnimation.TURN_RIGHT,
                c.x, c.y, c.x, c.y, right, Facing.FRONT, false, c.walked, c.node, c.held, toNode = c.node,
                endsFront = false, endsSeated = false,
            ),
        )
    }

    private fun walkLeg(c: VenueCursor, tx: Int, ty: Int, fromNode: String?, toNode: String?, state: VenueNpcState, action: VenueAction?) {
        if (tx == c.x && ty == c.y) { c.node = toNode; return }
        val right = if (tx != c.x) tx > c.x else c.facingRight
        if (c.front) turnOutOfFront(c, right, state, action)
        if (right != c.facingRight) turnSide(c, right, state, action)
        val distance = max(abs(tx - c.x), abs(ty - c.y))
        append(
            c, Segment(
                c.time, c.time + distance * c.msPerPixel, Kind.WALK, state, action, NpcAnimation.WALK,
                c.x, c.y, tx, ty, right, null, false, c.walked, toNode, c.held,
                fromNode = fromNode, toNode = toNode, endsFront = false, endsSeated = false,
            ),
        )
    }

    /** Anda pelo grafo até [target]; quem está sentado levanta antes. */
    internal fun walkTo(c: VenueCursor, target: String, state: VenueNpcState = VenueNpcState.NAVIGATING, action: VenueAction? = null) {
        if (c.seated) standUp(c, action)
        var start = c.node
        if (start == null) {
            val back = c.legFrom ?: graph.nearest(c.x, c.y)
            val s = spot(back)
            walkLeg(c, s.x, s.floorY, c.legTo, back, state, action)
            start = back
        }
        for ((a, b) in graph.route(start, target).zipWithNext()) {
            val s = spot(b)
            walkLeg(c, s.x, s.floorY, a, b, state, action)
        }
        c.node = target
    }

    internal fun estimateRouteMs(c: VenueCursor, target: String): Long {
        val start = c.node ?: graph.nearest(c.x, c.y)
        var x = c.x; var y = c.y; var total = if (c.seated) STAND_UP_MS else 0L
        for (node in graph.route(start, target)) {
            val s = spot(node)
            total += max(abs(s.x - x), abs(s.floorY - y)) * c.msPerPixel + NpcPoseLibrary.TURN_MS
            x = s.x; y = s.floorY
        }
        return total + NpcPoseLibrary.TURN_MS
    }

    /** Vira de frente e senta (640 ms, nunca de um quadro para o outro). */
    internal fun sit(c: VenueCursor, action: VenueAction?) {
        if (c.seated) return
        turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, action)
        stay(c, SIT_MS, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.SIT, Facing.FRONT, seated = false, endsSeated = true)
    }

    internal fun standUp(c: VenueCursor, action: VenueAction?) {
        if (!c.seated) return
        stay(c, STAND_UP_MS, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.STAND_UP, Facing.FRONT, seated = false, endsSeated = false)
    }

    /** Entra pela porta/borda [door] (fora de cena) e anda até [first]. */
    internal fun enter(c: VenueCursor, door: String, first: String, action: VenueAction?) {
        val d = spot(door)
        c.x = d.x; c.y = d.floorY; c.node = door; c.offstage = false; c.front = false; c.seated = false
        c.facingRight = spot(first).x >= d.x
        walkTo(c, first, VenueNpcState.ENTERING, action)
    }

    /** Sai pela porta/borda [door] e fica fora de cena por [awayMs]. */
    internal fun leave(c: VenueCursor, door: String, awayMs: Long) {
        walkTo(c, door, VenueNpcState.EXITING, VenueAction.EXIT)
        c.node = null; c.legFrom = null; c.legTo = null
        c.held = Item.NONE
        offstageWait(c, awayMs)
    }

    /** Fala uma linha se nenhuma outra estiver no ar; senão faz o gesto calado. */
    internal fun say(c: VenueCursor, line: String, ms: Long, state: VenueNpcState, action: VenueAction?, facing: Facing? = Facing.SIDE, seated: Boolean = c.seated) {
        val free = coordinator.speechFree(c.time, c.time + ms)
        if (free) coordinator.addSpeech(c.time, c.time + ms)
        stay(c, ms, state, action, if (free) NpcAnimation.TALK else if (seated) NpcAnimation.SIT_LOOK else NpcAnimation.IDLE, facing,
            speech = if (free) line else null, seated = seated)
    }

    // ───────────── Ocupação ─────────────

    internal fun isFree(c: VenueCursor, spot: String, from: Long, until: Long): Boolean =
        reservation.isFree(spot, from, until, c.npcId, spot(spot).capacity)

    /** Primeiro ponto livre durante a chegada e a permanência, evitando os dois últimos visitados. */
    internal fun chooseFree(c: VenueCursor, candidates: List<String>, holdMs: Long, avoidRecent: Boolean = true): String? {
        val rotation = (rand(c, 11) * candidates.size.coerceAtLeast(1)).toInt()
        val ordered = candidates.indices.map { candidates[(it + rotation) % candidates.size] }
        val preferred = if (avoidRecent) ordered.filter { it !in c.recentSpots } + ordered.filter { it in c.recentSpots } else ordered
        for (s in preferred) {
            val arrive = c.time + if (s == c.node) 0 else estimateRouteMs(c, s)
            if (isFree(c, s, c.time, arrive + holdMs + 1_500)) return s
        }
        return null
    }

    internal fun reserveFor(c: VenueCursor, spot: String, holdMs: Long) {
        val arrive = c.time + if (spot == c.node) 0 else estimateRouteMs(c, spot)
        reservation.reserve(spot, c.npcId, c.time, arrive + holdMs + 1_500)
    }

    internal fun choose(c: VenueCursor, available: Set<VenueAction>): VenueAction? =
        VenueNpcBrain.choose(c.profile, seed, c.decisions, VenueDecisionContext(c.time, available, c.recent.toList(), c.readyAt))

    // ───────────── Encontros ─────────────

    /**
     * Conversa entre [a] e [b] em [aSpot]/[bSpot] (já reservados): os dois se aproximam, quem
     * chega antes espera virado para o outro, as falas alternam, um balão por vez.
     */
    internal fun converse(a: VenueCursor, b: VenueCursor, aSpot: String, bSpot: String, lines: List<Pair<Boolean, String>>) {
        walkTo(a, aSpot, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
        walkTo(b, bSpot, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
        val aLeft = a.x <= b.x
        turnSide(a, aLeft, VenueNpcState.INTERACTING, VenueAction.SOCIAL)
        turnSide(b, !aLeft, VenueNpcState.INTERACTING, VenueAction.SOCIAL)
        val talkMs = lines.sumOf { lineMs(it.second) + VenueInteractionCoordinator.LINE_GAP_MS }
        val meet = coordinator.firstSpeechFreeFrom(max(a.time, b.time) + 300, talkMs)
        val encounter = coordinator.register(a.npcId, b.npcId, meet, meet + talkMs)
        listOf(a, b).forEach { c -> if (c.time < meet) stay(c, meet - c.time, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.IDLE) }
        for ((byA, text) in lines) {
            val speaker = if (byA) a else b
            val listener = if (byA) b else a
            val ms = lineMs(text)
            coordinator.addSpeech(speaker.time, speaker.time + ms)
            stay(speaker, ms, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.TALK, speech = text)
            stay(listener, ms, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.IDLE)
            listOf(a, b).forEach { c -> stay(c, VenueInteractionCoordinator.LINE_GAP_MS, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.IDLE) }
        }
        val sync = max(a.time, b.time)
        listOf(a, b).forEach { c -> if (c.time < sync) stay(c, sync - c.time, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.IDLE) }
        a.readyAt[VenueAction.SOCIAL] = a.time + coordinator.cooldownMs(a.npcId, encounter.id.toLong())
        b.readyAt[VenueAction.SOCIAL] = b.time + coordinator.cooldownMs(b.npcId, encounter.id + 1L)
        b.remember(VenueAction.SOCIAL); b.decisions++
    }

    internal fun lineMs(text: String): Long = (1_200L + text.length * 45L).coerceAtMost(2_400L)

    companion object {
        const val SIT_MS = 640L
        const val STAND_UP_MS = 640L
        const val WAIT_SLICE_MS = 600L
        private const val STAY_HOLD_MARGIN_MS = 600L
        private const val TIMELINE_LIMIT = 2_048
        private const val TIMELINE_PRUNE = 1_024
        private const val MAX_STEPS_PER_QUERY = 2_000_000
    }
}
