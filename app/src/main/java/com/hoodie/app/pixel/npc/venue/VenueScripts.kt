package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item

/**
 * Trechos de roteiro que se repetem entre estabelecimentos: entrar pela porta, fazer fila e pedir
 * num balcão, esperar e retirar o pedido, e o funcionário que atende um pedido por vez.
 * Cada função planeja um pedaço e devolve se a etapa terminou (senão, ela esperou uma fatia).
 */
internal object VenueScripts {

    /** Algum pedido de outro cliente ainda não retirado neste balcão (um atendimento por vez). */
    fun counterBusy(s: VenueNpcSession, c: VenueCursor, service: String) =
        s.coordinator.orders().any { it.service == service && it.customerId != c.npcId && (it.pickedAt?.let { p -> p > c.time } ?: true) }

    /** Entra pela porta se estiver fora de cena; true = planejou a entrada. */
    fun enterIfOutside(s: VenueNpcSession, c: VenueCursor, door: String, first: String, action: VenueAction): Boolean {
        if (!c.offstage) return false
        s.enter(c, door, first, action)
        c.phase = 1; c.orderId = -1; c.visits = 0; c.held = Item.NONE
        return true
    }

    /**
     * Pede no balcão se ele estiver livre; senão espera no primeiro ponto livre de [waitSpots]
     * (fila, vitrine). true = pedido feito (a fala termina e o pedido passa a valer).
     */
    fun queueAndOrder(
        s: VenueNpcSession, c: VenueCursor, service: String, orderSpot: String, waitSpots: List<String>,
        product: String, line: String, orderMs: Long = ORDER_MS, faceRightWhileWaiting: Boolean = true,
        /** Esperando aqui, olha os produtos (vitrine) em vez de só esperar. */
        browseSpot: String? = null,
    ): Boolean {
        val arrive = c.time + s.estimateRouteMs(c, orderSpot)
        if (!counterBusy(s, c, service) && s.isFree(c, orderSpot, c.time, arrive + orderMs + 1_000)) {
            s.endWait(c)
            s.reserveFor(c, orderSpot, orderMs)
            s.walkTo(c, orderSpot, VenueNpcState.NAVIGATING, VenueAction.ORDER)
            s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.ORDER)
            s.say(c, line, orderMs, VenueNpcState.INTERACTING, VenueAction.ORDER, Facing.FRONT)
            // O pedido vale quando a fala termina: o funcionário responde depois, nunca por cima.
            val order = s.coordinator.placeOrder(service, c.npcId, c.time, product)
            s.coordinator.emit(c.time, VenueEvent.ORDER_PLACED, c.npcId, product)
            c.orderId = order.id
            return true
        }
        val waitAt = waitSpots.firstOrNull { it == c.node } ?: waitSpots.firstOrNull { s.isFree(c, it, c.time, c.time + 4_000) }
        if (waitAt != null && c.node != waitAt) {
            s.endWait(c)
            s.reserveFor(c, waitAt, 3_000)
            s.walkTo(c, waitAt, VenueNpcState.NAVIGATING, VenueAction.WAIT)
            s.turnSide(c, faceRightWhileWaiting, VenueNpcState.WAITING, VenueAction.WAIT)
        }
        s.waitSlice(c, VenueAction.WAIT, if (c.node == browseSpot) NpcAnimation.STAND_BROWSE else NpcAnimation.CHECKOUT_WAIT)
        return false
    }

    /**
     * Espera o pedido [VenueCursor.orderId] ficar pronto em [at] e pega [item] (nunca antes de pronto).
     * true = já está com o objeto. Sem pedido (descartado), devolve true sem objeto.
     */
    fun collect(s: VenueNpcSession, c: VenueCursor, at: String, item: Item, event: VenueEvent): Boolean {
        val order = s.coordinator.order(c.orderId) ?: return true
        if (c.node != at) {
            s.endWait(c)
            s.walkTo(c, at, VenueNpcState.NAVIGATING, VenueAction.WAIT)
            s.turnIntoFront(c, VenueNpcState.WAITING, VenueAction.WAIT)
            return false
        }
        val ready = order.readyAt
        if (ready == null) { s.waitSlice(c, VenueAction.WAIT, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT); return false }
        if (ready > c.time) {
            s.stay(c, ready - c.time, VenueNpcState.WAITING, VenueAction.WAIT, NpcAnimation.CHECKOUT_WAIT, Facing.FRONT,
                localOffsetMs = c.time - c.waitStartedAt.coerceAtLeast(0))
        }
        s.endWait(c)
        // O objeto aparece na mão no meio do gesto de pegar.
        s.stay(c, PICK_MS / 2, VenueNpcState.INTERACTING, VenueAction.ORDER, NpcAnimation.SERVE, Facing.FRONT)
        c.held = item
        order.pickedAt = c.time
        s.coordinator.emit(c.time, event, c.npcId, order.product)
        s.stay(c, PICK_MS / 2, VenueNpcState.INTERACTING, VenueAction.ORDER, NpcAnimation.IDLE, Facing.FRONT)
        return true
    }

    /**
     * Funcionário de posto: anota em [register] e entrega com o gesto SERVE; [prepare] planeja o
     * que acontece entre um e outro (máquina, prateleira, computador) e devolve onde entregar.
     * O pedido fica pronto quando o objeto sai da mão do funcionário.
     */
    inline fun serveOrder(
        s: VenueNpcSession, c: VenueCursor, order: VenueInteractionCoordinator.VenueOrder, register: String,
        greeting: String?, readyEvent: VenueEvent?,
        /** Logo antes de entregar (já de frente no ponto de entrega): chamar o cliente, por exemplo. */
        beforeHandoff: () -> Unit = {},
        prepare: () -> String,
    ) {
        s.walkTo(c, register, VenueNpcState.NAVIGATING, VenueAction.SERVE)
        s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.SERVE)
        order.takenAt = c.time; order.servedBy = c.npcId
        s.coordinator.emit(c.time, VenueEvent.ORDER_TAKEN, c.npcId, order.product)
        if (greeting != null) s.say(c, greeting, 1_300, VenueNpcState.INTERACTING, VenueAction.SERVE, Facing.FRONT)
        val handoff = prepare()
        s.walkTo(c, handoff, VenueNpcState.NAVIGATING, VenueAction.SERVE)
        s.turnIntoFront(c, VenueNpcState.INTERACTING, VenueAction.SERVE)
        beforeHandoff()
        val start = c.time
        s.stay(c, NpcPoseLibrary.SERVE_MS, VenueNpcState.INTERACTING, VenueAction.SERVE, NpcAnimation.SERVE, Facing.FRONT)
        c.held = Item.NONE
        order.readyAt = start + SERVE_RELEASE_MS
        if (readyEvent != null) s.coordinator.emit(order.readyAt!!, readyEvent, c.npcId, order.product)
        c.remember(VenueAction.SERVE)
    }

    /** Fora de turno: espera no posto até o próximo pedido a caminho ou uma fatia curta, olhando o salão. */
    fun waitAtPost(s: VenueNpcSession, c: VenueCursor, post: String, calmForMs: Long, anim: NpcAnimation = NpcAnimation.LOOK) {
        if (c.node != post) s.walkTo(c, post, VenueNpcState.NAVIGATING, VenueAction.WAIT)
        s.turnIntoFront(c, VenueNpcState.WAITING, VenueAction.WAIT)
        val ms = calmForMs.coerceIn(400L, VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions))
        s.stay(c, ms, VenueNpcState.WAITING, VenueAction.WAIT, anim, Facing.FRONT)
        c.remember(VenueAction.WAIT)
    }

    /** Até o próximo pedido deste balcão (Long.MAX_VALUE = nenhum à vista). */
    fun calmFor(s: VenueNpcSession, c: VenueCursor, service: String): Long =
        s.coordinator.firstUnserved(service)?.let { it.placedAt - c.time } ?: Long.MAX_VALUE

    fun line(lines: List<String>, c: VenueCursor, salt: Int = 0) = lines[Math.floorMod(c.decisions.toInt() + c.spec.slotSeed + salt, lines.size)]

    const val ORDER_MS = 1_500L
    const val PICK_MS = 700L
    const val SERVE_RELEASE_MS = 650L
}
