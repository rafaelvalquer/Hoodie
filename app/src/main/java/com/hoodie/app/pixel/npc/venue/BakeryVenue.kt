package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/** Objetos da padaria derivados dos pedidos e eventos da sessão. */
data class BakeryAmbient(
    override val timeMs: Long,
    /** Pedido feito e ainda não anotado: comanda na registradora. */
    val orderTicket: Boolean,
    /** Barista preparando na máquina: vapor e luz da cafeteira. */
    val brewing: Boolean,
    /** Xícaras prontas na área de retirada, esperando o cliente. */
    val cupsReady: Int,
    /** Xícaras devolvidas no balcão, esperando a limpeza. */
    val dirtyCups: Int,
    /** Um salgado acabou de sair da vitrine (fica o vão até a reposição). */
    val showcaseGap: Boolean,
    /** Alguém passando pela porta. */
    val doorOpen: Boolean,
    /** Cliente pagando no caixa. */
    val registerActive: Boolean,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.BAKERY

    companion object {
        fun idle(t: Long) = BakeryAmbient(t, false, false, 0, 0, false, false, false)
    }
}

/**
 * Padaria / cafeteria (Sprint 1). Barista com posto fixo atrás do balcão — o grafo dele não se
 * liga ao dos clientes, então ele nunca atravessa o balcão — e dois clientes com ritmos
 * diferentes: o consumidor (ou o leitor, conforme o dia) e o da compra rápida.
 *
 *     cliente: entra → vitrine → fila → pede → espera → retira → senta → consome → devolve → sai
 *     barista: anota → máquina → prepara → entrega no balcão → (limpa quando não há pedido)
 */
internal object BakeryVenue : VenueBlueprint(SceneId.BAKERY) {
    const val SERVICE = "counter"
    const val COFFEE = "coffee"
    const val PASTRY_BAG = "pastry_bag"

    // Salão (clientes)
    const val DOOR = "door"
    const val ENTRY = "entry"
    const val AISLE = "aisle"
    const val MID = "mid"
    const val SHOWCASE = "showcase"
    const val ORDER = "order"
    const val QUEUE = "queue"
    const val PICKUP = "pickup"
    const val TABLE_APPROACH = "table_approach"
    const val SEAT_A = "seat_a"
    const val SEAT_B = "seat_b"
    const val WINDOW_BAR = "window_bar"

    // Atrás do balcão (barista)
    const val B_SHOWCASE = "b_showcase"
    const val B_REGISTER = "b_register"
    const val B_WIPE = "b_wipe"
    const val B_HANDOFF = "b_handoff"
    const val B_MACHINE = "b_machine"

    /**
     * Pés do barista: entre a bancada de trás e a frente do balcão, que passa na frente dele
     * (escondendo as pernas). Clientes nunca chegam aquém de [COUNTER_FRONT_Y].
     */
    const val BARISTA_Y = 186
    const val COUNTER_FRONT_Y = 194
    const val COUNTER_RIGHT_X = 196

    /** Pontos do Hoodie (BakeryScene): balcão onde ele pede e a mesa dele. NPCs nunca param ali. */
    const val HOODIE_COUNTER_X = 108
    const val HOODIE_COUNTER_Y = 228
    const val HOODIE_TABLE_X = 62
    const val HOODIE_TABLE_Y = 284

    private val graph = VenueNavigationGraph(
        "padaria",
        spots = listOf(
            VenueSpot(DOOR, 216, 160, capacity = 4, offstage = true),
            VenueSpot(ENTRY, 220, 196, capacity = 2),
            VenueSpot(AISLE, 186, 226, capacity = 2),
            VenueSpot(MID, 104, 254, capacity = 2),
            VenueSpot(SHOWCASE, 34, 220, interaction = "vitrine"),
            VenueSpot(ORDER, 72, 220, interaction = "pedido"),
            VenueSpot(QUEUE, 44, 244, interaction = "fila"),
            VenueSpot(PICKUP, 146, 220, interaction = "retirada"),
            VenueSpot(TABLE_APPROACH, 186, 252, capacity = 2),
            VenueSpot(SEAT_A, 172, 268, interaction = "mesa"),
            VenueSpot(SEAT_B, 202, 268, interaction = "mesa"),
            VenueSpot(WINDOW_BAR, 220, 300, interaction = "balcão da janela"),
            VenueSpot(B_SHOWCASE, 40, BARISTA_Y, interaction = "vitrine por dentro"),
            VenueSpot(B_REGISTER, 72, BARISTA_Y, interaction = "caixa"),
            VenueSpot(B_WIPE, 108, BARISTA_Y, interaction = "bancada"),
            VenueSpot(B_HANDOFF, 146, BARISTA_Y, interaction = "entrega"),
            VenueSpot(B_MACHINE, 174, BARISTA_Y, interaction = "cafeteira"),
        ),
        edges = listOf(
            DOOR to ENTRY, ENTRY to AISLE, AISLE to PICKUP, AISLE to TABLE_APPROACH, ENTRY to TABLE_APPROACH,
            PICKUP to MID, MID to TABLE_APPROACH, MID to ORDER, MID to QUEUE, QUEUE to ORDER, QUEUE to SHOWCASE,
            SHOWCASE to ORDER, TABLE_APPROACH to SEAT_A, TABLE_APPROACH to SEAT_B, ENTRY to WINDOW_BAR,
            // Atrás do balcão: componente separado.
            B_SHOWCASE to B_REGISTER, B_REGISTER to B_WIPE, B_WIPE to B_HANDOFF, B_HANDOFF to B_MACHINE,
        ),
        obstacles = listOf(
            VenueObstacle("balcão", 14, COUNTER_FRONT_Y, COUNTER_RIGHT_X, 212),
            VenueObstacle("mesa do Hoodie", 38, 288, 92, 304),
            VenueObstacle("mesa dos clientes", 160, 274, 214, 290),
            VenueObstacle("parede", 0, 0, 239, 150),
        ),
    )

    override fun graph(variant: Int) = graph

    /** Nenhum ponto do grafo coincide com os do Hoodie; a reserva fica só como trava. */
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> {
        // Dia/variante alternam o cliente que fica: consumidor ou leitor (Cliente C).
        val reader = Math.floorMod(key.daySeed + key.variant, 3) == 2
        val barista = VenueActorSpec(
            NpcCharacterRegistry.DOG_WORKER, profile("barista", VenueRole.BARISTA, mapOf(VenueAction.CLEAN to 30, VenueAction.WAIT to 70), 0f, 1_400, 2_800),
            slotSeed = 211, start = VenueStart.POST, startSpot = B_REGISTER,
        )
        val stay = if (reader) VenueActorSpec(
            NpcCharacterRegistry.RABBIT_READER,
            profile("cafe_reader", VenueRole.CAFE_READER, mapOf(VenueAction.READ to 45, VenueAction.CONSUME to 30, VenueAction.OBSERVE to 15, VenueAction.PHONE to 10), 0f, 5_000, 9_000),
            slotSeed = 223, start = VenueStart.OFFSTAGE, startDelayMs = 900L + Math.floorMod(key.daySeed, 5) * 300L,
        ) else VenueActorSpec(
            NpcCharacterRegistry.CAT_GUEST,
            profile("cafe_customer", VenueRole.CAFE_CUSTOMER, mapOf(VenueAction.CONSUME to 45, VenueAction.OBSERVE to 25, VenueAction.PHONE to 20, VenueAction.BROWSE to 10), 0f, 4_000, 7_000),
            slotSeed = 227, start = VenueStart.OFFSTAGE, startDelayMs = 900L + Math.floorMod(key.daySeed, 5) * 300L,
        )
        val quick = VenueActorSpec(
            NpcCharacterRegistry.MOUSE_COMMUTER,
            profile("quick_buyer", VenueRole.QUICK_BUYER, mapOf(VenueAction.BROWSE to 50, VenueAction.ORDER to 50), 0f, 2_000, 4_000),
            slotSeed = 229, start = VenueStart.OFFSTAGE, startDelayMs = 7_000L + Math.floorMod(key.daySeed, 7) * 900L,
        )
        // À noite a padaria fica mais calma: só o barista e um cliente.
        return if (key.night) listOf(barista, stay) else listOf(barista, stay, quick)
    }

    private fun profile(id: String, role: VenueRole, actions: Map<VenueAction, Int>, social: Float, minIdle: Long, maxIdle: Long) =
        VenueNpcProfile(id, role, actions, social, minIdle, maxIdle)

    // ───────────── Roteiros ─────────────

    override fun step(s: VenueNpcSession, c: VenueCursor) = when (c.role) {
        VenueRole.BARISTA -> barista(s, c)
        VenueRole.QUICK_BUYER -> quickBuyer(s, c)
        else -> customer(s, c)
    }

    private object Phase {
        const val OUTSIDE = 0
        const val INSIDE = 1
        const val TO_ORDER = 2
        const val WAITING = 3
        const val CONSUMING = 4
        const val LEAVING = 5
    }

    /** Ninguém pede enquanto outro pedido do balcão não foi retirado (um atendimento por vez). */
    private fun counterBusy(s: VenueNpcSession, c: VenueCursor) =
        s.coordinator.orders().any { it.service == SERVICE && it.customerId != c.npcId && (it.pickedAt?.let { p -> p > c.time } ?: true) }

    private fun enterIfOutside(s: VenueNpcSession, c: VenueCursor): Boolean {
        if (!c.offstage) return false
        // A porta comporta a passagem, mas ninguém entra enquanto outro ainda está saindo por ela.
        s.enter(c, DOOR, ENTRY, VenueAction.BROWSE)
        c.phase = Phase.INSIDE; c.orderId = -1; c.visits = 0; c.held = Item.NONE
        return true
    }

    /** Fila: pede se o balcão está livre; senão espera na fila (ou na vitrine se a fila estiver ocupada). */
    private fun goOrder(s: VenueNpcSession, c: VenueCursor, product: String) {
        val arrive = c.time + s.estimateRouteMs(c, ORDER)
        if (!counterBusy(s, c) && s.isFree(c, ORDER, c.time, arrive + ORDER_MS + 1_000)) {
            s.endWait(c)
            s.reserveFor(c, ORDER, ORDER_MS)
            s.walkTo(c, ORDER, VenueNpcState.NAVIGATING, VenueAction.ORDER)
            s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.ORDER)
            val line = if (product == COFFEE) ORDER_COFFEE_LINES else ORDER_PASTRY_LINES
            s.say(c, line[Math.floorMod(c.decisions.toInt() + c.spec.slotSeed, line.size)], ORDER_MS, VenueNpcState.INTERACTING, VenueAction.ORDER, Facing.FRONT)
            // O pedido vale quando a fala termina: o barista responde depois, nunca por cima.
            val order = s.coordinator.placeOrder(SERVICE, c.npcId, c.time, product)
            s.coordinator.emit(c.time, VenueEvent.ORDER_PLACED, c.npcId, product)
            c.orderId = order.id
            c.phase = Phase.WAITING
            return
        }
        val waitAt = when {
            c.node == QUEUE || s.isFree(c, QUEUE, c.time, c.time + 4_000) -> QUEUE
            c.node == SHOWCASE || s.isFree(c, SHOWCASE, c.time, c.time + 4_000) -> SHOWCASE
            else -> null
        }
        if (waitAt != null && c.node != waitAt) {
            s.endWait(c)
            s.reserveFor(c, waitAt, 3_000)
            s.walkTo(c, waitAt, VenueNpcState.NAVIGATING, VenueAction.WAIT)
            s.turnSide(c, true, VenueNpcState.WAITING, VenueAction.WAIT)
        }
        s.waitSlice(c, VenueAction.WAIT, if (c.node == SHOWCASE) NpcAnimation.STAND_BROWSE else NpcAnimation.CHECKOUT_WAIT)
    }

    /** Espera o pedido ficar pronto em [at]; true quando já pegou. */
    private fun collect(s: VenueNpcSession, c: VenueCursor, at: String, item: Item, event: VenueEvent): Boolean {
        val order = s.coordinator.order(c.orderId) ?: run { c.phase = Phase.LEAVING; return false }
        if (c.node != at) {
            s.endWait(c)
            s.walkTo(c, at, VenueNpcState.NAVIGATING, VenueAction.WAIT)
            s.turnIntoFront(c, VenueNpcState.WAITING, VenueAction.WAIT)
            return false
        }
        val ready = order.readyAt
        if (ready == null) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT); return false }
        if (ready > c.time) {
            s.stay(c, ready - c.time, VenueNpcState.WAITING, VenueAction.WAIT, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT, localOffsetMs = c.time - c.waitStartedAt.coerceAtLeast(0))
        }
        s.endWait(c)
        // Só pega depois de pronto: o objeto aparece na mão no meio do gesto.
        s.stay(c, PICK_MS / 2, VenueNpcState.INTERACTING, VenueAction.ORDER, NpcAnimation.SERVE, Facing.FRONT)
        c.held = item
        order.pickedAt = c.time
        s.coordinator.emit(c.time, event, c.npcId, order.product)
        s.stay(c, PICK_MS / 2, VenueNpcState.INTERACTING, VenueAction.ORDER, NpcAnimation.IDLE, Facing.FRONT)
        return true
    }

    /** Consumidor e leitor: pedem café, sentam e ficam um tempo; devolvem a xícara e saem. */
    private fun customer(s: VenueNpcSession, c: VenueCursor) {
        if (enterIfOutside(s, c)) return
        when (c.phase) {
            Phase.INSIDE -> {
                // Às vezes olha a vitrine antes de entrar na fila.
                if (s.rand(c, 3) < 0.4f && s.isFree(c, SHOWCASE, c.time, c.time + 6_000)) {
                    s.reserveFor(c, SHOWCASE, 3_000)
                    s.walkTo(c, SHOWCASE, VenueNpcState.NAVIGATING, VenueAction.BROWSE)
                    s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE)
                    s.stay(c, 2_200 + (s.rand(c, 5) * 1_500).toLong(), VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE, NpcAnimation.STAND_BROWSE)
                    c.remember(VenueAction.BROWSE)
                }
                c.phase = Phase.TO_ORDER
            }
            Phase.TO_ORDER -> goOrder(s, c, COFFEE)
            Phase.WAITING -> if (collect(s, c, PICKUP, Item.MUG, VenueEvent.CUP_PICKED)) {
                c.phase = Phase.CONSUMING
                c.visits = 0
            }
            Phase.CONSUMING -> consume(s, c)
            else -> returnAndLeave(s, c)
        }
        c.decisions++
    }

    private fun consume(s: VenueNpcSession, c: VenueCursor) {
        val rounds = if (c.role == VenueRole.CAFE_READER) 5 else 3
        if (c.visits >= rounds) { c.phase = Phase.LEAVING; return }
        if (!c.seated && c.node !in SEATS && c.node != WINDOW_BAR) {
            val stayMs = rounds * 7_000L
            val seat = s.chooseFree(c, SEATS, stayMs, avoidRecent = false) ?: WINDOW_BAR.takeIf { s.isFree(c, it, c.time, c.time + stayMs) }
            if (seat == null) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.STAND_COFFEE); return }
            s.endWait(c)
            s.reserveFor(c, seat, stayMs)
            s.walkTo(c, seat, VenueNpcState.NAVIGATING, VenueAction.CONSUME)
            if (seat in SEATS) s.sit(c, VenueAction.CONSUME) else s.turnSide(c, false, VenueNpcState.PERFORMING_ACTION, VenueAction.CONSUME)
            c.visited(seat)
            return
        }
        val seated = c.seated
        val available = buildSet {
            add(VenueAction.CONSUME); add(VenueAction.OBSERVE); add(VenueAction.PHONE)
            if (c.role == VenueRole.CAFE_READER && seated) add(VenueAction.READ)
        }
        val action = s.choose(c, available) ?: VenueAction.CONSUME
        val ms = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val (anim, facing) = when (action) {
            VenueAction.READ -> NpcAnimation.READ_DOCUMENT to Facing.FRONT
            VenueAction.PHONE -> (if (seated) NpcAnimation.SIT_PHONE else NpcAnimation.STAND_PHONE) to (if (seated) Facing.FRONT else Facing.SIDE)
            VenueAction.OBSERVE -> (if (seated) NpcAnimation.SIT_LOOK else NpcAnimation.LOOK) to (if (seated) Facing.FRONT else Facing.SIDE)
            else -> (if (seated) NpcAnimation.SIT_DRINK else NpcAnimation.STAND_COFFEE) to (if (seated) Facing.FRONT else Facing.SIDE)
        }
        s.stay(c, ms, VenueNpcState.PERFORMING_ACTION, action, anim, facing, seated = seated)
        c.remember(action)
        c.visits++
    }

    /** Devolve a xícara no balcão (o barista limpa depois) e vai embora. */
    private fun returnAndLeave(s: VenueNpcSession, c: VenueCursor) {
        if (c.held == Item.MUG) {
            if (!s.isFree(c, PICKUP, c.time, c.time + s.estimateRouteMs(c, PICKUP) + 2_000) || counterPickupPending(s, c)) {
                s.waitSlice(c, VenueAction.WAIT, if (c.seated) NpcAnimation.SIT_LOOK else NpcAnimation.IDLE, if (c.seated) Facing.FRONT else Facing.SIDE)
                return
            }
            s.endWait(c)
            s.reserveFor(c, PICKUP, 1_500)
            s.walkTo(c, PICKUP, VenueNpcState.NAVIGATING, VenueAction.EXIT)
            s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.EXIT)
            s.stay(c, NpcPoseLibrary.SERVE_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.EXIT, NpcAnimation.SERVE, Facing.FRONT)
            c.held = Item.NONE
            s.coordinator.emit(c.time, VenueEvent.CUP_RETURNED, c.npcId)
        }
        s.endWait(c)
        s.leave(c, DOOR, 9_000 + (s.rand(c, 41) * 14_000).toLong())
        c.phase = Phase.OUTSIDE
    }

    /** O balcão de retirada tem um café esperando outro cliente: não devolve a xícara em cima dele. */
    private fun counterPickupPending(s: VenueNpcSession, c: VenueCursor) =
        s.coordinator.orders().any { it.service == SERVICE && it.product == COFFEE && it.customerId != c.npcId && it.pickedAt == null }

    /** Compra rápida: vitrine, pede, recebe a sacola no caixa, paga e sai. */
    private fun quickBuyer(s: VenueNpcSession, c: VenueCursor) {
        if (enterIfOutside(s, c)) return
        when (c.phase) {
            Phase.INSIDE -> {
                if (s.isFree(c, SHOWCASE, c.time, c.time + s.estimateRouteMs(c, SHOWCASE) + 5_000)) {
                    s.reserveFor(c, SHOWCASE, 3_500)
                    s.walkTo(c, SHOWCASE, VenueNpcState.NAVIGATING, VenueAction.BROWSE)
                    s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE)
                    s.stay(c, 2_400 + (s.rand(c, 7) * 1_800).toLong(), VenueNpcState.PERFORMING_ACTION, VenueAction.BROWSE, NpcAnimation.STAND_BROWSE)
                    c.remember(VenueAction.BROWSE)
                }
                c.phase = Phase.TO_ORDER
            }
            Phase.TO_ORDER -> goOrder(s, c, PASTRY_BAG)
            Phase.WAITING -> if (collect(s, c, ORDER, Item.SHOPPING_BAG, VenueEvent.SHOPPING_BAG_TAKEN)) {
                s.stay(c, PAY_MS, VenueNpcState.INTERACTING, VenueAction.PAY, NpcAnimation.CHECKOUT_PAY, Facing.FRONT)
                s.say(c, THANKS_LINES[Math.floorMod(c.decisions.toInt() + c.spec.slotSeed, THANKS_LINES.size)], 1_300, VenueNpcState.INTERACTING, VenueAction.PAY, Facing.FRONT)
                c.phase = Phase.LEAVING
            }
            else -> {
                s.endWait(c)
                s.leave(c, DOOR, 16_000 + (s.rand(c, 43) * 20_000).toLong())
                c.phase = Phase.OUTSIDE
            }
        }
        c.decisions++
    }

    /**
     * Barista: atende um pedido por vez (anota → prepara → entrega). Sem pedido, espera no caixa;
     * limpa a bancada só quando não há atendimento prioritário à vista.
     */
    private fun barista(s: VenueNpcSession, c: VenueCursor) {
        val pending = s.coordinator.nextPending(SERVICE, c.time)
        if (pending != null) { serve(s, c, pending); c.decisions++; return }
        val upcoming = s.coordinator.firstUnserved(SERVICE)
        val calmFor = upcoming?.let { it.placedAt - c.time } ?: Long.MAX_VALUE
        val dirty = dirtyCupsAt(s, c.time)
        when {
            dirty > 0 && calmFor > CLEAN_MS + 2_000 -> {
                s.walkTo(c, B_HANDOFF, VenueNpcState.NAVIGATING, VenueAction.CLEAN)
                s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN)
                s.stay(c, CLEAN_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN, NpcAnimation.WIPE, Facing.FRONT)
                s.coordinator.emit(c.time, VenueEvent.COUNTER_CLEANED, c.npcId)
                c.remember(VenueAction.CLEAN)
            }
            calmFor > CLEAN_MS + 4_000 && s.choose(c, setOf(VenueAction.CLEAN, VenueAction.WAIT)) == VenueAction.CLEAN -> {
                // Bancada sem xícaras: passa o pano no meio do balcão de vez em quando.
                s.walkTo(c, B_WIPE, VenueNpcState.NAVIGATING, VenueAction.CLEAN)
                s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN)
                s.stay(c, CLEAN_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.CLEAN, NpcAnimation.WIPE, Facing.FRONT)
                c.remember(VenueAction.CLEAN)
            }
            else -> {
                if (c.node != B_REGISTER) s.walkTo(c, B_REGISTER, VenueNpcState.NAVIGATING, VenueAction.WAIT)
                s.turnIntoFront(c, VenueNpcState.WAITING, VenueAction.WAIT)
                // Espera até o próximo pedido (se ele já estiver a caminho) ou um pouco, olhando o salão.
                val ms = calmFor.coerceIn(400L, VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions))
                s.stay(c, ms, VenueNpcState.WAITING, VenueAction.WAIT, NpcAnimation.LOOK, Facing.FRONT)
                c.remember(VenueAction.WAIT)
            }
        }
        c.decisions++
    }

    private fun serve(s: VenueNpcSession, c: VenueCursor, order: VenueInteractionCoordinator.VenueOrder) {
        s.walkTo(c, B_REGISTER, VenueNpcState.NAVIGATING, VenueAction.SERVE)
        s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.SERVE)
        order.takenAt = c.time; order.servedBy = c.npcId
        s.coordinator.emit(c.time, VenueEvent.ORDER_TAKEN, c.npcId, order.product)
        s.say(c, BARISTA_LINES[Math.floorMod(order.id, BARISTA_LINES.size)], 1_300, VenueNpcState.INTERACTING, VenueAction.SERVE, Facing.FRONT)
        if (order.product == COFFEE) {
            // Xícara na máquina, preparo visível, entrega na área de retirada.
            s.walkTo(c, B_MACHINE, VenueNpcState.NAVIGATING, VenueAction.PREPARE)
            s.turnSide(c, true, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE)
            c.held = Item.MUG
            s.stay(c, PREPARE_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.PREPARE)
            s.walkTo(c, B_HANDOFF, VenueNpcState.NAVIGATING, VenueAction.SERVE)
        } else {
            // Salgado da vitrine para o saquinho, entregue no caixa.
            s.walkTo(c, B_SHOWCASE, VenueNpcState.NAVIGATING, VenueAction.PREPARE)
            s.turnIntoFront(c, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE)
            s.stay(c, BAG_MS / 2, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.STAND_PICK_PRODUCT, Facing.FRONT)
            s.coordinator.emit(c.time, VenueEvent.PASTRY_PICKED, c.npcId)
            c.held = Item.SHOPPING_BAG
            s.stay(c, BAG_MS / 2, VenueNpcState.PERFORMING_ACTION, VenueAction.PREPARE, NpcAnimation.IDLE, Facing.FRONT)
            s.walkTo(c, B_REGISTER, VenueNpcState.NAVIGATING, VenueAction.SERVE)
        }
        s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.SERVE)
        val start = c.time
        s.stay(c, NpcPoseLibrary.SERVE_MS, VenueNpcState.INTERACTING, VenueAction.SERVE, NpcAnimation.SERVE, Facing.FRONT)
        c.held = Item.NONE
        // Pronto no instante em que o objeto sai da mão do barista.
        order.readyAt = start + SERVE_RELEASE_MS
        if (order.product == COFFEE) s.coordinator.emit(order.readyAt!!, VenueEvent.COFFEE_READY, c.npcId, order.product)
        c.remember(VenueAction.SERVE)
    }

    private fun dirtyCupsAt(s: VenueNpcSession, t: Long): Int {
        val events = s.coordinator.events()
        val lastClean = events.filter { it.event == VenueEvent.COUNTER_CLEANED && it.at <= t }.maxOfOrNull { it.at } ?: Long.MIN_VALUE
        return events.count { it.event == VenueEvent.CUP_RETURNED && it.at <= t && it.at > lastClean }
    }

    // ───────────── Ambiente ─────────────

    override fun ambient(s: VenueNpcSession, t: Long): BakeryAmbient {
        val orders = s.coordinator.orders()
        var brewing = false; var paying = false; var door = false
        s.allCursors().forEach { c ->
            val seg = s.segmentAt(c.index, t) ?: return@forEach
            if (seg.action == VenueAction.PREPARE && seg.animation == NpcAnimation.PREPARE) brewing = true
            if (seg.action == VenueAction.PAY) paying = true
            if (!seg.offstage && (seg.fromNode == DOOR || seg.toNode == DOOR || seg.spot == DOOR)) door = true
        }
        val lastPastry = s.coordinator.lastEvent(t, setOf(VenueEvent.PASTRY_PICKED))
        return BakeryAmbient(
            timeMs = t,
            orderTicket = orders.any { it.placedAt <= t && (it.takenAt?.let { k -> t < k } ?: true) },
            brewing = brewing,
            cupsReady = orders.count { o -> o.product == COFFEE && o.readyAt?.let { it <= t } == true && (o.pickedAt?.let { t < it } ?: true) },
            dirtyCups = dirtyCupsAt(s, t),
            showcaseGap = lastPastry != null && t - lastPastry.at < SHOWCASE_REFILL_MS,
            doorOpen = door,
            registerActive = paying,
        )
    }

    private val SEATS = listOf(SEAT_A, SEAT_B)
    private const val ORDER_MS = 1_500L
    private const val PICK_MS = 700L
    private const val PAY_MS = 1_200L
    private const val PREPARE_MS = 3_400L
    private const val BAG_MS = 1_400L
    private const val CLEAN_MS = 2_600L
    private const val SERVE_RELEASE_MS = 650L
    private const val SHOWCASE_REFILL_MS = 90_000L

    private val ORDER_COFFEE_LINES = listOf("Um cafe, por favor", "Um pingado, por favor", "Cafe com leite")
    private val ORDER_PASTRY_LINES = listOf("Dois salgados", "Um pao de queijo", "Uma coxinha, por favor")
    private val BARISTA_LINES = listOf("Bom dia", "Ja sai", "Saindo")
    private val THANKS_LINES = listOf("Obrigado", "Valeu", "Ate amanha")
}
