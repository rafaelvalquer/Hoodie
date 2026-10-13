package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/** Objetos do shopping: escada rolante, porta da loja C, banco e vitrines. */
data class MallAmbient(
    override val timeMs: Long,
    /** Fase dos degraus da escada rolante (0..1, ciclo contínuo). */
    val escalatorPhase: Float,
    /** Alguém atravessando a porta da loja C (entrando ou saindo). */
    val storeDoorOpen: Boolean,
    /** Quantos visitantes estão dentro da loja C agora. */
    val shoppersInside: Int,
    /** Assentos ocupados por NPCs (o do meio é do Hoodie). */
    val benchSeats: Int,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.MALL

    companion object {
        const val ESCALATOR_MS = 2_400L
        fun idle(t: Long) = MallAmbient(t, Math.floorMod(t, ESCALATOR_MS) / ESCALATOR_MS.toFloat(), false, 0, 0)
    }
}

/**
 * Controle de circulação do corredor (plano, seção 4): limite de visitantes em cena, uma entrada
 * por vez em cada borda, distância mínima entre quem para e ocupação do banco.
 */
internal object MallCrowdCoordinator {
    const val MAX_VISIBLE = 4
    /** Ninguém entra pela mesma borda logo atrás de outro. */
    const val ENTRY_GAP_MS = 3_500L

    fun visible(s: VenueNpcSession) = s.allCursors().count { !it.offstage }

    fun canEnter(s: VenueNpcSession, c: VenueCursor, edge: String): Boolean {
        if (visible(s) >= MAX_VISIBLE) return false
        val last = s.coordinator.lastEvent(c.time, setOf(VenueEvent.SEAT_RESERVED), "edge:$edge")?.at ?: Long.MIN_VALUE / 2
        return c.time - last >= ENTRY_GAP_MS
    }

    fun markEntry(s: VenueNpcSession, c: VenueCursor, edge: String) =
        s.coordinator.emit(c.time, VenueEvent.SEAT_RESERVED, c.npcId, "edge:$edge")
}

/**
 * Shopping (Sprint 4): corredor principal com três vitrines, escada rolante (animação ambiental),
 * banco de três lugares, plantas e placas. Comprador (vitrines → entra na loja → sai com sacola),
 * dois visitantes sociais que se encontram e conversam, e um visitante tranquilo (banco, celular).
 * Entrar na loja não carrega outra cena: o personagem some atrás da porta e volta depois.
 */
internal object MallVenue : VenueBlueprint(SceneId.MALL) {
    const val EDGE_L = "edge_l"
    const val EDGE_R = "edge_r"
    const val L_WALK = "l_walk"
    const val MID = "mid"
    const val R_WALK = "r_walk"
    const val WIN_A = "win_a"
    const val WIN_C = "win_c"
    const val DOOR_C = "door_c"
    const val LOOK_L = "look_l"
    const val LOOK_R = "look_r"
    const val SEAT_L = "seat_l"
    const val SEAT_R = "seat_r"
    const val TALK_L = "talk_l"
    const val TALK_R = "talk_r"

    /** Pontos do Hoodie (MallScene). */
    const val HOODIE_STOREFRONT_A_X = 30
    const val HOODIE_STOREFRONT_C_X = 214
    const val HOODIE_STOREFRONT_Y = 178
    const val HOODIE_STORE_DOOR_X = 78
    const val HOODIE_STORE_DOOR_Y = 166
    const val HOODIE_BENCH_X = 120
    const val BENCH_Y = 282
    const val STORE_C_DOOR_X = 172

    private val graph = VenueNavigationGraph(
        "shopping",
        spots = listOf(
            VenueSpot(EDGE_L, -36, 222, capacity = 4, offstage = true),
            VenueSpot(EDGE_R, 276, 222, capacity = 4, offstage = true),
            VenueSpot(L_WALK, 30, 222, interaction = "corredor"),
            VenueSpot(MID, 120, 228, capacity = 3),
            VenueSpot(R_WALK, 210, 222, interaction = "corredor"),
            VenueSpot(WIN_A, 54, 182, interaction = "vitrine A"),
            VenueSpot(WIN_C, 188, 182, interaction = "vitrine C"),
            VenueSpot(DOOR_C, STORE_C_DOOR_X, 168, capacity = 2, interaction = "porta da loja C"),
            VenueSpot(LOOK_L, 40, 262, interaction = "corredor"),
            VenueSpot(LOOK_R, 206, 266, interaction = "corredor"),
            VenueSpot(SEAT_L, 92, BENCH_Y, interaction = "banco"),
            VenueSpot(SEAT_R, 148, BENCH_Y, interaction = "banco"),
            VenueSpot(TALK_L, 150, 244, interaction = "conversa"),
            VenueSpot(TALK_R, 180, 244, interaction = "conversa"),
        ),
        edges = listOf(
            EDGE_L to L_WALK, L_WALK to MID, MID to R_WALK, R_WALK to EDGE_R,
            L_WALK to WIN_A, R_WALK to WIN_C, R_WALK to DOOR_C, WIN_C to DOOR_C,
            L_WALK to LOOK_L, LOOK_L to SEAT_L, R_WALK to LOOK_R, LOOK_R to SEAT_R,
            MID to TALK_L, TALK_L to TALK_R, TALK_R to R_WALK,
        ),
        obstacles = listOf(
            VenueObstacle("lojas", 0, 0, 239, 160),
            VenueObstacle("banco", 70, 286, 170, 296),
            VenueObstacle("escada rolante", 106, 160, 156, 200),
        ),
    )

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        fun spec(style: com.hoodie.app.pixel.character.CharacterStyle, id: String, role: VenueRole, actions: Map<VenueAction, Int>, social: Float, seed: Int, delay: Long, min: Long, max: Long) =
            VenueActorSpec(style, VenueNpcProfile(id, role, actions, social, min, max), seed, VenueStart.OFFSTAGE, delay)
        val shopper = spec(
            NpcCharacterRegistry.DOG_SHOPPER, "mall_shopper", VenueRole.MALL_SHOPPER,
            mapOf(VenueAction.WINDOW_SHOP to 35, VenueAction.ENTER_STORE to 30, VenueAction.REST to 15, VenueAction.STROLL to 20), 0.1f,
            501, 1_000L + Math.floorMod(key.daySeed, 4) * 600L, 3_000, 6_000,
        )
        val friendA = spec(
            NpcCharacterRegistry.CAT_COLLEAGUE, "mall_friend_a", VenueRole.MALL_SOCIAL,
            mapOf(VenueAction.SOCIAL to 40, VenueAction.OBSERVE to 25, VenueAction.STROLL to 20, VenueAction.WINDOW_SHOP to 15), 0.8f,
            503, 4_000L + Math.floorMod(key.daySeed, 5) * 700L, 3_500, 6_500,
        )
        val friendB = spec(
            NpcCharacterRegistry.RABBIT_WALKER, "mall_friend_b", VenueRole.MALL_SOCIAL,
            mapOf(VenueAction.SOCIAL to 40, VenueAction.WINDOW_SHOP to 25, VenueAction.STROLL to 20, VenueAction.OBSERVE to 15), 0.8f,
            509, 9_000L + Math.floorMod(key.daySeed, 3) * 800L, 3_500, 6_500,
        )
        val calm = spec(
            NpcCharacterRegistry.MOUSE_COMMUTER, "mall_calm", VenueRole.MALL_CALM,
            mapOf(VenueAction.REST to 35, VenueAction.PHONE to 30, VenueAction.WINDOW_SHOP to 20, VenueAction.OBSERVE to 15), 0.1f,
            521, 16_000L + Math.floorMod(key.daySeed, 6) * 900L, 6_000, 11_000,
        )
        return if (key.night) listOf(shopper, calm) else listOf(shopper, friendA, friendB, calm)
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        if (c.offstage) enter(s, c) else visit(s, c)
        c.decisions++
    }

    private fun enter(s: VenueNpcSession, c: VenueCursor) {
        val fromLeft = if (c.decisions == 0L) Math.floorMod(c.spec.slotSeed, 2) == 0 else s.rand(c, 47) < 0.5f
        val edge = if (fromLeft) EDGE_L else EDGE_R
        if (!MallCrowdCoordinator.canEnter(s, c, edge)) { s.offstageWait(c, 2_000); return }
        MallCrowdCoordinator.markEntry(s, c, edge)
        s.enter(c, edge, if (fromLeft) L_WALK else R_WALK, VenueAction.STROLL)
        c.visits = 0; c.phase = 0; c.held = Item.NONE
        c.target = (4 + (s.rand(c, 53) * 4).toInt()).toString()
    }

    private fun visit(s: VenueNpcSession, c: VenueCursor) {
        if (c.visits >= (c.target?.toIntOrNull() ?: 5)) { leave(s, c); return }
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val available = buildSet {
            add(VenueAction.STROLL)
            if (listOf(WIN_A, WIN_C).any { s.isFree(c, it, c.time, c.time + hold + 8_000) }) add(VenueAction.WINDOW_SHOP)
            if (SEATS.any { s.isFree(c, it, c.time, c.time + hold + 8_000) }) add(VenueAction.REST)
            if (c.held == Item.NONE) add(VenueAction.ENTER_STORE)
            if (!c.seated) { add(VenueAction.PHONE); add(VenueAction.OBSERVE) }
            if (partnerFor(s, c) != null) add(VenueAction.SOCIAL)
        }
        val action = s.choose(c, available) ?: VenueAction.STROLL
        c.remember(action); c.visits++
        when (action) {
            VenueAction.WINDOW_SHOP -> windowShop(s, c, hold)
            VenueAction.ENTER_STORE -> enterStore(s, c)
            VenueAction.REST -> rest(s, c, hold)
            VenueAction.SOCIAL -> meet(s, c)
            VenueAction.PHONE, VenueAction.OBSERVE -> standAround(s, c, action, hold)
            else -> stroll(s, c)
        }
    }

    private fun windowShop(s: VenueNpcSession, c: VenueCursor, hold: Long) {
        val spot = s.chooseFree(c, listOf(WIN_A, WIN_C), hold + 1_500) ?: return stroll(s, c)
        s.reserveFor(c, spot, hold + 1_500)
        s.walkTo(c, spot, VenueNpcState.NAVIGATING, VenueAction.WINDOW_SHOP)
        // A vitrine fica na parede do fundo: olha de lado, para cima, apontando às vezes.
        s.turnSide(c, spot == WIN_A, VenueNpcState.PERFORMING_ACTION, VenueAction.WINDOW_SHOP)
        s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, VenueAction.WINDOW_SHOP, if (s.rand(c, 5) < 0.5f) NpcAnimation.LOOK_PROMOTION else NpcAnimation.STAND_BROWSE)
        c.visited(spot)
    }

    /** Entra na loja C (some atrás da porta), compra e volta com a sacola. */
    private fun enterStore(s: VenueNpcSession, c: VenueCursor) {
        if (c.seated) s.standUp(c, VenueAction.ENTER_STORE)
        s.walkTo(c, DOOR_C, VenueNpcState.NAVIGATING, VenueAction.ENTER_STORE)
        val inside = STORE_MIN_MS + (s.rand(c, 17) * STORE_EXTRA_MS).toLong()
        s.stay(c, inside, VenueNpcState.OFFSTAGE, VenueAction.ENTER_STORE, NpcAnimation.IDLE, hidden = true)
        c.held = Item.SHOPPING_BAG
        s.coordinator.emit(c.time, VenueEvent.SHOPPING_BAG_TAKEN, c.npcId, "loja C")
        s.stay(c, 500, VenueNpcState.PERFORMING_ACTION, VenueAction.ENTER_STORE, NpcAnimation.LOOK)
    }

    private fun rest(s: VenueNpcSession, c: VenueCursor, hold: Long) {
        val seat = s.chooseFree(c, SEATS, hold + 6_000) ?: return stroll(s, c)
        val stay = hold + 4_000
        s.reserveFor(c, seat, stay)
        s.walkTo(c, seat, VenueNpcState.NAVIGATING, VenueAction.REST)
        s.sit(c, VenueAction.REST)
        s.stay(c, stay, VenueNpcState.PERFORMING_ACTION, VenueAction.REST, if (s.rand(c, 9) < 0.5f) NpcAnimation.SIT_PHONE else NpcAnimation.SIT_LOOK, Facing.FRONT)
        c.visited(seat)
    }

    private fun standAround(s: VenueNpcSession, c: VenueCursor, action: VenueAction, hold: Long) {
        val spot = s.chooseFree(c, listOf(LOOK_L, LOOK_R), hold + 1_000) ?: return stroll(s, c)
        s.reserveFor(c, spot, hold + 1_000)
        s.walkTo(c, spot, VenueNpcState.NAVIGATING, action)
        s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, action, if (action == VenueAction.PHONE) NpcAnimation.STAND_PHONE else NpcAnimation.LOOK)
        c.visited(spot)
    }

    /**
     * Atravessa o corredor e para num ponto livre do outro lado (o meio é só passagem). Sem ponto
     * livre, segue andando e vai embora — nunca para em cima de alguém.
     */
    private fun stroll(s: VenueNpcSession, c: VenueCursor) {
        val pause = 900 + (s.rand(c, 21) * 1_200).toLong()
        val far = if (c.x < 120) listOf(R_WALK, LOOK_R) else listOf(L_WALK, LOOK_L)
        val stop = far.firstOrNull { s.isFree(c, it, c.time, c.time + s.estimateRouteMs(c, it) + pause + 1_500) } ?: return leave(s, c)
        s.reserveFor(c, stop, pause)
        s.walkTo(c, stop, VenueNpcState.NAVIGATING, VenueAction.STROLL)
        s.stay(c, pause, VenueNpcState.PERFORMING_ACTION, VenueAction.STROLL, NpcAnimation.LOOK)
    }

    private fun partnerFor(s: VenueNpcSession, c: VenueCursor): VenueCursor? {
        if (c.role != VenueRole.MALL_SOCIAL || c.offstage || c.seated) return null
        if ((c.readyAt[VenueAction.SOCIAL] ?: 0L) > c.time) return null
        if (s.coordinator.busy(c.time, c.time + 25_000)) return null
        return s.allCursors().firstOrNull { o ->
            o !== c && o.role == VenueRole.MALL_SOCIAL && !o.offstage && !o.seated &&
                o.time in c.time..c.time + 6_000 && (o.readyAt[VenueAction.SOCIAL] ?: 0L) <= o.time
        }
    }

    /** Os amigos se encontram, conversam frente a frente e cada um segue o seu caminho. */
    private fun meet(s: VenueNpcSession, c: VenueCursor) {
        val p = partnerFor(s, c) ?: return stroll(s, c)
        val hold = 12_000L
        if (!s.isFree(c, TALK_L, c.time, c.time + hold + 8_000) || !s.isFree(p, TALK_R, p.time, p.time + hold + 8_000)) return stroll(s, c)
        s.reservation.reserve(TALK_L, c.npcId, c.time, c.time + hold + 8_000)
        s.reservation.reserve(TALK_R, p.npcId, p.time, p.time + hold + 8_000)
        val i = c.decisions.toInt()
        s.converse(c, p, TALK_L, TALK_R, listOf(
            true to GREETINGS[Math.floorMod(i, GREETINGS.size)],
            false to REPLIES[Math.floorMod(i + 1, REPLIES.size)],
            true to TOPICS[Math.floorMod(i + 2, TOPICS.size)],
            false to FAREWELLS[Math.floorMod(i + 3, FAREWELLS.size)],
        ))
        p.visits++
    }

    private fun leave(s: VenueNpcSession, c: VenueCursor) {
        val right = if (kotlin.math.abs(c.x - 120) < 30) s.rand(c, 41) < 0.5f else c.x > 120
        s.leave(c, if (right) EDGE_R else EDGE_L, 12_000 + (s.rand(c, 43) * 26_000).toLong())
    }

    override fun ambient(s: VenueNpcSession, t: Long): MallAmbient {
        var door = false; var inside = 0; var seats = 0
        s.allCursors().forEach { c ->
            val seg = s.segmentAt(c.index, t) ?: return@forEach
            if (seg.action == VenueAction.ENTER_STORE && seg.hidden) inside++
            if (seg.toNode == DOOR_C || seg.fromNode == DOOR_C) {
                val local = t - seg.start
                if (seg.kind == VenueNpcSession.Kind.WALK || local < 700 || seg.end - t < 700) door = true
            }
            if (seg.seated && seg.spot in SEATS) seats++
        }
        return MallAmbient(t, Math.floorMod(t, MallAmbient.ESCALATOR_MS) / MallAmbient.ESCALATOR_MS.toFloat(), door, inside, seats)
    }

    private val SEATS = listOf(SEAT_L, SEAT_R)
    private const val STORE_MIN_MS = 12_000L
    private const val STORE_EXTRA_MS = 14_000L

    private val GREETINGS = listOf("Oi, sumida", "Que coincidencia", "Ola, tudo bem")
    private val REPLIES = listOf("Tudo otimo", "Vim ver as vitrines", "Que bom te ver")
    private val TOPICS = listOf("Viu a promocao", "Vamos no cafe", "Adorei a vitrine")
    private val FAREWELLS = listOf("Ate mais", "Combinado", "Tchau, ate logo")
}
