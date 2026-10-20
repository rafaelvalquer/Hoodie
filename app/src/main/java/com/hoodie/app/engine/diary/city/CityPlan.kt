package com.hoodie.app.engine.diary.city

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode

/**
 * Lugares do dia → prédios da cidade. Cada lugar físico (mesma [JourneyNode.placeKey]) ganha UM
 * prédio: voltar para Casa é voltar ao mesmo prédio. A escolha é por tipo (Casa no bairro,
 * Trabalho no prédio alto, Academia na academia…) e determinística pela ordem de chegada.
 */
object CityPlacement {
    private val PREFERENCE: Map<PlaceType, List<String>> = mapOf(
        PlaceType.HOME to listOf("home", "house2", "house4", "house3"),
        PlaceType.FAMILY to listOf("house3", "house4", "house2"),
        PlaceType.WORK to listOf("work", "office2", "office3"),
        PlaceType.GYM to listOf("gym", "office3"),
        PlaceType.BAKERY to listOf("bakery", "restaurant", "shops"),
        PlaceType.RESTAURANT to listOf("restaurant", "bakery", "shops"),
        PlaceType.MARKET to listOf("market", "shops"),
        PlaceType.MALL to listOf("market", "shops"),
        PlaceType.STORE to listOf("shops", "market"),
        PlaceType.PHARMACY to listOf("pharmacy", "shops"),
        PlaceType.SCHOOL to listOf("office3", "office2"),
    )
    private val GENERIC = listOf("office3", "office2", "shops", "house4", "house2", "house3", "bakery", "market", "restaurant", "pharmacy", "gym")

    /** placeKey → id do prédio. */
    fun assign(nodes: List<JourneyNode>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val taken = HashSet<String>()
        nodes.sortedBy { it.arrivalAt }.forEach { n ->
            if (n.placeKey in out) return@forEach
            val wanted = PREFERENCE[n.placeType].orEmpty()
            val slot = (wanted + GENERIC).firstOrNull { it !in taken } ?: wanted.firstOrNull() ?: GENERIC.first()
            out[n.placeKey] = slot; taken += slot
        }
        return out
    }
}

/** Uma permanência do dia num prédio. */
data class CityStay(
    val index: Int,
    val nodeId: String,
    val slotId: String,
    val placeName: String,
    val placeType: PlaceType,
    val arrivalAt: Long,
    val departureAt: Long?,
    /** Fim usado no replay (saída, ou o fim do replay se ainda está lá). */
    val endAt: Long,
    /** 1 = primeira vez no lugar hoje; 2+ = retorno. */
    val visitNumber: Int,
    /** Eventos de contexto que formam a permanência (mais de 1 = agrupada). */
    val sourceCount: Int,
) {
    val icon: String get() = when (placeType) {
        PlaceType.HOME, PlaceType.FAMILY -> "heart"
        PlaceType.WORK -> "laptop"
        PlaceType.GYM -> "dumbbell"
        PlaceType.RESTAURANT, PlaceType.BAKERY -> "fork"
        PlaceType.PHARMACY -> "cross"
        PlaceType.MARKET, PlaceType.STORE, PlaceType.MALL -> "bag"
        PlaceType.SCHOOL -> "book"
        else -> "star"
    }
}

/** Deslocamento entre duas permanências, pelas calçadas. */
data class CityTrip(
    val index: Int,
    val from: CityStay,
    val to: CityStay,
    val startAt: Long,
    val endAt: Long,
    /** Cantos do caminho (x0, y0, x1, y1…) em px de mundo. */
    val path: IntArray,
    val length: Int,
    val mode: MovementMode?,
) {
    /** Meios que aparecem como veículo na rua; o resto é o Hoodie andando. */
    val vehicle: Vehicle get() = when (mode) {
        MovementMode.BUS, MovementMode.PUBLIC_TRANSPORT, MovementMode.TRAIN, MovementMode.METRO -> Vehicle.BUS
        MovementMode.CAR, MovementMode.VEHICLE_UNKNOWN -> Vehicle.CAR
        else -> Vehicle.WALK
    }
    enum class Vehicle { WALK, CAR, BUS }
    override fun equals(other: Any?) = other is CityTrip && other.index == index && other.startAt == startAt && other.to.nodeId == to.nodeId
    override fun hashCode() = index * 31 + startAt.hashCode()
}

/** O que o Hoodie está fazendo num instante do replay. */
data class CityHoodieState(
    val x: Float, val y: Float,
    val dir: CityTown.Dir,
    val walking: Boolean,
    /** px andados (o quadro da passada vem daqui: nunca desliza). */
    val distance: Float,
    val alpha: Float,
    val inside: CityStay?,
    val stay: CityStay?,
    val trip: CityTrip?,
    /** Balão de reação ao chegar (ícone do lugar). */
    val reaction: String? = null,
    /** Parado na porta (chegando ou saindo). */
    val atDoor: Boolean = false,
    /** Desenhar na frente do prédio enquanto passa pela porta. */
    val baseOverride: Int? = null,
)

/**
 * A Jornada como roteiro da cidade: permanências, trajetos pelas calçadas e o ritmo do replay.
 *
 * O ritmo segue a velocidade escolhida (5 ou 10 min/s) nas permanências, mas no trajeto o relógio
 * acompanha o passo do Hoodie: a caminhada fica legível em qualquer velocidade. Permanências curtas
 * ganham o tempo mínimo para ele chegar, reagir, entrar e sair.
 */
class CityPlan internal constructor(
    val stays: List<CityStay>,
    val trips: List<CityTrip>,
    val placement: Map<String, String>,
    val startAt: Long,
    val endAt: Long,
) {
    private val boundaries: LongArray = (listOf(startAt, endAt) + stays.flatMap { listOf(it.arrivalAt, it.endAt) } + trips.flatMap { listOf(it.startAt, it.endAt) })
        .filter { it in startAt..endAt }.distinct().sorted().toLongArray()

    fun stayAt(ts: Long): CityStay? = stays.lastOrNull { ts >= it.arrivalAt && ts < it.endAt } ?: stays.lastOrNull { ts >= it.arrivalAt && it.departureAt == null && ts >= it.endAt }
    fun tripAt(ts: Long): CityTrip? = trips.firstOrNull { ts >= it.startAt && ts < it.endAt }
    fun staysAt(slotId: String): List<CityStay> = stays.filter { it.slotId == slotId }

    /** Milissegundos de relógio por milissegundo real em [ts]. */
    fun rate(ts: Long, minutesPerSecond: Int): Double {
        val base = minutesPerSecond * 60.0 // ms de relógio por ms real
        tripAt(ts)?.let { trip -> return (trip.endAt - trip.startAt).toDouble() / tripRealMs(trip, minutesPerSecond) }
        stayAt(ts)?.let { stay -> return minOf(base, (stay.endAt - stay.arrivalAt).toDouble() / MIN_STAY_REAL_MS).coerceAtLeast(1.0) }
        return base
    }

    fun tripRealMs(trip: CityTrip, minutesPerSecond: Int): Double = maxOf(MIN_TRIP_REAL_MS, trip.length / walkPxPerSecond(minutesPerSecond) * 1000.0)

    /** Avança o replay [elapsedMs] reais a partir de [ts], respeitando as mudanças de ritmo. */
    fun advance(ts: Long, elapsedMs: Long, minutesPerSecond: Int, limit: Long = endAt): Long {
        var t = ts.toDouble(); var rest = elapsedMs.coerceAtLeast(1).toDouble()
        while (rest > 0 && t < limit) {
            val now = t.toLong()
            val next = boundaries.firstOrNull { it > now } ?: limit
            val r = rate(now, minutesPerSecond)
            val need = (next - t) / r
            if (need >= rest) { t += rest * r; rest = 0.0 } else { t = next.toDouble(); rest -= need }
        }
        return t.toLong().coerceAtMost(limit)
    }

    /** Onde o Hoodie está em [ts]: andando, chegando na porta, dentro do prédio ou saindo. */
    fun hoodieAt(ts: Long, minutesPerSecond: Int, out: FloatArray = FloatArray(2)): CityHoodieState {
        tripAt(ts)?.let { trip ->
            val f = ((ts - trip.startAt).toDouble() / (trip.endAt - trip.startAt).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
            val d = ease(f) * trip.length
            val dir = CityTown.pointAt(trip.path, d, out)
            return CityHoodieState(out[0], out[1], dir, true, d, 1f, null, null, trip)
        }
        val stay = stayAt(ts) ?: stays.firstOrNull() ?: return CityHoodieState(0f, 0f, CityTown.Dir.DOWN, false, 0f, 0f, null, null, null)
        val slot = CityTown.slotById.getValue(stay.slotId)
        val ax = slot.doorX * CityTown.TS + 8f; val ay = slot.frontY * CityTown.TS + 12f
        val r = rate(ts.coerceAtLeast(stay.arrivalAt), minutesPerSecond)
        val sinceArrival = (ts - stay.arrivalAt) / r
        val untilExit = (stay.endAt - ts) / r
        val first = stay.index == 0 && trips.none { it.to == stay }
        val last = stay.departureAt == null || stay.index == stays.lastIndex
        val doorBase = slot.py + slot.ph + 1
        if (!first && sinceArrival < ENTER_MS) {
            if (sinceArrival < 950) return CityHoodieState(ax, ay, CityTown.Dir.UP, false, 0f, 1f, null, stay, null,
                reaction = if (sinceArrival > 200) stay.icon else null, atDoor = true)
            val k = ((sinceArrival - 950) / (ENTER_MS - 950)).toFloat()
            return CityHoodieState(ax, ay - 10 * k, CityTown.Dir.UP, true, 10 * k, 1 - k, null, stay, null, baseOverride = doorBase)
        }
        if (!last && untilExit < EXIT_MS) {
            val k = (1 - untilExit / EXIT_MS).toFloat()
            if (k < 0.55f) { val kk = k / 0.55f; return CityHoodieState(ax, ay - 10 * (1 - kk), CityTown.Dir.DOWN, true, 10 * kk, kk, null, stay, null, baseOverride = doorBase) }
            return CityHoodieState(ax, ay, CityTown.Dir.DOWN, false, 0f, 1f, null, stay, null, atDoor = true)
        }
        return CityHoodieState(ax, ay, CityTown.Dir.UP, false, 0f, 0f, stay, stay, null)
    }

    companion object {
        const val ENTER_MS = 1_500.0
        const val EXIT_MS = 900.0
        const val MIN_STAY_REAL_MS = ENTER_MS + EXIT_MS + 600.0
        const val MIN_TRIP_REAL_MS = 1_200.0

        /** Passo da caminhada (px/s) por velocidade: mais rápido sem ficar ilegível. */
        fun walkPxPerSecond(minutesPerSecond: Int): Double = when {
            minutesPerSecond <= 1 -> 34.0
            minutesPerSecond <= 5 -> 50.0
            else -> 64.0
        }

        /** Acelera na saída e desacelera na chegada, sempre para a frente. */
        fun ease(f: Float): Float = 0.55f * f + 0.45f * (f * f * (3 - 2 * f))

        fun build(data: JourneyMapData): CityPlan? {
            if (data.nodes.isEmpty()) return null
            val placement = CityPlacement.assign(data.nodes)
            val end = maxOf(data.endAt, data.nodes.maxOf { it.departureAt ?: it.arrivalAt })
            val seen = HashMap<String, Int>()
            val stays = data.nodes.sortedBy { it.arrivalAt }.mapIndexed { i, n ->
                val visit = (seen[n.placeKey] ?: 0) + 1; seen[n.placeKey] = visit
                CityStay(i, n.id, placement.getValue(n.placeKey), n.placeName, n.placeType, n.arrivalAt, n.departureAt,
                    n.departureAt ?: end, visit, n.sourceIntervals.size.coerceAtLeast(1))
            }
            val trips = stays.zipWithNext().mapIndexedNotNull { i, (a, b) ->
                val segment = data.segments.firstOrNull { it.fromNodeId == a.nodeId && it.toNodeId == b.nodeId }
                val start = segment?.startedAt ?: a.endAt
                val stop = maxOf(segment?.endedAt ?: b.arrivalAt, start)
                val from = CityTown.slotById.getValue(a.slotId); val to = CityTown.slotById.getValue(b.slotId)
                if (from.id == to.id && stop - start < 60_000) return@mapIndexedNotNull null
                val path = CityTown.route(from.doorX, from.frontY, to.doorX, to.frontY) ?: return@mapIndexedNotNull null
                CityTrip(i, a, b, start, stop, path, CityTown.pathLength(path), segment?.movementMode)
            }
            val startAt = minOf(data.startAt.takeIf { data.endAt > data.startAt } ?: stays.first().arrivalAt, stays.first().arrivalAt)
            return CityPlan(stays, trips, placement, startAt, end)
        }
    }
}
