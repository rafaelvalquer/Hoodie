package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.HoodieOccupancy
import com.hoodie.app.pixel.character.CharacterStyle
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
import com.hoodie.app.pixel.npc.events.SceneStoryEvent
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Identidade de uma simulação do Passeio: mesma chave, mesma cena em qualquer instante. */
data class LeisureSessionKey(val daySeed: Int, val variant: Int, val night: Boolean)

/** Como um visitante começa a sessão. */
internal enum class LeisureStart { LEFT, RIGHT, BENCH }

internal data class LeisureActorSpec(
    val style: CharacterStyle,
    val personality: LeisurePersonality,
    val slotSeed: Int,
    val start: LeisureStart,
    val startDelayMs: Long,
)

/** Visão de um NPC dentro da sessão: o que o renderer consulta para este slot. */
class LeisureNpcActor internal constructor(val session: LeisureNpcSession, val index: Int, val npcId: String) {
    fun frameStateAt(timeMs: Long): LeisureNpcFrameState = session.snapshotAt(timeMs).npcs[index]
    fun movementAt(timeMs: Long): NpcMovement = frameStateAt(timeMs).movement
}

/**
 * Simulação ambiental do Passeio, pertencente a um único SceneRenderer.
 *
 * Todos os visitantes são planejados JUNTOS numa linha do tempo de segmentos (andar, virar,
 * parar fazendo algo), gerada adiante a partir da semente e consultada por instante. Como a
 * ordem de planejamento é sempre a mesma (o visitante com a menor "hora livre" decide primeiro),
 * a cena num instante só depende de (semente, variante, período, instante) e das reservas do
 * Hoodie já observadas — nunca da frequência dos quadros nem da ordem das consultas. Voltar
 * no tempo além da janela guardada refaz a simulação do zero, com o mesmo resultado.
 */
class LeisureNpcSession internal constructor(val key: LeisureSessionKey, specs: List<LeisureActorSpec>) {
    private val variant = key.variant.mod(3)
    private val seed = key.daySeed * 37 + variant * 17

    // ───────────── Segmentos ─────────────

    internal enum class Kind { STAY, WALK, TURN, TURN_FRONT_IN, TURN_FRONT_OUT }

    internal class Segment(
        val start: Long,
        val end: Long,
        val kind: Kind,
        val intent: LeisureNpcIntent,
        val animation: NpcAnimation,
        val fromX: Int, val fromY: Int, val toX: Int, val toY: Int,
        val facingRight: Boolean,
        val facing: Facing?,
        val seated: Boolean,
        val walkedStart: Float,
        val spot: LeisureNpcSpot?,
        val activity: LeisureActivity?,
        val speech: String? = null,
        val offstage: Boolean = false,
        val socialId: Int = -1,
        val interruptible: Boolean = true,
        val usesBench: Boolean = false,
        val fromNode: LeisureNpcSpot? = null,
        val toNode: LeisureNpcSpot? = null,
        /** Desloca o relógio da pose (alinha o "clique" da foto ao início do segmento). */
        val localOffsetMs: Long = 0,
        /** Estado ao terminar o segmento. */
        val endsFront: Boolean = facing == Facing.FRONT,
        val endsSeated: Boolean = seated,
    ) {
        val distance: Int get() = max(abs(toX - fromX), abs(toY - fromY))

        /** Mesmo segmento encerrado antes, na posição em que estiver em [at]. */
        fun truncated(at: Long): Segment {
            val p = if (end > start) (at - start).toFloat() / (end - start) else 1f
            val x = fromX + ((toX - fromX) * p).toInt()
            val y = fromY + ((toY - fromY) * p).toInt()
            return Segment(
                start, at, kind, intent, animation, fromX, fromY, x, y, facingRight, facing, seated, walkedStart,
                spot, activity, speech, offstage, socialId, interruptible, usesBench, fromNode,
                if (kind == Kind.WALK) null else toNode, localOffsetMs, endsFront, endsSeated,
            )
        }
    }

    private class Cursor(val index: Int, val spec: LeisureActorSpec, val npcId: String) {
        var time = 0L
        var x = 0; var y = 0
        var node: LeisureNpcSpot? = null
        var legFrom: LeisureNpcSpot? = null; var legTo: LeisureNpcSpot? = null
        var facingRight = true
        var front = false
        var seated = false
        var offstage = true
        var walked = 0f
        val recent = ArrayDeque<LeisureActivity>()
        var decisions = 0L
        var visitCount = 0
        var visitLength = 6
        var photoReadyAt = 0L
        var socialReadyAt = 0L
        /** Últimas duas paradas no caminho (mesmo com mirante/encontro no meio): evitam o vai-e-vem A → B → A. */
        val recentPathStops = ArrayDeque<LeisureNpcSpot>()
        var lastStop: LeisureNpcSpot? = null
            set(value) {
                field = value
                if (value != null && value in PATH_STOPS && recentPathStops.lastOrNull() != value) {
                    recentPathStops.addLast(value)
                    if (recentPathStops.size > 2) recentPathStops.removeFirst()
                }
            }
        val msPerPixel: Long get() = SpeciesMotionProfiles.forCharacter(spec.style).msPerPixel
    }

    private val specs = specs.toList()
    private val timelines = List(specs.size) { ArrayList<Segment>() }
    private val retainedFrom = LongArray(specs.size)
    private var cursors = newCursors()
    private val reservation = LeisureSpotReservation()
    private val social = LeisureSocialCoordinator(seed, variant)
    /** Microeventos (vista compartilhada) planejados nesta simulação, com o mesmo determinismo. */
    private val stories = com.hoodie.app.pixel.npc.venue.VenueStoryLog()
    private val speech = ArrayList<LongArray>()
    private var dirty = false

    // Reservas do Hoodie (observadas quadro a quadro, histórico imutável).
    private var observedOnce = false
    private var lastObservationAt = Long.MIN_VALUE
    private var hoodie: HoodieOccupancy = HoodieOccupancy.ABSENT

    // Planejamento com prazo: o banco é cedido ao Hoodie no início da reserva dele.
    private var deadline = Long.MAX_VALUE
    private var deadlineHit = false

    private var cachedAt = Long.MIN_VALUE
    private var cachedHoodie: HoodieOccupancy? = null
    private var cached: LeisureFrameSnapshot? = null
    private var disposed = false

    val actors: List<LeisureNpcActor> = specs.mapIndexed { i, s -> LeisureNpcActor(this, i, s.style.id) }

    val npcSlots: List<AmbientNpcSlot> = specs.mapIndexed { i, s ->
        AmbientNpcSlot(
            definition = AmbientNpcDefinition(
                s.style.id, s.style,
                NpcBehaviorProfile(NpcAnimation.IDLE, SpeciesMotionProfiles.forCharacter(s.style), reactions = false),
            ),
            x = 0, floorY = 268, baseline = 268, seed = s.slotSeed,
            depth = NpcDepth.SCENE,
            leisureActor = actors[i],
        )
    }

    private fun newCursors() = specs.mapIndexed { i, s -> Cursor(i, s, s.style.id) }

    private fun spot(id: LeisureNpcSpot) = LeisureNavigationGraph.spot(variant, id)

    private fun rand(c: Cursor, salt: Long): Float = NpcDeterministicRandom.value(c.npcId, seed, c.decisions * 97 + salt)

    // ───────────── Hoodie ─────────────

    /** Registra o que o Hoodie quer fazer neste instante (só avança no tempo; voltar não reescreve a história). */
    @Synchronized
    fun observeHoodie(timeMs: Long, occupancy: HoodieOccupancy) {
        hoodie = occupancy
        val claiming = claimsBench(occupancy)
        if (!observedOnce) {
            observedOnce = true
            lastObservationAt = timeMs
            if (claiming) {
                // Já estava no banco ao entrar na cena: vale desde sempre (o rato não começa sentado).
                reservation.hoodieClaims += LeisureSpotReservation.HoodieClaim(Long.MIN_VALUE / 4, null)
                dirty = true
            }
            return
        }
        if (timeMs < lastObservationAt) return
        lastObservationAt = timeMs
        val open = reservation.hoodieClaims.lastOrNull()?.takeIf { it.end == null }
        if (claiming && open == null) {
            reservation.hoodieClaims += LeisureSpotReservation.HoodieClaim(timeMs, null)
            if (reservation.hoodieClaims.size > MAX_CLAIMS) reservation.hoodieClaims.removeAt(0)
            dirty = true
        } else if (!claiming && open != null) {
            open.end = timeMs
            dirty = true
        }
    }

    private fun claimsBench(o: HoodieOccupancy): Boolean {
        if (!o.present) return false
        if (o.targetSpot == SpotId.BENCH) return true
        return o.seated && abs(o.x - HOODIE_BENCH_X) <= 14 && abs(o.y - HOODIE_BENCH_Y) <= 8
    }

    // ───────────── Consulta ─────────────

    @Synchronized
    fun snapshotAt(timeMs: Long): LeisureFrameSnapshot {
        check(!disposed) { "LeisureNpcSession is disposed" }
        val t = timeMs.coerceAtLeast(0L)
        if (!dirty && t == cachedAt && cachedHoodie == hoodie) cached?.let { return it }
        if (dirty || retainedFrom.any { t < it }) resetSimulation()
        ensure(t)
        val states = cursors.map { c -> frameState(c, t) }
        val snapshot = LeisureFrameSnapshot(
            timeMs = t,
            npcs = states,
            ambient = ambientAt(t),
            benchHolder = if (reservation.hoodieClaimAt(t)) HOODIE_ID else benchNpcAt(t),
            activeSocialId = social.activeAt(t)?.id ?: -1,
        )
        cachedAt = t; cachedHoodie = hoodie; cached = snapshot
        return snapshot
    }

    /** Linha do tempo de um visitante entre dois instantes (testes e folhas de revisão). */
    @Synchronized
    internal fun segmentsBetween(index: Int, from: Long, until: Long): List<Segment> {
        if (dirty || retainedFrom.any { from < it }) resetSimulation()
        ensure(until)
        return timelines[index].filter { it.end > from && it.start < until }
    }

    internal fun encounters(): List<LeisureSocialCoordinator.Encounter> = social.all()

    @Synchronized
    internal fun dispose() {
        disposed = true
        timelines.forEach { it.clear() }
        speech.clear()
        reservation.clearNpcHolds()
        social.reset()
        stories.clear()
        cached = null
    }

    private fun benchNpcAt(t: Long): String? = cursors.firstNotNullOfOrNull { c ->
        segmentAt(c, t)?.takeIf { it.usesBench && (it.seated || it.toNode == LeisureNpcSpot.BENCH_SEAT || it.spot == LeisureNpcSpot.BENCH_SEAT) }?.let { c.npcId }
    }

    private fun segmentAt(c: Cursor, t: Long): Segment? = timelines[c.index].getOrNull(segmentIndexAt(c, t))?.takeIf { t < it.end }

    /** Índice do último segmento que começa até [t] (busca binária: a linha do tempo é ordenada). */
    private fun segmentIndexAt(c: Cursor, t: Long): Int {
        val list = timelines[c.index]
        var lo = 0; var hi = list.lastIndex; var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].start <= t) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    private fun frameState(c: Cursor, t: Long): LeisureNpcFrameState {
        val seg = segmentAt(c, t) ?: error("sem segmento para ${c.npcId} em $t")
        val local = t - seg.start
        val duration = (seg.end - seg.start).coerceAtLeast(1)
        val p = local.toFloat() / duration
        val x = seg.fromX + ((seg.toX - seg.fromX) * p).toInt()
        val y = seg.fromY + ((seg.toY - seg.fromY) * p).toInt()
        val facingRight = if (seg.kind == Kind.TURN && local < NpcPoseLibrary.TURN_MS / 3) !seg.facingRight else seg.facingRight
        val phase = when (seg.kind) {
            Kind.WALK -> when {
                seg.fromX < 0 || seg.fromX >= SCENE_W -> PathPhase.ENTER
                seg.toX < 0 || seg.toX >= SCENE_W -> PathPhase.EXIT
                else -> PathPhase.WALK
            }
            Kind.TURN, Kind.TURN_FRONT_IN, Kind.TURN_FRONT_OUT -> PathPhase.TURN
            Kind.STAY -> if (seg.offstage) PathPhase.IDLE else PathPhase.STOP
        }
        var movement = NpcMovement(
            x, y, facingRight, seg.animation, local + seg.localOffsetMs,
            walkedPx = seg.walkedStart + seg.distance * p,
            phase = phase, seated = seg.seated, facing = seg.facing,
        )
        val reaction = reactionToHoodie(c, seg, t, x, y)
        if (reaction != null) {
            movement = movement.copy(animation = NpcAnimation.REACTION, localTimeMs = reaction.second, facing = null, reaction = reaction.first)
        } else if (attentionToHoodiePhoto(seg, x, y)) {
            movement = movement.copy(animation = NpcAnimation.LOOK_WINDOW, facingRight = HOODIE_VIEW_X >= x, facing = Facing.SIDE)
        }
        val line = seg.speech
        return LeisureNpcFrameState(
            npcId = c.npcId,
            intent = seg.intent,
            activity = seg.activity,
            spot = seg.spot,
            movement = movement,
            speech = line?.let { LeisureSpeechEvent(it, seg.start, seg.end - seg.start) },
            reaction = reaction?.first,
            offstage = seg.offstage,
            socialId = seg.socialId,
        )
    }

    // ───────────── Reações ao Hoodie (P2) ─────────────

    private fun reactable(seg: Segment) = !seg.seated && seg.speech == null && seg.kind == Kind.STAY && !seg.offstage &&
        seg.intent in setOf(LeisureNpcIntent.IDLE, LeisureNpcIntent.OBSERVING, LeisureNpcIntent.USING_PHONE)

    /**
     * Olhar breve quando o Hoodie passa perto; sorriso ou aceno quando ele para ao lado. Uma janela
     * por visitante a cada [REACTION_WINDOW_MS] (cooldown natural), só dentro de uma atividade parada
     * e nunca no meio de conversa ou sentado.
     */
    private fun reactionToHoodie(c: Cursor, seg: Segment, t: Long, x: Int, y: Int): Pair<NpcReaction, Long>? {
        val h = hoodie
        if (!h.present || !reactable(seg)) return null
        if (abs(h.x - x) > 38 || abs(h.y - y) > 34) return null
        if (h.animation == AnimationId.LEISURE_PHOTO) return null
        val shifted = t + c.spec.slotSeed * 1_777L
        val windowStart = t - Math.floorMod(shifted, REACTION_WINDOW_MS)
        val local = t - windowStart
        if (local >= NpcPoseLibrary.REACTION_MS) return null
        if (windowStart < seg.start + 300 || windowStart + NpcPoseLibrary.REACTION_MS > seg.end - 200) return null
        val kind = when {
            h.walking -> NpcReaction.LOOK_AT_HOODIE
            Math.floorMod(Math.floorDiv(shifted, REACTION_WINDOW_MS), 2L) == 0L -> NpcReaction.SMILE
            else -> NpcReaction.WAVE
        }
        return kind to local
    }

    /** Hoodie fotografando por perto: quem está parado espera e olha para o mesmo ponto. */
    private fun attentionToHoodiePhoto(seg: Segment, x: Int, y: Int): Boolean {
        val h = hoodie
        return h.present && h.animation == AnimationId.LEISURE_PHOTO && reactable(seg) && seg.intent != LeisureNpcIntent.OBSERVING &&
            abs(h.x - x) <= 60 && abs(h.y - y) <= 50
    }

    // ───────────── Ambiente ─────────────

    private fun ambientAt(t: Long): LeisureAmbientState {
        var best: LeisureAmbientState? = null
        for (c in cursors) {
            val list = timelines[c.index]
            val idx = segmentIndexAt(c, t)
            if (idx < 0) continue
            // A atração reage a quem está (ou acabou de estar) diante dela.
            for (k in idx downTo max(0, idx - 4)) {
                val s = list[k]
                // Parado ou virando no lugar (para a foto) conta; andando até lá, não.
                if (s.spot != LeisureNpcSpot.NATURE_VIEW || s.kind == Kind.WALK || s.offstage) continue
                if (s.intent != LeisureNpcIntent.OBSERVING && s.intent != LeisureNpcIntent.PHOTOGRAPHING) continue
                // Quem está diante da atração agora sempre conta (mínimo > 0); depois de sair, some em rampa.
                val rampIn = ((t - s.start).toFloat() / LeisureAmbientState.RAMP_MS).coerceIn(0f, 1f)
                val rampOut = if (t >= s.end) 1f - ((t - s.end).toFloat() / LeisureAmbientState.RAMP_MS) else 1f
                val attraction = if (t < s.end) max(rampIn, MIN_LIVE_ATTRACTION) else min(rampIn, rampOut).coerceIn(0f, 1f)
                if (attraction <= 0f) break
                val local = t - s.start + s.localOffsetMs
                val photo = s.intent == LeisureNpcIntent.PHOTOGRAPHING && t < s.end
                val framing = photo && Math.floorMod(local + c.spec.slotSeed * 101L, LeisureNpcBrain.PHOTO_MS) in 650L until 2_200L
                val click = photo && Math.floorMod(local + c.spec.slotSeed * 101L, LeisureNpcBrain.PHOTO_MS) in 2_200L until 2_450L
                val state = LeisureAmbientState(
                    variant, t, attraction, c.npcId, s.toX, s.toY,
                    fountainBoost = if (variant == 1) (if (framing) 4 else 2) else 0,
                    photoFlash = variant == 1 && click,
                )
                if (best == null || state.attraction > best.attraction) best = state
                break
            }
        }
        return best ?: LeisureAmbientState.idle(variant, t)
    }

    // ───────────── Simulação ─────────────

    private fun resetSimulation() {
        timelines.forEach { it.clear() }
        retainedFrom.fill(0L)
        cursors = newCursors()
        reservation.clearNpcHolds()
        social.reset()
        stories.clear()
        speech.clear()
        dirty = false
        cachedAt = Long.MIN_VALUE
        cached = null
        cursors.forEach { initialize(it) }
    }

    init { cursors.forEach { initialize(it) } }

    private fun initialize(c: Cursor) {
        c.visitLength = c.spec.personality.visitLength.let { it.first + (rand(c, 1) * (it.last - it.first + 1)).toInt() }
        when (c.spec.start) {
            LeisureStart.BENCH -> if (!reservation.hoodieClaimAt(0)) {
                val seat = spot(LeisureNpcSpot.BENCH_SEAT)
                c.offstage = false; c.seated = true; c.front = true
                c.x = seat.x; c.y = seat.floorY; c.node = LeisureNpcSpot.BENCH_SEAT
            } else {
                // O Hoodie já está no banco: o rato começa passeando, nunca sentado no mesmo lugar.
                val stop = spot(LeisureNpcSpot.PATH_CENTER)
                c.offstage = false; c.x = stop.x; c.y = stop.floorY; c.node = LeisureNpcSpot.PATH_CENTER
                c.lastStop = LeisureNpcSpot.PATH_CENTER
            }
            LeisureStart.LEFT, LeisureStart.RIGHT -> {
                c.offstage = true
                c.x = if (c.spec.start == LeisureStart.LEFT) LeisureNavigationGraph.OFFSTAGE_LEFT_X else LeisureNavigationGraph.OFFSTAGE_RIGHT_X
                c.y = 268
                c.facingRight = c.spec.start == LeisureStart.LEFT
                if (c.spec.startDelayMs > 0) offstageWait(c, c.spec.startDelayMs)
            }
        }
    }

    /** Gera até que todos os visitantes tenham plano além de [t]. */
    private fun ensure(t: Long) {
        var guard = 0
        while (true) {
            val next = cursors.minBy { it.time }
            if (next.time > t) break
            step(next)
            check(++guard < MAX_STEPS_PER_QUERY) { "simulação do Passeio não avança" }
        }
        prune()
    }

    private fun prune() {
        val minTime = cursors.minOf { it.time }
        reservation.prune(minTime - 30_000)
        speech.removeAll { it[1] < minTime - 30_000 }
        timelines.forEachIndexed { i, list ->
            if (list.size > TIMELINE_LIMIT) {
                list.subList(0, TIMELINE_PRUNE).clear()
                retainedFrom[i] = list.first().start
            }
        }
    }

    private fun step(c: Cursor) {
        val before = c.time
        if (c.offstage) {
            reenter(c)
        } else {
            val activity = LeisureNpcBrain.choose(c.npcId, seed, c.decisions, c.spec.personality, context(c))
            val planned = when (activity) {
                LeisureActivity.SOCIAL -> planSocial(c)
                LeisureActivity.EXIT -> { planExit(c); true }
                LeisureActivity.READ, LeisureActivity.BENCH_REST -> { planBench(c, activity); true }
                LeisureActivity.OBSERVE -> planObserve(c, photo = false)
                LeisureActivity.PHOTO -> planObserve(c, photo = true)
                LeisureActivity.PHONE -> { planPhone(c); true }
                LeisureActivity.STRETCH -> planStop(c, LeisureActivity.STRETCH)
                LeisureActivity.EXPLORE, LeisureActivity.STROLL -> planStop(c, activity)
            }
            val done = if (planned) activity else { if (!planStop(c, LeisureActivity.STROLL)) idleHere(c, 1_500); LeisureActivity.STROLL }
            c.recent.addLast(done)
            if (c.recent.size > 6) c.recent.removeFirst()
            c.decisions++
            if (done != LeisureActivity.EXIT) c.visitCount++
        }
        if (c.time <= before) idleHere(c, 500)
    }

    private fun context(c: Cursor) = LeisureDecisionContext(
        now = c.time,
        seated = c.seated,
        night = key.night,
        benchAvailable = c.spec.personality == LeisurePersonality.CALM && benchFreeFor(c, c.time),
        observeSpotAvailable = observeOptions(c).isNotEmpty(),
        photoReadyAt = c.photoReadyAt,
        socialReadyAt = c.socialReadyAt,
        socialPossible = c.spec.personality != LeisurePersonality.CALM && partnerFor(c) != null,
        recent = c.recent.toList(),
        visitDone = c.visitCount >= c.visitLength,
    )

    private fun benchFreeFor(c: Cursor, at: Long) =
        !reservation.hoodieClaimAt(at) && reservation.isFree(LeisureNpcSpot.BENCH_SEAT, at, at + 15_000, c.npcId)

    // ───────────── Construção de segmentos ─────────────

    private fun append(c: Cursor, seg: Segment) {
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

    /** Acrescenta respeitando o prazo do banco; false = plano interrompido aqui. */
    private fun push(c: Cursor, seg: Segment): Boolean {
        if (deadlineHit) return false
        if (seg.start >= deadline) { deadlineHit = true; return false }
        if (seg.end > deadline) {
            append(c, if (seg.interruptible) seg.truncated(deadline) else seg)
            deadlineHit = true
            return false
        }
        append(c, seg)
        return true
    }

    private fun stay(
        c: Cursor, ms: Long, intent: LeisureNpcIntent, animation: NpcAnimation, facing: Facing?,
        spot: LeisureNpcSpot?, activity: LeisureActivity?, speech: String? = null, socialId: Int = -1,
        interruptible: Boolean = true, usesBench: Boolean = false, localOffsetMs: Long = 0,
        seated: Boolean = c.seated, endsSeated: Boolean = seated, endsFront: Boolean = facing == Facing.FRONT,
        facingRight: Boolean = c.facingRight,
    ): Boolean {
        // De frente (foto, banco) para uma pose de perfil: vira antes, nunca num quadro só.
        if (c.front && facing != Facing.FRONT && !seated && !turnOutOfFront(c, c.facingRight, intent, activity, usesBench)) return false
        return stayRaw(c, ms, intent, animation, facing, spot, activity, speech, socialId, interruptible, usesBench, localOffsetMs, seated, endsSeated, endsFront, facingRight)
    }

    private fun stayRaw(
        c: Cursor, ms: Long, intent: LeisureNpcIntent, animation: NpcAnimation, facing: Facing?,
        spot: LeisureNpcSpot?, activity: LeisureActivity?, speech: String?, socialId: Int,
        interruptible: Boolean, usesBench: Boolean, localOffsetMs: Long,
        seated: Boolean, endsSeated: Boolean, endsFront: Boolean, facingRight: Boolean,
    ): Boolean {
        val start = c.time
        val ok = push(
            c, Segment(
                start, start + ms.coerceAtLeast(1), Kind.STAY, intent, animation, c.x, c.y, c.x, c.y, facingRight, facing,
                seated, c.walked, spot, activity, speech, offstage = false, socialId = socialId,
                interruptible = interruptible, usesBench = usesBench, toNode = c.node, localOffsetMs = localOffsetMs,
                endsFront = endsFront, endsSeated = endsSeated,
            ),
        )
        // Quem está parado num ponto o ocupa de fato, mesmo que a chegada tenha atrasado além da
        // estimativa: a reserva acompanha a parada real (o banco tem reserva própria).
        val here = c.node
        if (here != null && here == spot && here != LeisureNpcSpot.BENCH_SEAT && spot(here).let { it.capacity == 1 && !it.offstage }) {
            reservation.reserve(here, c.npcId, start, c.time + STAY_HOLD_MARGIN_MS)
        }
        return ok
    }

    private fun idleHere(c: Cursor, ms: Long) {
        if (c.offstage) offstageWait(c, ms)
        else stay(c, ms, LeisureNpcIntent.IDLE, NpcAnimation.IDLE, if (c.front) Facing.FRONT else Facing.SIDE, c.node, null)
    }

    private fun offstageWait(c: Cursor, ms: Long) {
        append(
            c, Segment(
                c.time, c.time + ms.coerceAtLeast(1), Kind.STAY, LeisureNpcIntent.EXITING, NpcAnimation.IDLE,
                c.x, c.y, c.x, c.y, c.facingRight, Facing.SIDE, false, c.walked, null, LeisureActivity.EXIT, offstage = true,
            ),
        )
    }

    /** Vira de perfil para o outro lado (180°), nunca de um quadro para o outro. */
    private fun turnSide(c: Cursor, right: Boolean, intent: LeisureNpcIntent, activity: LeisureActivity?): Boolean {
        if (c.front) return turnOutOfFront(c, right, intent, activity)
        if (c.facingRight == right) return true
        return push(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN, intent,
                if (right) NpcAnimation.TURN_RIGHT else NpcAnimation.TURN_LEFT,
                c.x, c.y, c.x, c.y, right, null, false, c.walked, c.node, activity, interruptible = false,
                toNode = c.node, endsFront = false, endsSeated = false,
            ),
        )
    }

    /** Perfil → frente (sentar, fotografar). */
    private fun turnIntoFront(c: Cursor, intent: LeisureNpcIntent, activity: LeisureActivity?, usesBench: Boolean = false): Boolean {
        if (c.front) return true
        return push(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN_FRONT_IN, intent, NpcAnimation.TURN_LEFT,
                c.x, c.y, c.x, c.y, c.facingRight, Facing.FRONT, false, c.walked, c.node, activity, interruptible = false,
                usesBench = usesBench, toNode = c.node, endsFront = true, endsSeated = false,
            ),
        )
    }

    /** Frente → perfil para o lado em que vai andar. */
    private fun turnOutOfFront(c: Cursor, right: Boolean, intent: LeisureNpcIntent, activity: LeisureActivity?, usesBench: Boolean = false): Boolean {
        if (!c.front) return turnSide(c, right, intent, activity)
        return push(
            c, Segment(
                c.time, c.time + NpcPoseLibrary.TURN_MS, Kind.TURN_FRONT_OUT, intent, NpcAnimation.TURN_RIGHT,
                c.x, c.y, c.x, c.y, right, Facing.FRONT, false, c.walked, c.node, activity, interruptible = false,
                usesBench = usesBench, toNode = c.node, endsFront = false, endsSeated = false,
            ),
        )
    }

    private fun walkLeg(
        c: Cursor, tx: Int, ty: Int, fromNode: LeisureNpcSpot?, toNode: LeisureNpcSpot?,
        intent: LeisureNpcIntent, activity: LeisureActivity?, usesBench: Boolean,
    ): Boolean {
        if (tx == c.x && ty == c.y) { c.node = toNode; return true }
        val right = if (tx != c.x) tx > c.x else c.facingRight
        if (c.front && !turnOutOfFront(c, right, intent, activity, usesBench)) return false
        if (right != c.facingRight && !turnSide(c, right, intent, activity)) return false
        val distance = max(abs(tx - c.x), abs(ty - c.y))
        return push(
            c, Segment(
                c.time, c.time + distance * c.msPerPixel, Kind.WALK, intent, NpcAnimation.WALK,
                c.x, c.y, tx, ty, right, null, false, c.walked, null, activity, usesBench = usesBench,
                fromNode = fromNode, toNode = toNode, endsFront = false, endsSeated = false,
            ),
        )
    }

    /** Anda pelo grafo até [target]; reconecta ao grafo se estiver no meio de um trecho. */
    private fun walkTo(c: Cursor, target: LeisureNpcSpot, intent: LeisureNpcIntent, activity: LeisureActivity?, usesBench: Boolean = false): Boolean {
        var start = c.node
        if (start == null) {
            // Parou no meio de um trecho (cedeu o banco): volta para a ponta de onde veio.
            val back = c.legFrom ?: LeisureNavigationGraph.nearestNode(variant, c.x, c.y)
            val s = spot(back)
            if (!walkLeg(c, s.x, s.floorY, c.legTo, back, intent, activity, usesBench)) return false
            start = back
        }
        val route = LeisureNavigationGraph.route(start, target)
        for ((a, b) in route.zipWithNext()) {
            val s = spot(b)
            val bench = usesBench || b == LeisureNpcSpot.BENCH_SEAT || b == LeisureNpcSpot.BENCH_APPROACH
            if (!walkLeg(c, s.x, s.floorY, a, b, intent, activity, bench)) return false
        }
        c.node = target
        return true
    }

    private fun estimateRouteMs(c: Cursor, target: LeisureNpcSpot): Long {
        val start = c.node ?: LeisureNavigationGraph.nearestNode(variant, c.x, c.y)
        var x = c.x; var y = c.y; var total = 0L
        val first = spot(start)
        total += max(abs(first.x - x), abs(first.floorY - y)) * c.msPerPixel
        x = first.x; y = first.floorY
        for (node in LeisureNavigationGraph.route(start, target).drop(1)) {
            val s = spot(node)
            total += max(abs(s.x - x), abs(s.floorY - y)) * c.msPerPixel + NpcPoseLibrary.TURN_MS
            x = s.x; y = s.floorY
        }
        return total + NpcPoseLibrary.TURN_MS * 2
    }

    private fun faceAttraction(c: Cursor, s: LeisureSpot, intent: LeisureNpcIntent, activity: LeisureActivity): Boolean {
        val ax = s.attractionX ?: return true
        return turnSide(c, ax >= c.x, intent, activity)
    }

    // ───────────── Atividades ─────────────

    private val stopSpots = PATH_STOPS

    /** Ponto de parada livre durante a chegada e a atividade; evita voltar para onde acabou de estar. */
    private fun chooseStop(c: Cursor, holdMs: Long, allowCurrent: Boolean): LeisureNpcSpot? {
        val rotation = (rand(c, 11) * stopSpots.size).toInt()
        val ordered = stopSpots.indices.map { stopSpots[(it + rotation) % stopSpots.size] }
        // Sem vai-e-vem: nunca volta para as duas últimas paradas do caminho. Atividades que podem
        // ficar no lugar (celular, alongar) preferem o ponto atual; explorar sempre vai para um ponto novo.
        val here = c.node?.takeIf { it in stopSpots }
        val others = ordered.filter { it != here && it !in c.recentPathStops }
        val candidates = if (allowCurrent && here != null) listOf(here) + others else others
        for (s in candidates) {
            val arrive = c.time + if (s == c.node) 0 else estimateRouteMs(c, s)
            if (reservation.isFree(s, arrive - 400, arrive + holdMs + 1_500, c.npcId)) return s
        }
        return null
    }

    private fun reserveStop(c: Cursor, s: LeisureNpcSpot, holdMs: Long) {
        val arrive = c.time + if (s == c.node) 0 else estimateRouteMs(c, s)
        reservation.reserve(s, c.npcId, c.time, arrive + holdMs + 1_500)
    }

    /** Explorar / passear / alongar: anda até um ponto livre do caminho e age lá. */
    private fun planStop(c: Cursor, activity: LeisureActivity): Boolean {
        if (c.seated && !leaveBench(c, activity)) return true
        val hold = LeisureNpcBrain.holdMs(activity, false, c.npcId, seed, c.decisions)
        val stop = chooseStop(c, hold, allowCurrent = activity == LeisureActivity.STRETCH) ?: return false
        reserveStop(c, stop, hold)
        val moving = if (activity == LeisureActivity.STROLL) LeisureNpcIntent.RETURNING else LeisureNpcIntent.WALKING
        if (!walkTo(c, stop, moving, activity)) return true
        c.lastStop = stop
        when (activity) {
            LeisureActivity.STRETCH -> stay(c, hold, LeisureNpcIntent.RELAXING, NpcAnimation.STRETCH, Facing.SIDE, stop, activity)
            else -> {
                // Para, olha em volta e às vezes vira para o lado de onde veio.
                val look = if (rand(c, 17) < 0.5f) NpcAnimation.LOOK else NpcAnimation.IDLE
                stay(c, hold, LeisureNpcIntent.IDLE, look, Facing.SIDE, stop, activity)
            }
        }
        return true
    }

    private fun observeOptions(c: Cursor): List<LeisureNpcSpot> {
        val calm = c.spec.personality == LeisurePersonality.CALM
        val options = if (calm) listOf(LeisureNpcSpot.NATURE_VIEW) else listOf(LeisureNpcSpot.NATURE_VIEW, LeisureNpcSpot.VIEWPOINT)
        return options.filter { s ->
            val arrive = c.time + estimateRouteMs(c, s)
            reservation.isFree(s, arrive - 400, arrive + 7_000, c.npcId)
        }
    }

    /** Observar ou fotografar a atração da variante (ou a vista do mirante). */
    private fun planObserve(c: Cursor, photo: Boolean): Boolean {
        val options = observeOptions(c)
        if (options.isEmpty()) return false
        val target = if (options.size == 1 || rand(c, 23) < 0.62f) options.first() else options.last()
        val activity = if (photo) LeisureActivity.PHOTO else LeisureActivity.OBSERVE
        val hold = LeisureNpcBrain.holdMs(activity, false, c.npcId, seed, c.decisions)
        val comment = !photo && target == LeisureNpcSpot.NATURE_VIEW && rand(c, 29) < 0.55f
        val total = hold + if (comment) COMMENT_MS else 0
        if (c.seated && !leaveBench(c, activity)) return true
        val arrive = c.time + estimateRouteMs(c, target)
        reservation.reserve(target, c.npcId, c.time, arrive + total + 1_500)
        if (!walkTo(c, target, LeisureNpcIntent.WALKING, activity)) return true
        c.lastStop = target
        val s = spot(target)
        if (photo) {
            turnIntoFront(c, LeisureNpcIntent.PHOTOGRAPHING, activity)
            // Alinha o ciclo da pose: enquadra, ergue, clica e abaixa dentro do segmento.
            val offset = Math.floorMod(-c.spec.slotSeed * 101L, LeisureNpcBrain.PHOTO_MS)
            stay(c, hold, LeisureNpcIntent.PHOTOGRAPHING, NpcAnimation.STAND_PHOTO, Facing.FRONT, target, activity, localOffsetMs = offset)
            c.photoReadyAt = c.time + 25_000 + (rand(c, 31) * 20_000).toLong()
        } else {
            faceAttraction(c, s, LeisureNpcIntent.OBSERVING, activity)
            stay(c, hold, LeisureNpcIntent.OBSERVING, NpcAnimation.LOOK_WINDOW, Facing.SIDE, target, activity)
            if (comment && speechFree(c.time, c.time + COMMENT_MS)) {
                val line = LeisureSpeechLibrary.line(LeisureSpeechLibrary.natureComment(variant), c.npcId, seed, c.decisions)
                addSpeech(c.time, c.time + COMMENT_MS)
                stay(c, COMMENT_MS, LeisureNpcIntent.OBSERVING, NpcAnimation.TALK, Facing.SIDE, target, activity, speech = line)
            }
        }
        return true
    }

    private fun planPhone(c: Cursor) {
        val hold = LeisureNpcBrain.holdMs(LeisureActivity.PHONE, c.seated, c.npcId, seed, c.decisions)
        if (c.seated) {
            reservation.reserve(LeisureNpcSpot.BENCH_SEAT, c.npcId, c.time, c.time + hold + 200)
            benchPlan(c) {
                stay(c, hold, LeisureNpcIntent.USING_PHONE, NpcAnimation.SIT_PHONE, Facing.FRONT, LeisureNpcSpot.BENCH_SEAT, LeisureActivity.PHONE, usesBench = true)
            }
            return
        }
        val stop = chooseStop(c, hold, allowCurrent = true)
        if (stop == null) { idleHere(c, 1_200); return }
        reserveStop(c, stop, hold)
        if (!walkTo(c, stop, LeisureNpcIntent.WALKING, LeisureActivity.PHONE)) return
        c.lastStop = stop
        stay(c, hold, LeisureNpcIntent.USING_PHONE, NpcAnimation.STAND_PHONE, Facing.SIDE, stop, LeisureActivity.PHONE)
    }

    /**
     * Ler ou descansar no banco. Se o Hoodie reservar o banco no meio do plano, o plano é
     * cortado ali (o que está acontecendo termina em [GRACE_MS]) e o visitante levanta e sai.
     */
    private fun planBench(c: Cursor, activity: LeisureActivity) {
        val hold = LeisureNpcBrain.holdMs(activity, true, c.npcId, seed, c.decisions)
        val walkMs = if (c.seated) 0 else estimateRouteMs(c, LeisureNpcSpot.BENCH_SEAT) + NpcPoseLibrary.TURN_MS + SIT_MS
        reservation.reserve(LeisureNpcSpot.BENCH_SEAT, c.npcId, c.time, c.time + walkMs + hold + 400)
        benchPlan(c) {
            if (!c.seated) {
                if (!walkTo(c, LeisureNpcSpot.BENCH_SEAT, LeisureNpcIntent.WALKING, activity, usesBench = true)) return@benchPlan
                if (!turnIntoFront(c, LeisureNpcIntent.SITTING, activity, usesBench = true)) return@benchPlan
                if (!stay(c, SIT_MS, LeisureNpcIntent.SITTING, NpcAnimation.SIT, Facing.FRONT, LeisureNpcSpot.BENCH_SEAT, activity,
                        interruptible = false, usesBench = true, seated = false, endsSeated = true)) return@benchPlan
            }
            val (intent, animation) = when {
                activity == LeisureActivity.READ -> LeisureNpcIntent.READING to NpcAnimation.READ_DOCUMENT
                rand(c, 37) < 0.45f -> LeisureNpcIntent.RELAXING to NpcAnimation.SIT_DRINK
                else -> LeisureNpcIntent.SITTING to NpcAnimation.SIT_LOOK
            }
            stay(c, hold, intent, animation, Facing.FRONT, LeisureNpcSpot.BENCH_SEAT, activity, usesBench = true, seated = true)
        }
    }

    /** Executa um plano que usa o banco com prazo = início da próxima reserva do Hoodie. */
    private inline fun benchPlan(c: Cursor, plan: () -> Unit) {
        val start = c.time
        val claim = reservation.firstHoodieClaimStartIn(start, Long.MAX_VALUE / 4)
        deadline = claim?.plus(GRACE_MS) ?: Long.MAX_VALUE
        deadlineHit = false
        try { plan() } finally {
            val cut = deadlineHit
            deadline = Long.MAX_VALUE
            deadlineHit = false
            if (cut) yieldBench(c)
        }
    }

    /** Cede o banco: termina o gesto, levanta, vira, desce do assento e vai para um ponto livre. */
    private fun yieldBench(c: Cursor) {
        reservation.releaseAt(LeisureNpcSpot.BENCH_SEAT, c.npcId, c.time)
        if (c.seated) leaveBench(c, LeisureActivity.STROLL)
        val options = listOf(LeisureNpcSpot.PATH_CENTER, LeisureNpcSpot.PATH_LEFT, LeisureNpcSpot.PATH_RIGHT)
            .filter { reservation.isFree(it, c.time, c.time + 12_000, c.npcId) }
        val stop = options.firstOrNull { it !in c.recentPathStops } ?: options.firstOrNull()
        // Sem ponto livre no caminho: em vez de parar em cima de alguém, segue até a borda e sai.
        if (stop == null) { planExit(c); return }
        reservation.reserve(stop, c.npcId, c.time, c.time + 10_000)
        walkTo(c, stop, LeisureNpcIntent.RETURNING, LeisureActivity.STROLL)
        c.lastStop = stop
        stay(c, 1_400, LeisureNpcIntent.IDLE, NpcAnimation.LOOK, Facing.SIDE, stop, LeisureActivity.STROLL)
    }

    /** Levantar do banco (nunca de um quadro para o outro) e descer para a frente dele. */
    private fun leaveBench(c: Cursor, activity: LeisureActivity): Boolean {
        if (!c.seated) return true
        if (!stay(c, STAND_UP_MS, LeisureNpcIntent.RETURNING, NpcAnimation.STAND_UP, Facing.FRONT, LeisureNpcSpot.BENCH_SEAT, activity,
                interruptible = false, usesBench = true, seated = false, endsSeated = false)) return false
        val approach = spot(LeisureNpcSpot.BENCH_APPROACH)
        if (!walkLeg(c, approach.x, approach.floorY, LeisureNpcSpot.BENCH_SEAT, LeisureNpcSpot.BENCH_APPROACH, LeisureNpcIntent.RETURNING, activity, usesBench = true)) return false
        reservation.releaseAt(LeisureNpcSpot.BENCH_SEAT, c.npcId, c.time)
        c.node = LeisureNpcSpot.BENCH_APPROACH
        return true
    }

    /** Sai pela borda mais natural e fica fora de cena um tempo antes de voltar. */
    private fun planExit(c: Cursor) {
        if (c.seated) leaveBench(c, LeisureActivity.EXIT)
        val right = if (abs(c.x - 120) < 30) rand(c, 41) < 0.5f else c.x > 120
        val entrance = if (right) LeisureNpcSpot.ENTRANCE_RIGHT else LeisureNpcSpot.ENTRANCE_LEFT
        walkTo(c, entrance, LeisureNpcIntent.EXITING, LeisureActivity.EXIT)
        // Fora de cena, longe o bastante para não ser desenhado.
        c.x = if (right) LeisureNavigationGraph.OFFSTAGE_RIGHT_X else LeisureNavigationGraph.OFFSTAGE_LEFT_X
        c.node = null; c.legFrom = null; c.legTo = null
        offstageWait(c, 6_000 + (rand(c, 43) * 14_000).toLong())
    }

    /** Volta para a cena por uma das bordas (posição fora da tela, então nada "teletransporta" à vista). */
    private fun reenter(c: Cursor) {
        val fromLeft = when {
            c.decisions == 0L -> c.spec.start != LeisureStart.RIGHT
            else -> rand(c, 47) < 0.5f
        }
        val entrance = if (fromLeft) LeisureNpcSpot.ENTRANCE_LEFT else LeisureNpcSpot.ENTRANCE_RIGHT
        val first = if (fromLeft) LeisureNpcSpot.PATH_LEFT else LeisureNpcSpot.PATH_RIGHT
        // Primeiro ponto ocupado por outro visitante: espera um pouco fora de cena antes de entrar.
        if (!reservation.isFree(first, c.time, c.time + REENTER_WINDOW_MS, c.npcId)) {
            offstageWait(c, 2_500)
            return
        }
        val e = spot(entrance)
        c.x = e.x; c.y = e.floorY; c.node = entrance; c.offstage = false; c.front = false; c.seated = false
        c.facingRight = fromLeft
        c.visitCount = 0
        c.visitLength = c.spec.personality.visitLength.let { it.first + (rand(c, 53) * (it.last - it.first + 1)).toInt() }
        reservation.reserve(first, c.npcId, c.time, c.time + estimateRouteMs(c, first) + 2_500)
        walkTo(c, first, LeisureNpcIntent.WALKING, LeisureActivity.EXPLORE)
        c.lastStop = first
        stay(c, 1_200, LeisureNpcIntent.IDLE, NpcAnimation.LOOK, Facing.SIDE, first, LeisureActivity.EXPLORE)
        c.decisions++
    }

    // ───────────── Encontros (P1) ─────────────

    private fun partnerFor(a: Cursor): Cursor? {
        if (a.offstage || a.seated || a.visitCount >= a.visitLength - 1) return null
        if (social.busy(a.time, a.time + 25_000)) return null
        return cursors.filter { b ->
            b !== a && b.spec.personality != LeisurePersonality.CALM && !b.offstage && !b.seated &&
                b.time >= a.time && b.time <= a.time + LeisureSocialCoordinator.MAX_PARTNER_WAIT_MS &&
                b.socialReadyAt <= b.time && b.visitCount < b.visitLength - 1 &&
                timelines[b.index].lastOrNull()?.activity != LeisureActivity.EXIT
        }.minByOrNull { it.time }
    }

    private fun planSocial(a: Cursor): Boolean {
        val b = partnerFor(a) ?: return false
        val aLeft = a.x <= b.x
        val aSpot = if (aLeft) LeisureNpcSpot.SOCIAL_LEFT else LeisureNpcSpot.SOCIAL_RIGHT
        val bSpot = if (aLeft) LeisureNpcSpot.SOCIAL_RIGHT else LeisureNpcSpot.SOCIAL_LEFT
        val index = a.decisions * 31 + b.decisions
        // Às vezes o encontro vira "olha essa vista": os dois param lado a lado diante da atração.
        val sharedView = rand(a, 77) < SHARED_VIEW_CHANCE &&
            stories.canStart(SceneStoryEventType.LEISURE_SHARED_VIEW, a.time, listOf(a.npcId, b.npcId))
        val lines = social.script(a.npcId, b.npcId, index).let { script ->
            // Mesmo roteiro de quatro falas: o assunto vira o comentário sobre a vista.
            if (!sharedView) script else listOf(script[0], script[1],
                LeisureSocialCoordinator.Line(true, VIEW_LINES[Math.floorMod(index.toInt(), VIEW_LINES.size)], 1_600), script[3])
        }
        val viewExtraMs = if (sharedView) VIEW_LOOK_MS + 4 * com.hoodie.app.pixel.npc.NpcPoseLibrary.TURN_MS else 0L
        val talkMs = lines.sumOf { it.durationMs + LeisureSocialCoordinator.LINE_GAP_MS } + viewExtraMs
        val arriveA = a.time + estimateRouteMs(a, aSpot)
        val arriveB = b.time + estimateRouteMs(b, bSpot)
        val holdUntil = max(arriveA, arriveB) + talkMs + 4_000
        if (!reservation.isFree(aSpot, a.time, holdUntil, a.npcId) || !reservation.isFree(bSpot, b.time, holdUntil, b.npcId)) return false
        reservation.reserve(aSpot, a.npcId, a.time, holdUntil + LeisureSocialCoordinator.RELEASE_MS)
        reservation.reserve(bSpot, b.npcId, b.time, holdUntil + LeisureSocialCoordinator.RELEASE_MS)
        // Os dois se aproximam (o parceiro só sai quando termina o que estava fazendo).
        walkTo(a, aSpot, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
        turnSide(a, aLeft, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
        walkTo(b, bSpot, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
        turnSide(b, !aLeft, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
        var meet = max(a.time, b.time) + 300
        meet = firstSpeechFreeFrom(meet, talkMs)
        val encounter = social.register(a.npcId, b.npcId, meet, meet + talkMs)
        // Quem chegou antes espera virado para o outro.
        listOf(a, b).forEach { c ->
            if (c.time < meet) stay(c, meet - c.time, LeisureNpcIntent.SOCIALIZING, NpcAnimation.IDLE, Facing.SIDE,
                if (c === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
        }
        val viewRight = (spot(LeisureNpcSpot.NATURE_VIEW).attractionX ?: 239) > (a.x + b.x) / 2
        var viewStart = -1L
        var viewEnd = -1L
        for ((i, line) in lines.withIndex()) {
            val speaker = if (line.byInitiator) a else b
            val listener = if (line.byInitiator) b else a
            if (sharedView && i == 2) {
                // Lado a lado, virados para a atração.
                viewStart = max(a.time, b.time)
                listOf(a, b).forEach { c ->
                    if (c.time < viewStart) stay(c, viewStart - c.time, LeisureNpcIntent.SOCIALIZING, NpcAnimation.IDLE, Facing.SIDE,
                        if (c === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
                    turnSide(c, viewRight, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
                }
            }
            if (sharedView && i == 3) {
                // Ficam um tempo olhando juntos e voltam a se olhar para a despedida.
                val lookFrom = max(a.time, b.time)
                listOf(a, b).forEach { c ->
                    stay(c, lookFrom - c.time + VIEW_LOOK_MS, LeisureNpcIntent.SOCIALIZING, NpcAnimation.LOOK_WINDOW, Facing.SIDE,
                        if (c === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
                }
                viewEnd = max(a.time, b.time)
                turnSide(a, aLeft, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
                turnSide(b, !aLeft, LeisureNpcIntent.SOCIALIZING, LeisureActivity.SOCIAL)
            }
            addSpeech(speaker.time, speaker.time + line.durationMs)
            stay(speaker, line.durationMs, LeisureNpcIntent.SOCIALIZING, NpcAnimation.TALK, Facing.SIDE,
                if (speaker === a) aSpot else bSpot, LeisureActivity.SOCIAL, speech = line.text, socialId = encounter.id)
            stay(listener, line.durationMs, LeisureNpcIntent.SOCIALIZING, NpcAnimation.IDLE, Facing.SIDE,
                if (listener === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
            listOf(a, b).forEach { c ->
                stay(c, LeisureSocialCoordinator.LINE_GAP_MS, LeisureNpcIntent.SOCIALIZING, NpcAnimation.IDLE, Facing.SIDE,
                    if (c === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
            }
        }
        if (sharedView) {
            // O encontro foi reservado com folga para as viradas: os dois ficam juntos até o fim dele,
            // nunca andando dentro da janela registrada.
            listOf(a, b).forEach { c ->
                if (c.time < meet + talkMs) stay(c, meet + talkMs - c.time, LeisureNpcIntent.SOCIALIZING, NpcAnimation.IDLE, Facing.SIDE,
                    if (c === a) aSpot else bSpot, LeisureActivity.SOCIAL, socialId = encounter.id)
            }
            val end = max(a.time, b.time)
            stories.record(com.hoodie.app.pixel.npc.venue.VenueStoryRecord(SceneStoryEvent(
                id = "leisure-view:${key.daySeed}:${encounter.id}:$meet", type = SceneStoryEventType.LEISURE_SHARED_VIEW, scene = SceneId.LEISURE,
                participants = listOf(a.npcId, b.npcId), requiredSpots = listOf(aSpot.name, bSpot.name),
                startAt = meet, durationMs = (end - meet).coerceAtLeast(1), priority = 1, cooldownMs = SHARED_VIEW_COOLDOWN_MS,
                interactAt = viewStart.coerceIn(meet, end), resolveAt = viewEnd.coerceIn(viewStart.coerceIn(meet, end), end),
            )))
        }
        a.lastStop = aSpot; b.lastStop = bSpot
        a.socialReadyAt = a.time + social.cooldownMs(a.npcId, index)
        b.socialReadyAt = b.time + social.cooldownMs(b.npcId, index + 1)
        // O parceiro também viveu um encontro: entra no histórico dele.
        b.recent.addLast(LeisureActivity.SOCIAL)
        if (b.recent.size > 6) b.recent.removeFirst()
        b.decisions++
        b.visitCount++
        return true
    }

    // ───────────── Um balão por vez ─────────────

    private fun speechFree(from: Long, until: Long) = speech.none { it[0] < until && from < it[1] }

    private fun addSpeech(from: Long, until: Long) { speech += longArrayOf(from, until) }

    private fun firstSpeechFreeFrom(from: Long, duration: Long): Long {
        var t = from
        while (true) {
            val clash = speech.filter { it[0] < t + duration && t < it[1] }.maxOfOrNull { it[1] } ?: return t
            t = clash + LeisureSocialCoordinator.LINE_GAP_MS
        }
    }

    /** Microevento em [timeMs] (null = nenhum). */
    @Synchronized
    fun storySnapshotAt(timeMs: Long): com.hoodie.app.pixel.npc.events.SceneStorySnapshot? {
        snapshotAt(timeMs)
        return stories.snapshotAt(timeMs.coerceAtLeast(0L))
    }

    /** Microeventos já planejados até [timeMs] (testes e folhas de revisão). */
    @Synchronized
    internal fun storiesUntil(timeMs: Long): List<com.hoodie.app.pixel.npc.venue.VenueStoryRecord> {
        snapshotAt(timeMs)
        return stories.all().filter { it.event.startAt <= timeMs }
    }

    companion object {
        const val SHARED_VIEW_CHANCE = 0.4f
        const val SHARED_VIEW_COOLDOWN_MS = 120_000L
        const val VIEW_LOOK_MS = 2_200L
        val VIEW_LINES = listOf("Olha essa vista!", "Que lugar bonito!")
        const val HOODIE_ID = "hoodie"
        private const val SCENE_W = 240
        /** Spot BENCH do Hoodie (LeisureScene). */
        private const val HOODIE_BENCH_X = 122
        private const val HOODIE_BENCH_Y = 228
        /** Mirante do Hoodie: para onde os visitantes olham enquanto ele fotografa. */
        private const val HOODIE_VIEW_X = 196
        const val GRACE_MS = 300L
        const val SIT_MS = 640L
        const val STAND_UP_MS = 640L
        const val COMMENT_MS = 1_600L
        const val REACTION_WINDOW_MS = 9_000L
        private const val STAY_HOLD_MARGIN_MS = 600L
        private const val REENTER_WINDOW_MS = 8_000L
        private val PATH_STOPS =listOf(LeisureNpcSpot.PATH_LEFT, LeisureNpcSpot.PATH_CENTER, LeisureNpcSpot.PATH_RIGHT)
        private const val MIN_LIVE_ATTRACTION = 0.05f
        private const val TIMELINE_LIMIT = 2_048
        private const val TIMELINE_PRUNE = 1_024
        private const val MAX_STEPS_PER_QUERY = 2_000_000
        private const val MAX_CLAIMS = 256
    }
}
