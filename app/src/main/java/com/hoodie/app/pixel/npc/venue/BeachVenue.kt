package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.events.SceneStoryEvent
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Ambiente da praia (plano, seção 5): ondas, nuvens, gaivotas e guarda-sol são cíclicos e baratos
 * (função do tempo); a bola tem um dono lógico — em voo entre as mãos, ou na mão de quem pegou.
 */
data class BeachAmbientState(
    override val timeMs: Long,
    val wavePhase: Float,
    val cloudOffset: Float,
    val seagullPhase: Float,
    val umbrellaSway: Int,
    val ballOwnerId: String?,
    val ballX: Int,
    val ballY: Int,
    val ballVisible: Boolean,
    val vendorOpen: Boolean,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.BEACH

    companion object {
        fun atTime(t: Long, owner: String?, x: Int, y: Int, visible: Boolean, open: Boolean) = BeachAmbientState(
            timeMs = t,
            wavePhase = Math.floorMod(t, WAVE_MS) / WAVE_MS.toFloat(),
            cloudOffset = Math.floorMod(t / 90, 480L).toFloat(),
            seagullPhase = Math.floorMod(t, GULL_MS) / GULL_MS.toFloat(),
            umbrellaSway = (sin(t / 1_100.0 * PI) * 1.4).toInt(),
            ballOwnerId = owner, ballX = x, ballY = y, ballVisible = visible, vendorOpen = open,
        )

        const val WAVE_MS = 5_600L
        const val GULL_MS = 23_000L
    }
}

/**
 * Praia (Sprint 3), reaproveitando a lógica do Passeio 2.0 (atividades ponderadas, reservas,
 * encontros) com perfis próprios: dupla de jogadores (BALL_PLAYER), turista (BEACH_WALKER),
 * banhista (SUNBATHER) e sorveteiro (ICE_CREAM_VENDOR).
 *
 * A bola: só quem está com ela arremessa (THROW_BALL); quem recebe só agarra (CATCH_BALL) quando
 * ela chega. A trajetória vai da mão de um à mão do outro, por cima da areia entre os dois.
 */
internal object BeachVenue : VenueBlueprint(SceneId.BEACH) {
    const val SERVICE = "sorvete"
    const val CONE = "cone"

    const val EDGE_L = "edge_l"
    const val EDGE_R = "edge_r"
    const val VIEW_L = "view_l"
    const val SHORE_MID = "shore_mid"
    const val VIEW_R = "view_r"
    const val R_LANE = "r_lane"
    const val R_LOW = "r_low"
    const val BUY = "buy"
    const val BUY_QUEUE = "buy_queue"
    const val VENDOR = "vendor"
    const val SUN_SEAT = "sun_seat"
    const val A1 = "court_a1"
    const val A2 = "court_a2"
    const val B1 = "court_b1"
    const val B2 = "court_b2"
    const val PHOTO_L = "photo_l"
    const val PHOTO_R = "photo_r"

    /** Pontos do Hoodie (BeachScene). */
    const val HOODIE_SHORE_X = 120
    const val HOODIE_SHORE_Y = 182
    const val HOODIE_UMBRELLA_X = 200
    const val HOODIE_UMBRELLA_Y = 300
    const val HOODIE_ICE_CREAM_X = 90
    const val HOODIE_ICE_CREAM_Y = 308

    /** Carrinho (frente à esquerda): o tampo fica na cintura do sorveteiro; o obstáculo é a frente dele. */
    const val CART_X0 = 8
    const val CART_X1 = 56
    const val CART_TOP = 278
    const val CART_Y0 = 298
    const val CART_Y1 = 312
    const val SEA_Y = 158

    /** Altura das mãos acima dos pés na hora de jogar/agarrar a bola. */
    const val HAND_DY = 30
    const val BALL_ARC = 24

    private val graph = VenueNavigationGraph(
        "praia",
        spots = listOf(
            VenueSpot(EDGE_L, -40, 174, capacity = 4, offstage = true),
            VenueSpot(EDGE_R, 280, 174, capacity = 4, offstage = true),
            VenueSpot(VIEW_L, 34, 176, interaction = "beira-mar"),
            VenueSpot(SHORE_MID, 120, 168, capacity = 2),
            VenueSpot(VIEW_R, 204, 176, interaction = "beira-mar"),
            VenueSpot(R_LANE, 176, 214, capacity = 2),
            VenueSpot(R_LOW, 162, 272, capacity = 2),
            VenueSpot(BUY, 72, 296, interaction = "sorvete"),
            VenueSpot(BUY_QUEUE, 102, 278, interaction = "fila do sorvete"),
            VenueSpot(VENDOR, 32, 292, interaction = "carrinho"),
            VenueSpot(SUN_SEAT, 206, 204, interaction = "cadeira de praia"),
            VenueSpot(A1, 60, 236, interaction = "quadra"),
            VenueSpot(A2, 54, 248, interaction = "quadra"),
            VenueSpot(B1, 132, 236, interaction = "quadra"),
            VenueSpot(B2, 138, 248, interaction = "quadra"),
            // Meio da quadra: a dupla se junta ali para a foto (microevento BEACH_SHARED_PHOTO).
            VenueSpot(PHOTO_L, 82, 240, interaction = "foto"),
            VenueSpot(PHOTO_R, 110, 240, interaction = "foto"),
        ),
        edges = listOf(
            EDGE_L to VIEW_L, VIEW_L to SHORE_MID, SHORE_MID to VIEW_R, VIEW_R to EDGE_R,
            VIEW_R to R_LANE, R_LANE to SUN_SEAT, R_LANE to R_LOW, R_LOW to BUY_QUEUE, BUY_QUEUE to BUY,
            A1 to A2, B1 to B2, A1 to PHOTO_L, PHOTO_L to PHOTO_R, PHOTO_R to B1,
        ),
        obstacles = listOf(
            VenueObstacle("mar", 0, 0, 239, SEA_Y),
            VenueObstacle("carrinho de sorvete", CART_X0, CART_Y0, CART_X1, CART_Y1),
            VenueObstacle("cadeira do Hoodie", 186, 302, 216, 314),
        ),
    )

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        val tourist = VenueActorSpec(
            NpcCharacterRegistry.RABBIT_WALKER,
            VenueNpcProfile("beach_tourist", VenueRole.BEACH_WALKER,
                mapOf(VenueAction.PHOTO to 25, VenueAction.OBSERVE to 25, VenueAction.STROLL to 25, VenueAction.BUY_ICE_CREAM to 25), 0f, 3_000, 6_000),
            slotSeed = 401, start = VenueStart.OFFSTAGE, startDelayMs = 2_000L + Math.floorMod(key.daySeed, 5) * 700L,
        )
        val sunbather = VenueActorSpec(
            NpcCharacterRegistry.CAT_GUEST,
            VenueNpcProfile("sunbather", VenueRole.SUNBATHER,
                mapOf(VenueAction.SUNBATHE to 55, VenueAction.PHONE to 25, VenueAction.BUY_ICE_CREAM to 20), 0f, 6_000, 12_000),
            slotSeed = 409, start = VenueStart.INSIDE, startSpot = SUN_SEAT,
        )
        if (key.night) return listOf(tourist, sunbather)
        val playerA = VenueActorSpec(
            NpcCharacterRegistry.DOG_WORKER,
            VenueNpcProfile("ball_player_a", VenueRole.BALL_PLAYER, mapOf(VenueAction.PLAY_BALL to 70, VenueAction.SOCIAL to 15, VenueAction.REST to 15), 1f, 300, 900),
            slotSeed = 419, start = VenueStart.INSIDE, startSpot = A1,
        )
        val playerB = VenueActorSpec(
            NpcCharacterRegistry.RACCOON_COMMUTER,
            VenueNpcProfile("ball_player_b", VenueRole.BALL_PLAYER, mapOf(VenueAction.PLAY_BALL to 70, VenueAction.SOCIAL to 15, VenueAction.REST to 15), 1f, 300, 900),
            slotSeed = 421, start = VenueStart.INSIDE, startSpot = B1,
        )
        val vendor = VenueActorSpec(
            NpcCharacterRegistry.BULLDOG_EXEC,
            VenueNpcProfile("ice_cream_vendor", VenueRole.ICE_CREAM_VENDOR, mapOf(VenueAction.SERVE to 50, VenueAction.WAIT to 50), 0f, 1_600, 3_200),
            slotSeed = 431, start = VenueStart.POST, startSpot = VENDOR,
        )
        return listOf(playerA, playerB, tourist, sunbather, vendor)
    }

    override fun initialize(s: VenueNpcSession, c: VenueCursor) {
        super.initialize(s, c)
        when (c.role) {
            // A banhista já está deitada na cadeira quando a cena abre.
            VenueRole.SUNBATHER -> { c.seated = true; c.front = true }
            // Os jogadores começam frente a frente; A abre com a bola.
            VenueRole.BALL_PLAYER -> c.facingRight = c.npcId == PLAYER_A
            VenueRole.ICE_CREAM_VENDOR -> c.front = true
            else -> Unit
        }
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        when (c.role) {
            VenueRole.BALL_PLAYER -> pair(s, c)
            VenueRole.ICE_CREAM_VENDOR -> vendor(s, c)
            VenueRole.SUNBATHER -> sunbather(s, c)
            else -> tourist(s, c)
        }
        c.decisions++
    }

    private fun vendorOpen(s: VenueNpcSession) = s.allCursors().any { it.role == VenueRole.ICE_CREAM_VENDOR }

    // ───── Dupla da bola ─────

    private const val PLAYER_A = "ball_player_a"

    /** Quem está com a bola agora (o último a pegar; no começo, o jogador A). */
    private fun ballOwner(s: VenueNpcSession, t: Long): String = s.coordinator.flightAt(t)?.to ?: PLAYER_A

    private fun pair(s: VenueNpcSession, c: VenueCursor) {
        val p = s.allCursors().first { it.role == VenueRole.BALL_PLAYER && it !== c }
        // O parceiro ainda está em outra coisa: espera virado para ele.
        if (p.time > c.time + 50) {
            s.stay(c, p.time - c.time, VenueNpcState.WAITING, VenueAction.PLAY_BALL, NpcAnimation.LOOK)
            return
        }
        val a = if (c.npcId == PLAYER_A) c else p
        val b = if (a === c) p else c
        // Pausa para uma foto juntos, de vez em quando (dentro do orçamento de microeventos).
        if (s.rand(c, 71) < PHOTO_CHANCE && s.stories.canStart(SceneStoryEventType.BEACH_SHARED_PHOTO, maxOf(a.time, b.time), listOf(a.npcId, b.npcId))) {
            sharedPhoto(s, c, a, b)
            p.decisions++
            return
        }
        when (s.choose(c, setOf(VenueAction.PLAY_BALL, VenueAction.SOCIAL, VenueAction.REST))) {
            VenueAction.SOCIAL -> {
                s.converse(a, b, a.node ?: A1, b.node ?: B1, listOf(true to RALLY_LINES[Math.floorMod(c.decisions.toInt(), RALLY_LINES.size)], false to REPLY_LINES[Math.floorMod(c.decisions.toInt() + 1, REPLY_LINES.size)]))
                c.remember(VenueAction.SOCIAL)
            }
            VenueAction.REST -> {
                // Pausa: um se alonga, o outro olha o mar; depois voltam a se olhar.
                val ms = 3_000L + (s.rand(c, 3) * 3_000).toLong()
                val start = max(a.time, b.time)
                listOf(a, b).forEach { x -> if (x.time < start) s.stay(x, start - x.time, VenueNpcState.WAITING, VenueAction.REST, NpcAnimation.IDLE) }
                s.stay(a, ms, VenueNpcState.PERFORMING_ACTION, VenueAction.REST, NpcAnimation.STRETCH)
                s.stay(b, ms, VenueNpcState.PERFORMING_ACTION, VenueAction.REST, NpcAnimation.LOOK_WINDOW)
                c.remember(VenueAction.REST)
            }
            else -> { rally(s, c, a, b); c.remember(VenueAction.PLAY_BALL) }
        }
        p.decisions++
    }

    /**
     * Uma troca de bola: cada arremesso é planejado com horário de soltar e de chegar; quem recebe
     * ergue os braços antes e agarra exatamente na chegada. Posições e número de passes variam.
     */
    private fun rally(s: VenueNpcSession, c: VenueCursor, a: VenueCursor, b: VenueCursor) {
        val aSpot = if (s.rand(c, 5) < 0.5f) A1 else A2
        val bSpot = if (s.rand(c, 7) < 0.5f) B1 else B2
        s.walkTo(a, aSpot, VenueNpcState.NAVIGATING, VenueAction.PLAY_BALL)
        s.walkTo(b, bSpot, VenueNpcState.NAVIGATING, VenueAction.PLAY_BALL)
        s.turnSide(a, true, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL)
        s.turnSide(b, false, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL)
        var t = max(a.time, b.time) + 200
        listOf(a, b).forEach { x -> s.stay(x, t - x.time, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.LOOK) }
        val throws = 4 + (s.rand(c, 9) * 6).toInt()
        var thrower = if (ballOwner(s, t) == a.npcId) a else b
        repeat(throws) { i ->
            val catcher = if (thrower === a) b else a
            val release = t + THROW_RELEASE_MS
            val dist = abs(catcher.x - thrower.x)
            val arrive = release + 640 + dist * 4L
            // Arremesso.
            s.stay(thrower, NpcPoseLibrary.THROW_MS, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.THROW_BALL)
            s.coordinator.addFlight(VenueInteractionCoordinator.Flight(
                release, arrive, thrower.npcId, catcher.npcId,
                handX(thrower), thrower.y - HAND_DY, handX(catcher), catcher.y - HAND_DY,
            ))
            s.coordinator.emit(release, VenueEvent.BALL_THROWN, thrower.npcId, catcher.npcId)
            // Recebedor: espera, ergue os braços antes da chegada e agarra nela.
            val raise = arrive - CATCH_AT_MS
            if (catcher.time < raise) s.stay(catcher, raise - catcher.time, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.LOOK)
            s.stay(catcher, NpcPoseLibrary.CATCH_MS, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.CATCH_BALL)
            s.coordinator.emit(arrive, VenueEvent.BALL_CAUGHT, catcher.npcId, thrower.npcId)
            // Quem jogou acompanha com o olhar até o outro pegar.
            if (thrower.time < catcher.time) s.stay(thrower, catcher.time - thrower.time, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.LOOK)
            val hold = 250L + (s.rand(c, 20L + i) * 700).toLong()
            s.stay(catcher, hold, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.IDLE)
            s.stay(thrower, hold, VenueNpcState.INTERACTING, VenueAction.PLAY_BALL, NpcAnimation.IDLE)
            t = catcher.time
            thrower = catcher
        }
    }

    private fun handX(c: VenueCursor) = c.x + if (c.facingRight) 6 else -6

    /**
     * BEACH_SHARED_PHOTO: os dois se encontram no meio da quadra, viram de frente, um segura o
     * celular para a selfie e o outro sorri; depois comentam a foto e voltam ao jogo. A bola fica
     * com quem já estava com ela.
     */
    private fun sharedPhoto(s: VenueNpcSession, c: VenueCursor, a: VenueCursor, b: VenueCursor) {
        val start = maxOf(a.time, b.time)
        listOf(a, b).forEach { x -> if (x.time < start) s.stay(x, start - x.time, VenueNpcState.WAITING, VenueAction.PHOTO, NpcAnimation.LOOK) }
        s.walkTo(a, PHOTO_L, VenueNpcState.NAVIGATING, VenueAction.PHOTO)
        s.walkTo(b, PHOTO_R, VenueNpcState.NAVIGATING, VenueAction.PHOTO)
        s.turnIntoFront(a, VenueNpcState.INTERACTING, VenueAction.PHOTO)
        s.turnIntoFront(b, VenueNpcState.INTERACTING, VenueAction.PHOTO)
        val pose = maxOf(a.time, b.time)
        listOf(a, b).forEach { x -> if (x.time < pose) s.stay(x, pose - x.time, VenueNpcState.INTERACTING, VenueAction.PHOTO, NpcAnimation.IDLE, Facing.FRONT) }
        s.stay(a, PHOTO_MS, VenueNpcState.INTERACTING, VenueAction.PHOTO, NpcAnimation.STAND_PHOTO, Facing.FRONT)
        s.stay(b, PHOTO_MS, VenueNpcState.INTERACTING, VenueAction.PHOTO, NpcAnimation.REACTION, Facing.FRONT, reaction = com.hoodie.app.pixel.npc.NpcReaction.SMILE)
        s.coordinator.emit(pose + PHOTO_MS - 800, VenueEvent.PHOTO_TAKEN, a.npcId, "selfie")
        val talk = maxOf(a.time, b.time)
        val line = PHOTO_LINES[Math.floorMod(c.decisions.toInt(), PHOTO_LINES.size)]
        if (s.coordinator.speechFree(talk, talk + PHOTO_LINE_MS)) {
            s.say(b, line, PHOTO_LINE_MS, VenueNpcState.INTERACTING, VenueAction.PHOTO, Facing.FRONT)
            s.stay(a, PHOTO_LINE_MS, VenueNpcState.INTERACTING, VenueAction.PHOTO, NpcAnimation.IDLE, Facing.FRONT)
        }
        val end = maxOf(a.time, b.time)
        listOf(a, b).forEach { x -> if (x.time < end) s.stay(x, end - x.time, VenueNpcState.INTERACTING, VenueAction.PHOTO, NpcAnimation.IDLE, Facing.FRONT) }
        s.stories.record(VenueStoryRecord(SceneStoryEvent(
            id = "beach-photo:${s.seed}:$start", type = SceneStoryEventType.BEACH_SHARED_PHOTO, scene = SceneId.BEACH,
            participants = listOf(a.npcId, b.npcId), requiredSpots = listOf(PHOTO_L, PHOTO_R),
            startAt = start, durationMs = end - start, priority = 1, cooldownMs = PHOTO_COOLDOWN_MS,
            interactAt = pose, resolveAt = pose + PHOTO_MS,
        )))
        c.remember(VenueAction.PHOTO)
    }

    // ───── Sorveteiro ─────

    private fun vendor(s: VenueNpcSession, c: VenueCursor) {
        s.coordinator.nextPending(SERVICE, c.time)?.let { order ->
            VenueScripts.serveOrder(s, c, order, VENDOR, VenueScripts.line(VENDOR_LINES, c), VenueEvent.ICE_CREAM_SERVED) {
                // Abre a tampa, monta a casquinha e entrega pelo lado do carrinho.
                c.held = Item.ICE_CREAM
                s.stay(c, SCOOP_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.PREPARE, Facing.FRONT)
                VENDOR
            }
            return
        }
        val calm = VenueScripts.calmFor(s, c, SERVICE)
        val lastCall = s.coordinator.lastEvent(c.time, setOf(VenueEvent.ICE_CREAM_SERVED), "call")?.at ?: Long.MIN_VALUE / 2
        if (calm > 4_000 && c.time - lastCall > CALL_EVERY_MS && s.coordinator.speechFree(c.time, c.time + 1_800)) {
            s.say(c, VenueScripts.line(CALL_LINES, c), 1_800, VenueNpcState.PERFORMING_ACTION, VenueAction.WAIT, Facing.FRONT)
            s.coordinator.emit(c.time, VenueEvent.ICE_CREAM_SERVED, c.npcId, "call")
            return
        }
        VenueScripts.waitAtPost(s, c, VENDOR, calm)
    }

    // ───── Banhista ─────

    private object Phase {
        const val INSIDE = 1
        const val TO_ORDER = 2
        const val WAITING = 3
        const val BACK = 4
        const val LEAVING = 5
    }

    private fun sunbather(s: VenueNpcSession, c: VenueCursor) {
        when (c.phase) {
            Phase.TO_ORDER -> if (VenueScripts.queueAndOrder(s, c, SERVICE, BUY, listOf(BUY_QUEUE), CONE, VenueScripts.line(ORDER_LINES, c))) c.phase = Phase.WAITING
            Phase.WAITING -> if (VenueScripts.collect(s, c, BUY, Item.ICE_CREAM, VenueEvent.ICE_CREAM_SERVED)) c.phase = Phase.BACK
            Phase.BACK -> {
                s.walkTo(c, SUN_SEAT, VenueNpcState.NAVIGATING, VenueAction.SUNBATHE)
                s.sit(c, VenueAction.SUNBATHE)
                s.stay(c, 7_000 + (s.rand(c, 3) * 4_000).toLong(), VenueNpcState.PERFORMING_ACTION, VenueAction.BUY_ICE_CREAM, NpcAnimation.SIT_LOOK, Facing.FRONT)
                c.held = Item.NONE
                c.phase = Phase.INSIDE
            }
            else -> {
                if (!c.seated) { s.walkTo(c, SUN_SEAT, VenueNpcState.NAVIGATING, VenueAction.SUNBATHE); s.sit(c, VenueAction.SUNBATHE); return }
                val available = buildSet {
                    add(VenueAction.SUNBATHE); add(VenueAction.PHONE)
                    if (vendorOpen(s)) add(VenueAction.BUY_ICE_CREAM)
                }
                when (s.choose(c, available) ?: VenueAction.SUNBATHE) {
                    VenueAction.BUY_ICE_CREAM -> {
                        c.readyAt[VenueAction.BUY_ICE_CREAM] = c.time + 120_000
                        c.remember(VenueAction.BUY_ICE_CREAM)
                        c.phase = Phase.TO_ORDER
                    }
                    VenueAction.PHONE -> {
                        s.stay(c, VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions), VenueNpcState.PERFORMING_ACTION, VenueAction.PHONE, NpcAnimation.SIT_PHONE, Facing.FRONT)
                        c.remember(VenueAction.PHONE)
                    }
                    else -> {
                        // Sol: cochila ou só olha o mar; à noite, as estrelas.
                        val anim = if (s.rand(c, 7) < 0.5f) NpcAnimation.SIT_SLEEP else NpcAnimation.SIT_LOOK
                        s.stay(c, VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions), VenueNpcState.PERFORMING_ACTION, VenueAction.SUNBATHE, anim, Facing.FRONT)
                        c.remember(VenueAction.SUNBATHE)
                    }
                }
            }
        }
    }

    // ───── Turista ─────

    private val VIEWS = listOf(VIEW_L, VIEW_R)

    private fun tourist(s: VenueNpcSession, c: VenueCursor) {
        if (c.offstage) {
            val fromLeft = if (c.decisions == 0L) true else s.rand(c, 47) < 0.5f
            s.enter(c, if (fromLeft) EDGE_L else EDGE_R, if (fromLeft) VIEW_L else VIEW_R, VenueAction.STROLL)
            c.phase = Phase.INSIDE; c.visits = 0; c.visitedFor(4 + (s.rand(c, 53) * 3).toInt())
            return
        }
        when (c.phase) {
            Phase.TO_ORDER -> { if (VenueScripts.queueAndOrder(s, c, SERVICE, BUY, listOf(BUY_QUEUE), CONE, VenueScripts.line(ORDER_LINES, c, 1))) c.phase = Phase.WAITING; return }
            Phase.WAITING -> { if (VenueScripts.collect(s, c, BUY, Item.ICE_CREAM, VenueEvent.ICE_CREAM_SERVED)) c.phase = Phase.INSIDE; return }
        }
        if (c.visits >= c.target!!.toInt()) {
            val right = c.x >= 120
            s.leave(c, if (right) EDGE_R else EDGE_L, 18_000 + (s.rand(c, 41) * 24_000).toLong())
            return
        }
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val available = buildSet {
            add(VenueAction.STROLL)
            if (VIEWS.any { s.isFree(c, it, c.time, c.time + 12_000) }) { add(VenueAction.PHOTO); add(VenueAction.OBSERVE) }
            if (vendorOpen(s) && c.held == Item.NONE) add(VenueAction.BUY_ICE_CREAM)
        }
        val action = s.choose(c, available) ?: VenueAction.STROLL
        c.remember(action); c.visits++
        when (action) {
            VenueAction.BUY_ICE_CREAM -> { c.readyAt[VenueAction.BUY_ICE_CREAM] = c.time + 150_000; c.phase = Phase.TO_ORDER }
            VenueAction.PHOTO, VenueAction.OBSERVE -> {
                val spot = s.chooseFree(c, VIEWS, hold + 2_000) ?: return s.waitSlice(c, VenueAction.WAIT)
                s.endWait(c)
                s.reserveFor(c, spot, hold + 2_000)
                s.walkTo(c, spot, VenueNpcState.NAVIGATING, action)
                c.visited(spot)
                if (action == VenueAction.PHOTO && c.held == Item.NONE) {
                    s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, action)
                    s.stay(c, 3_800, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.STAND_PHOTO, Facing.FRONT)
                    s.coordinator.emit(c.time - 1_600, VenueEvent.PHOTO_TAKEN, c.npcId, spot)
                    c.readyAt[VenueAction.PHOTO] = c.time + 40_000
                } else {
                    // Olha o mar (de perfil, para o lado de onde vêm as ondas); com sorvete, vai tomando.
                    s.turnSide(c, spot == VIEW_L, VenueNpcState.PERFORMING_ACTION, action)
                    s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, VenueAction.OBSERVE, NpcAnimation.LOOK_WINDOW)
                    if (c.held == Item.ICE_CREAM) c.held = Item.NONE
                }
            }
            else -> {
                // Passeia pela beira-mar até o outro lado e para um pouco.
                val target = if (c.x < 120) VIEW_R else VIEW_L
                if (!s.isFree(c, target, c.time, c.time + s.estimateRouteMs(c, target) + 3_000)) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.LOOK); return }
                s.endWait(c)
                s.reserveFor(c, target, 2_500)
                s.walkTo(c, target, VenueNpcState.NAVIGATING, VenueAction.STROLL)
                s.stay(c, 1_400 + (s.rand(c, 9) * 1_200).toLong(), VenueNpcState.PERFORMING_ACTION, VenueAction.STROLL, NpcAnimation.LOOK)
                if (c.held == Item.ICE_CREAM) c.held = Item.NONE
            }
        }
    }

    private fun VenueCursor.visitedFor(n: Int) { target = n.toString() }

    // ───── Ambiente ─────

    override fun ambient(s: VenueNpcSession, t: Long): BeachAmbientState {
        val players = s.allCursors().filter { it.role == VenueRole.BALL_PLAYER }
        if (players.isEmpty()) return BeachAmbientState.atTime(t, null, 0, 0, visible = false, open = false)
        val f = s.coordinator.flightAt(t)
        if (f != null && t < f.arrive) {
            val p = (t - f.release).toFloat() / (f.arrive - f.release)
            val x = f.fromX + ((f.toX - f.fromX) * p).toInt()
            val y = f.fromY + ((f.toY - f.fromY) * p).toInt() - (4 * BALL_ARC * p * (1 - p)).toInt()
            return BeachAmbientState.atTime(t, null, x, y, visible = true, open = vendorOpen(s))
        }
        val owner = f?.to ?: PLAYER_A
        val holder = players.firstOrNull { it.npcId == owner } ?: players.first()
        val seg = s.segmentAt(holder.index, t)
        val (x, y, right) = if (seg != null) {
            val k = ((t - seg.start).toFloat() / (seg.end - seg.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Triple(seg.fromX + ((seg.toX - seg.fromX) * k).toInt(), seg.fromY + ((seg.toY - seg.fromY) * k).toInt(), seg.facingRight)
        } else Triple(holder.x, holder.y, holder.facingRight)
        return BeachAmbientState.atTime(t, owner, x + if (right) 6 else -6, y - HAND_DY + 4, visible = true, open = vendorOpen(s))
    }

    /** O braço do arremesso solta a bola no alto do gesto. */
    private const val THROW_RELEASE_MS = 420L
    /** Quem recebe ergue os braços este tanto antes da chegada (o gesto agarra em 380 ms). */
    private const val CATCH_AT_MS = 380L
    private const val SCOOP_MS = 1_600L
    private const val CALL_EVERY_MS = 45_000L

    private const val PHOTO_CHANCE = 0.12f
    private const val PHOTO_MS = 2_800L
    private const val PHOTO_LINE_MS = 1_600L
    private const val PHOTO_COOLDOWN_MS = 120_000L
    private val PHOTO_LINES = listOf("Ficou otima!", "Essa vai pro album!")

    private val RALLY_LINES = listOf("Boa jogada", "Essa foi longe", "Mais uma")
    private val REPLY_LINES = listOf("Agora vai", "Valeu", "Bora")
    private val ORDER_LINES = listOf("Um de morango", "Um de coco, por favor", "Uma casquinha")
    private val VENDOR_LINES = listOf("Saindo", "Aqui esta", "Bem gelado")
    private val CALL_LINES = listOf("Olha o sorvete", "Picole e casquinha")
}
