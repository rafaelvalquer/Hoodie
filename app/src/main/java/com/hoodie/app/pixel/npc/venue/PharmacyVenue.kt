package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/** Objetos da farmácia: vãos nas prateleiras, reposição em andamento, caixa e porta. */
data class PharmacyAmbient(
    override val timeMs: Long,
    /** Prateleiras com um produto faltando (cliente levou e ainda não foi reposto). */
    val shelfGaps: Set<String>,
    /** Prateleira sendo reposta agora (caixa de produtos aparece ao lado). */
    val restocking: String?,
    val registerActive: Boolean,
    val doorOpen: Boolean,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.PHARMACY

    companion object {
        fun idle(t: Long) = PharmacyAmbient(t, emptySet(), null, false, false)
    }
}

/**
 * Farmácia (Sprint 2): parada breve do cotidiano. Atendente com rotina própria (atende no caixa,
 * repõe as prateleiras quando não há cliente para atender, organiza o estoque atrás do balcão) e
 * clientes que olham prateleiras, às vezes levam um produto genérico, pagam e saem.
 *
 * Privacidade: nenhum produto, fala ou evento identifica remédio, sintoma ou motivo da visita.
 */
internal object PharmacyVenue : VenueBlueprint(SceneId.PHARMACY) {
    const val SERVICE = "caixa"
    const val BAG = "bag"

    const val DOOR = "door"
    const val ENTRY = "entry"
    const val FLOOR_MID = "floor_mid"
    const val W_SHELF_1 = "w_shelf_1"
    const val W_SHELF_2 = "w_shelf_2"
    const val GONDOLA_L = "gondola_l"
    const val GONDOLA_R = "gondola_r"
    const val GATE_OUT = "gate_out"
    const val COUNTER_PAY = "counter_pay"
    const val QUEUE = "queue"
    const val C_GATE = "c_gate"
    const val C_REG = "c_reg"
    const val C_STOCK = "c_stock"

    const val CLERK_Y = 186
    const val COUNTER_FRONT_Y = 194
    const val COUNTER_LEFT_X = 152

    /** Pontos do Hoodie (PharmacyScene). */
    const val HOODIE_SHELF_A_X = 46
    const val HOODIE_SHELF_A_Y = 196
    const val HOODIE_SHELF_B_X = 88
    const val HOODIE_SHELF_B_Y = 292
    const val HOODIE_CHECKOUT_X = 178
    const val HOODIE_CHECKOUT_Y = 230

    /** Prateleiras em que um cliente pode olhar (e o atendente repor) e para que lado ficam. */
    val SHELVES = mapOf(W_SHELF_1 to true, W_SHELF_2 to false, GONDOLA_L to true, GONDOLA_R to false)

    private val graph = VenueNavigationGraph(
        "farmácia",
        spots = listOf(
            VenueSpot(DOOR, 114, 160, capacity = 4, offstage = true),
            VenueSpot(ENTRY, 114, 196, capacity = 2),
            VenueSpot(FLOOR_MID, 110, 226, capacity = 2),
            VenueSpot(W_SHELF_1, 18, 190, interaction = "prateleira"),
            VenueSpot(W_SHELF_2, 76, 190, interaction = "prateleira"),
            VenueSpot(GONDOLA_L, 40, 262, interaction = "gôndola"),
            VenueSpot(GONDOLA_R, 132, 262, interaction = "gôndola"),
            VenueSpot(GATE_OUT, 144, 228, capacity = 2),
            VenueSpot(COUNTER_PAY, 212, 228, interaction = "caixa"),
            VenueSpot(QUEUE, 214, 258, interaction = "fila"),
            VenueSpot(C_GATE, 144, CLERK_Y, capacity = 2),
            VenueSpot(C_REG, 200, CLERK_Y, interaction = "caixa por dentro"),
            VenueSpot(C_STOCK, 228, CLERK_Y, interaction = "estoque"),
        ),
        edges = listOf(
            DOOR to ENTRY, ENTRY to FLOOR_MID, FLOOR_MID to W_SHELF_2, W_SHELF_2 to W_SHELF_1, W_SHELF_1 to GONDOLA_L,
            FLOOR_MID to GATE_OUT, GATE_OUT to C_GATE, C_GATE to C_REG, C_REG to C_STOCK,
            GATE_OUT to COUNTER_PAY, COUNTER_PAY to QUEUE, GATE_OUT to GONDOLA_R,
        ),
        obstacles = listOf(
            VenueObstacle("balcão", COUNTER_LEFT_X, COUNTER_FRONT_Y, 238, 212),
            VenueObstacle("gôndola", 56, 250, 120, 272),
            VenueObstacle("parede", 0, 0, 239, 150),
        ),
    )

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        val clerk = VenueActorSpec(
            NpcCharacterRegistry.DUCK_SLEEPY,
            VenueNpcProfile("pharmacy_clerk", VenueRole.PHARMACY_CLERK, mapOf(VenueAction.RESTOCK to 35, VenueAction.WAIT to 65), 0f, 1_800, 3_400),
            slotSeed = 311, start = VenueStart.POST, startSpot = C_REG,
        )
        val first = VenueActorSpec(
            NpcCharacterRegistry.DOG_SHOPPER,
            VenueNpcProfile("pharmacy_customer", VenueRole.PHARMACY_CUSTOMER, mapOf(VenueAction.BROWSE to 60, VenueAction.PAY to 40), 0f, 2_400, 4_400),
            slotSeed = 317, start = VenueStart.OFFSTAGE, startDelayMs = 1_200L + Math.floorMod(key.daySeed, 4) * 500L,
        )
        val second = VenueActorSpec(
            NpcCharacterRegistry.CAT_COLLEAGUE,
            VenueNpcProfile("pharmacy_visitor", VenueRole.PHARMACY_CUSTOMER, mapOf(VenueAction.BROWSE to 70, VenueAction.PAY to 30), 0f, 3_000, 5_200),
            slotSeed = 331, start = VenueStart.OFFSTAGE, startDelayMs = 14_000L + Math.floorMod(key.daySeed, 6) * 1_100L,
        )
        return if (key.night) listOf(clerk, first) else listOf(clerk, first, second)
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        if (c.role == VenueRole.PHARMACY_CLERK) clerk(s, c) else customer(s, c)
        c.decisions++
    }

    private object Phase {
        const val INSIDE = 1
        const val TO_PAY = 2
        const val WAITING = 3
        const val LEAVING = 4
    }

    // ───── Cliente ─────

    private fun customer(s: VenueNpcSession, c: VenueCursor) {
        if (VenueScripts.enterIfOutside(s, c, DOOR, ENTRY, VenueAction.BROWSE)) return
        when (c.phase) {
            Phase.INSIDE -> browse(s, c)
            Phase.TO_PAY -> if (VenueScripts.queueAndOrder(s, c, SERVICE, COUNTER_PAY, listOf(QUEUE), BAG, VenueScripts.line(ASK_LINES, c))) c.phase = Phase.WAITING
            Phase.WAITING -> if (VenueScripts.collect(s, c, COUNTER_PAY, Item.SHOPPING_BAG, VenueEvent.SHOPPING_BAG_TAKEN)) {
                s.stay(c, PAY_MS, VenueNpcState.INTERACTING, VenueAction.PAY, NpcAnimation.CHECKOUT_PAY, Facing.FRONT)
                s.say(c, VenueScripts.line(THANKS_LINES, c), 1_300, VenueNpcState.INTERACTING, VenueAction.PAY, Facing.FRONT)
                c.phase = Phase.LEAVING
            }
            else -> {
                s.endWait(c)
                s.leave(c, DOOR, 14_000 + (s.rand(c, 43) * 22_000).toLong())
            }
        }
    }

    /** Olha uma prateleira; às vezes leva uma caixinha. Depois de uma ou duas, paga ou vai embora. */
    private fun browse(s: VenueNpcSession, c: VenueCursor) {
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val shelf = s.chooseFree(c, SHELVES.keys.toList(), hold + 1_200)
        if (shelf == null || c.visits >= 2) {
            c.phase = if (c.held == Item.PRODUCT || s.rand(c, 9) < 0.35f) Phase.TO_PAY else Phase.LEAVING
            return
        }
        s.endWait(c)
        s.reserveFor(c, shelf, hold + 1_200)
        s.walkTo(c, shelf, VenueNpcState.NAVIGATING, VenueAction.BROWSE)
        s.turnSide(c, SHELVES.getValue(shelf), VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE)
        s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE, NpcAnimation.STAND_BROWSE)
        c.visited(shelf); c.remember(VenueAction.BROWSE)
        if (c.held == Item.NONE && s.rand(c, 13) < 0.6f) {
            s.stay(c, PICK_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE, NpcAnimation.STAND_PICK_PRODUCT)
            c.held = Item.PRODUCT
            s.coordinator.emit(c.time, VenueEvent.PRODUCT_TAKEN, c.npcId, shelf)
            s.stay(c, 1_200, VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE, NpcAnimation.STAND_READ_PRODUCT)
        }
        c.visits++
    }

    // ───── Atendente ─────

    private fun clerk(s: VenueNpcSession, c: VenueCursor) {
        s.coordinator.nextPending(SERVICE, c.time)?.let { order ->
            VenueScripts.serveOrder(s, c, order, C_REG, VenueScripts.line(CLERK_LINES, c), null) {
                // Embala o produto no balcão.
                s.stay(c, 900, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
                c.held = Item.SHOPPING_BAG
                s.stay(c, 500, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.IDLE, Facing.FRONT)
                C_REG
            }
            return
        }
        val calm = VenueScripts.calmFor(s, c, SERVICE)
        val gaps = gapsAt(s, c.time)
        // Loja vazia (ninguém dentro nem entrando): repõe uma prateleira (com vão, ou só organizando).
        val trip = if (calm > RESTOCK_TRIP_MS && storeEmpty(s, c) && (gaps.isNotEmpty() || s.choose(c, setOf(VenueAction.RESTOCK, VenueAction.WAIT)) == VenueAction.RESTOCK)) {
            (gaps.toList() + SHELVES.keys).firstOrNull { s.isFree(c, it, c.time, c.time + RESTOCK_TRIP_MS) }
        } else null
        when {
            trip != null -> restock(s, c, trip)
            calm > ORGANIZE_MS + 2_000 && s.choose(c, setOf(VenueAction.RESTOCK, VenueAction.WAIT)) == VenueAction.RESTOCK -> {
                s.walkTo(c, C_STOCK, VenueNpcState.NAVIGATING, VenueAction.RESTOCK)
                s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK)
                s.stay(c, ORGANIZE_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK, NpcAnimation.RESTOCK)
                c.remember(VenueAction.RESTOCK)
            }
            else -> VenueScripts.waitAtPost(s, c, C_REG, calm)
        }
    }

    /**
     * Ninguém vai precisar do caixa durante a ida à prateleira: nenhum cliente indo pagar ou já com
     * uma prateleira vista (pode pagar logo), e quem entrar durante a ida ainda vai olhar as
     * prateleiras antes ([BROWSE_BEFORE_PAY_MS]).
     */
    private fun storeEmpty(s: VenueNpcSession, clerk: VenueCursor) = s.allCursors().none { o ->
        o !== clerk && if (o.offstage) o.time < clerk.time + RESTOCK_TRIP_MS - BROWSE_BEFORE_PAY_MS
        else o.phase >= Phase.TO_PAY || o.visits >= 1
    }

    /** Pega caixas no estoque, sai pela portinhola, repõe a prateleira e volta ao caixa. */
    private fun restock(s: VenueNpcSession, c: VenueCursor, shelf: String) {
        s.walkTo(c, C_STOCK, VenueNpcState.NAVIGATING, VenueAction.RESTOCK)
        s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK)
        s.stay(c, PICK_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK, NpcAnimation.STAND_PICK_PRODUCT)
        c.held = Item.PRODUCT
        s.reserveFor(c, shelf, RESTOCK_MS)
        s.walkTo(c, shelf, VenueNpcState.NAVIGATING, VenueAction.RESTOCK)
        s.turnSide(c, SHELVES.getValue(shelf), VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK)
        s.stay(c, RESTOCK_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.RESTOCK, NpcAnimation.RESTOCK)
        c.held = Item.NONE
        s.coordinator.emit(c.time, VenueEvent.SHELF_RESTOCKED, c.npcId, shelf)
        c.remember(VenueAction.RESTOCK)
        s.walkTo(c, C_REG, VenueNpcState.NAVIGATING, VenueAction.WAIT)
    }

    private fun gapsAt(s: VenueNpcSession, t: Long): Set<String> {
        val events = s.coordinator.events()
        return SHELVES.keys.filter { shelf ->
            val taken = events.filter { it.event == VenueEvent.PRODUCT_TAKEN && it.tag == shelf && it.at <= t }.maxOfOrNull { it.at } ?: return@filter false
            val restocked = events.filter { it.event == VenueEvent.SHELF_RESTOCKED && it.tag == shelf && it.at <= t }.maxOfOrNull { it.at } ?: Long.MIN_VALUE
            taken > restocked
        }.toSet()
    }

    override fun ambient(s: VenueNpcSession, t: Long): PharmacyAmbient {
        var restocking: String? = null; var paying = false; var door = false
        s.allCursors().forEach { c ->
            val seg = s.segmentAt(c.index, t) ?: return@forEach
            if (seg.animation == NpcAnimation.RESTOCK && seg.spot in SHELVES) restocking = seg.spot
            if (seg.action == VenueAction.PAY) paying = true
            if (!seg.offstage && (seg.fromNode == DOOR || seg.toNode == DOOR)) door = true
        }
        return PharmacyAmbient(t, gapsAt(s, t), restocking, paying, door)
    }

    private const val PICK_MS = 900L
    private const val PAY_MS = 1_200L
    private const val RESTOCK_MS = 2_800L
    private const val ORGANIZE_MS = 2_400L
    /** Ir ao estoque, à prateleira e voltar (com folga): só sai do caixa se ninguém chegar antes. */
    private const val RESTOCK_TRIP_MS = 16_000L
    /** Quem acabou de entrar olha pelo menos uma prateleira antes de ir ao caixa. */
    private const val BROWSE_BEFORE_PAY_MS = 8_000L

    // Falas neutras: nada sobre remédio, sintoma ou motivo da visita.
    private val ASK_LINES = listOf("Boa tarde", "So isso, por favor", "Vou levar este")
    private val CLERK_LINES = listOf("Pois nao", "Mais alguma coisa", "Pode passar")
    private val THANKS_LINES = listOf("Obrigado", "Ate mais", "Bom dia")
}
