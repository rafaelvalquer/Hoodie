package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.brain.NpcDeterministicRandom

/**
 * Tudo o que dois ou mais personagens fazem juntos, com horários fechados no planejamento:
 *
 *  - **Pedidos** num balcão: o cliente faz o pedido, o funcionário pega quando fica livre,
 *    prepara e entrega. O cliente só recebe depois de [VenueOrder.readyAt] — ninguém bebe um
 *    café que ainda está na máquina. Um atendimento por vez por balcão.
 *  - **Encontros**: um de cada vez na cena, com cooldown por personagem.
 *  - **Falas**: um balão por vez na cena inteira.
 *  - **Eventos** de objetos ([VenueEvent]) com instante, lidos pelo estado ambiental da cena.
 */
class VenueInteractionCoordinator(private val seed: Int) {

    class VenueOrder(
        val id: Int,
        val service: String,
        val customerId: String,
        /** Quando o cliente chegou ao balcão e pediu. */
        val placedAt: Long,
        val product: String,
    ) {
        /** Funcionário começou a atender (anotar / preparar). null = ainda na fila. */
        var takenAt: Long? = null
        var servedBy: String? = null

        /** Pronto na área de retirada. */
        var readyAt: Long? = null

        /** Cliente pegou. */
        var pickedAt: Long? = null
    }

    data class Encounter(val id: Int, val a: String, val b: String, val start: Long, val end: Long)

    data class EventRecord(val at: Long, val event: VenueEvent, val actorId: String, val tag: String? = null)

    /**
     * Objeto em voo entre duas mãos (a bola da praia): sai de [from] em [release] e chega a [to]
     * em [arrive]. Entre um voo e o próximo, o dono é quem pegou por último.
     */
    data class Flight(val release: Long, val arrive: Long, val from: String, val to: String, val fromX: Int, val fromY: Int, val toX: Int, val toY: Int)

    private val orders = ArrayList<VenueOrder>()
    private val flights = ArrayList<Flight>()
    private val encounters = ArrayList<Encounter>()
    private val speech = ArrayList<LongArray>()
    private val events = ArrayList<EventRecord>()
    private var nextOrderId = 1

    fun reset() {
        orders.clear(); encounters.clear(); speech.clear(); events.clear(); flights.clear(); nextOrderId = 1
    }

    // ───── Objetos em voo ─────

    fun addFlight(f: Flight) { flights += f }

    fun flights(): List<Flight> = flights.toList()

    /** Último voo que já começou até [t] (null = o objeto ainda não saiu da mão do primeiro dono). */
    fun flightAt(t: Long): Flight? {
        var best: Flight? = null
        for (f in flights) if (f.release <= t && (best == null || f.release > best.release)) best = f
        return best
    }

    // ───── Pedidos ─────

    fun placeOrder(service: String, customerId: String, at: Long, product: String): VenueOrder =
        VenueOrder(nextOrderId++, service, customerId, at, product).also { orders += it }

    /** Pedido mais antigo ainda não atendido deste balcão que já tinha sido feito até [upTo]. */
    fun nextPending(service: String, upTo: Long): VenueOrder? =
        orders.filter { it.service == service && it.takenAt == null && it.placedAt <= upTo }.minByOrNull { it.placedAt }

    /** Primeiro pedido (mesmo futuro) ainda sem atendimento: o funcionário espera por ele. */
    fun firstUnserved(service: String): VenueOrder? =
        orders.filter { it.service == service && it.takenAt == null }.minByOrNull { it.placedAt }

    fun order(id: Int): VenueOrder? = orders.firstOrNull { it.id == id }

    fun orders(): List<VenueOrder> = orders.toList()

    /** O balcão está ocupado por um atendimento em [t]? (do pedido até a retirada). */
    fun serviceBusyAt(service: String, t: Long): Boolean = orders.any { o ->
        o.service == service && t >= o.placedAt && (o.pickedAt?.let { t < it } ?: true)
    }

    // ───── Encontros ─────

    fun busy(from: Long, until: Long): Boolean = encounters.any { it.start < until && from < it.end }

    fun register(a: String, b: String, start: Long, end: Long): Encounter =
        Encounter(encounters.size + 1, a, b, start, end).also { encounters += it }

    fun encounters(): List<Encounter> = encounters.toList()

    fun cooldownMs(npcId: String, index: Long): Long =
        COOLDOWN_MIN_MS + (NpcDeterministicRandom.value(npcId, seed, 7_000 + index) * (COOLDOWN_MAX_MS - COOLDOWN_MIN_MS)).toLong()

    // ───── Falas ─────

    fun speechFree(from: Long, until: Long) = speech.none { it[0] < until && from < it[1] }

    fun addSpeech(from: Long, until: Long) { speech += longArrayOf(from, until) }

    fun firstSpeechFreeFrom(from: Long, duration: Long): Long {
        var t = from
        while (true) {
            val clash = speech.filter { it[0] < t + duration && t < it[1] }.maxOfOrNull { it[1] } ?: return t
            t = clash + LINE_GAP_MS
        }
    }

    // ───── Eventos ─────

    fun emit(at: Long, event: VenueEvent, actorId: String, tag: String? = null) {
        events += EventRecord(at, event, actorId, tag)
    }

    fun events(): List<EventRecord> = events.toList()

    /** Último evento de um dos [types] até [t] (inclusive), opcionalmente de uma [tag]. */
    fun lastEvent(t: Long, types: Set<VenueEvent>, tag: String? = null): EventRecord? =
        events.filter { it.at <= t && it.event in types && (tag == null || it.tag == tag) }.maxByOrNull { it.at }

    /** Descarta o que terminou antes de [before]. */
    fun prune(before: Long) {
        orders.removeAll { o -> (o.pickedAt ?: Long.MAX_VALUE) < before }
        encounters.removeAll { it.end < before }
        speech.removeAll { it[1] < before }
        events.removeAll { it.at < before - EVENT_MEMORY_MS }
        // Guarda sempre o último voo antigo: ele diz quem está com a bola.
        val keep = flights.filter { it.arrive < before }.maxByOrNull { it.release }
        flights.removeAll { it.arrive < before && it !== keep }
    }

    internal val retainedSize: Int get() = orders.size + encounters.size + speech.size + events.size + flights.size

    companion object {
        const val COOLDOWN_MIN_MS = 60_000L
        const val COOLDOWN_MAX_MS = 100_000L
        const val LINE_GAP_MS = 250L
        /** Eventos antigos ainda podem decidir o estado de um objeto (xícara esquecida na mesa). */
        const val EVENT_MEMORY_MS = 10 * 60_000L
    }
}
