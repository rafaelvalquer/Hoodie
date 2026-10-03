package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.PlaceVisit
import kotlin.math.abs

/**
 * Monta a cidade do dia de forma determinística: a mesma lista de visitas gera
 * sempre o mesmo mapa. Cada lugar vira UM prédio (visitas repetidas reutilizam o
 * nó) num terreno escolhido pela preferência do tipo — casa embaixo à esquerda,
 * trabalho em cima à direita, restaurante no centro, academia embaixo à direita —
 * ou no terreno livre mais próximo dela.
 */
object DiaryMapLayoutEngine {

    /** Terreno preferido (coluna, linha da grade de terrenos 5×3). */
    private val PREFERRED: Map<PlaceType, Pair<Int, Int>> = mapOf(
        PlaceType.HOME to (0 to 2),
        PlaceType.WORK to (4 to 0),
        PlaceType.RESTAURANT to (2 to 1),
        PlaceType.GYM to (4 to 2),
        PlaceType.MARKET to (0 to 0),
        PlaceType.STORE to (4 to 1),
        PlaceType.SCHOOL to (2 to 0),
        PlaceType.LEISURE to (3 to 1),
        PlaceType.FAMILY to (1 to 2),
        PlaceType.OTHER to (1 to 1),
    )

    private const val LOT_COLUMNS = 5

    const val OVERFLOW_ID = "place-overflow"

    fun layout(visits: List<PlaceVisit>): DiaryMapLayout {
        if (visits.isEmpty()) return DiaryMapLayout.EMPTY
        val lots = DiaryMapTiles.LOTS
        // 1ª passada: lugares únicos na ordem da primeira visita.
        val unique = LinkedHashMap<String, PlaceVisit>()
        visits.forEach { unique.putIfAbsent(placeKey(it), it) }
        // Mais lugares que terrenos: os últimos dividem um prédio "Outros lugares".
        val overflow = unique.size > lots.size
        val ownLot = if (overflow) unique.keys.take(lots.size - 1).toSet() else unique.keys
        val nodeOf: (String) -> String = { key -> if (key in ownLot) "place-$key" else OVERFLOW_ID }

        val taken = BooleanArray(lots.size)
        val lotOf = LinkedHashMap<String, Int>()
        unique.forEach { (key, first) ->
            val id = nodeOf(key)
            if (id in lotOf) return@forEach
            val lot = chooseLot(if (id == OVERFLOW_ID) PlaceType.OTHER else first.placeType, taken)!!
            taken[lot] = true
            lotOf[id] = lot
        }
        val nodes = lotOf.map { (id, lot) ->
            val first = unique.entries.first { nodeOf(it.key) == id }.value
            val isOverflow = id == OVERFLOW_ID
            DiaryMapPlaceNode(
                id = id,
                placeId = if (isOverflow) null else first.placeId,
                label = if (isOverflow) "Outros lugares" else first.placeName,
                type = if (isOverflow) PlaceType.OTHER else first.placeType,
                footprint = lots[lot],
                door = DiaryMapTiles.door(lots[lot]),
                visitIndices = visits.indices.filter { nodeOf(placeKey(visits[it])) == id },
            )
        }
        val keyToNode = unique.keys.associateWith(nodeOf)
        val visitModels = visits.mapIndexed { i, v ->
            DiaryMapVisit("visit-$i", i, keyToNode.getValue(placeKey(v)), v.arrivalAt, v.departureAt, v.durationMs)
        }
        val byId = nodes.associateBy { it.id }
        val trips = visitModels.zipWithNext().mapIndexed { i, (a, b) ->
            DiaryMapTrip("edge-$i", i, a.nodeId, b.nodeId, DiaryRoadBuilder.route(byId.getValue(a.nodeId).door, byId.getValue(b.nodeId).door))
        }
        return DiaryMapLayout(nodes, visitModels, trips)
    }

    /** Mesmo lugar = mesmo id cadastrado; sem id, mesmo nome e tipo. */
    fun placeKey(v: PlaceVisit): String = v.placeId?.let { "id$it" } ?: "${v.placeType.name.lowercase()}-${v.placeName.trim().lowercase()}"

    private fun chooseLot(type: PlaceType, taken: BooleanArray): Int? {
        val (pc, pr) = PREFERRED.getValue(type)
        return DiaryMapTiles.LOTS.indices
            .filter { !taken[it] }
            .minWithOrNull(compareBy<Int> { abs(it % LOT_COLUMNS - pc) + abs(it / LOT_COLUMNS - pr) }.thenBy { it })
    }
}
