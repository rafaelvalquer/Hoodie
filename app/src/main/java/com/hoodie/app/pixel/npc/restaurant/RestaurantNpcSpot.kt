package com.hoodie.app.pixel.npc.restaurant

import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.abs

enum class RestaurantNpcSpot {
    TABLE_A_SEAT_LEFT,
    TABLE_A_SEAT_RIGHT,
    TABLE_B_SEAT_LEFT,
    TABLE_B_SEAT_RIGHT,
    COUNTER,
    WINDOW,
    AISLE,
    DOOR,
}

data class RestaurantSeat(
    val x: Int,
    val floorY: Int,
    val facing: Facing,
    val tableId: String,
)

data class RestaurantWaypoint(val x: Int, val floorY: Int, val facing: Facing = Facing.SIDE)

/** Posições e caminhos semânticos: os percursos usam o corredor à direita da mesa principal. */
object RestaurantNavigationGraph {
    val spots = mapOf(
        RestaurantNpcSpot.TABLE_A_SEAT_LEFT to RestaurantSeat(184, 253, Facing.SIDE, "table_a"),
        RestaurantNpcSpot.TABLE_A_SEAT_RIGHT to RestaurantSeat(201, 253, Facing.SIDE, "table_a"),
        RestaurantNpcSpot.TABLE_B_SEAT_LEFT to RestaurantSeat(72, 224, Facing.SIDE, "table_b"),
        RestaurantNpcSpot.TABLE_B_SEAT_RIGHT to RestaurantSeat(98, 224, Facing.SIDE, "table_b"),
        RestaurantNpcSpot.COUNTER to RestaurantSeat(80, 184, Facing.SIDE, "counter"),
        RestaurantNpcSpot.WINDOW to RestaurantSeat(202, 112, Facing.SIDE, "window"),
        RestaurantNpcSpot.AISLE to RestaurantSeat(222, 220, Facing.SIDE, "aisle"),
        // Fora da tela à direita; o último trecho atravessa a porta desenhada no cenário.
        RestaurantNpcSpot.DOOR to RestaurantSeat(264, 184, Facing.SIDE, "door"),
    )

    private val links = mapOf(
        RestaurantNpcSpot.DOOR to listOf(RestaurantNpcSpot.AISLE),
        RestaurantNpcSpot.AISLE to listOf(
            RestaurantNpcSpot.DOOR, RestaurantNpcSpot.TABLE_A_SEAT_LEFT,
            RestaurantNpcSpot.TABLE_A_SEAT_RIGHT, RestaurantNpcSpot.WINDOW, RestaurantNpcSpot.COUNTER,
        ),
        RestaurantNpcSpot.TABLE_A_SEAT_LEFT to listOf(RestaurantNpcSpot.AISLE),
        RestaurantNpcSpot.TABLE_A_SEAT_RIGHT to listOf(RestaurantNpcSpot.AISLE),
        RestaurantNpcSpot.WINDOW to listOf(RestaurantNpcSpot.AISLE, RestaurantNpcSpot.COUNTER),
        RestaurantNpcSpot.COUNTER to listOf(
            RestaurantNpcSpot.AISLE, RestaurantNpcSpot.WINDOW,
            RestaurantNpcSpot.TABLE_B_SEAT_LEFT, RestaurantNpcSpot.TABLE_B_SEAT_RIGHT,
        ),
        RestaurantNpcSpot.TABLE_B_SEAT_LEFT to listOf(RestaurantNpcSpot.COUNTER),
        RestaurantNpcSpot.TABLE_B_SEAT_RIGHT to listOf(RestaurantNpcSpot.COUNTER),
    )

    fun route(from: RestaurantNpcSpot, to: RestaurantNpcSpot): List<RestaurantWaypoint> {
        if (from == to) return listOf(spots.getValue(to).waypoint())
        val previous = mutableMapOf<RestaurantNpcSpot, RestaurantNpcSpot?>().apply { put(from, null) }
        val queue = ArrayDeque<RestaurantNpcSpot>().apply { addLast(from) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in links[current].orEmpty()) if (next !in previous) {
                previous[next] = current
                if (next == to) {
                    val path = mutableListOf(to)
                    var cursor = to
                    while (previous[cursor] != null) {
                        cursor = requireNotNull(previous[cursor])
                        path += cursor
                    }
                    return path.asReversed().map { spots.getValue(it).waypoint() }
                }
                queue.addLast(next)
            }
        }
        error("rota de restaurante inexistente: $from -> $to")
    }

    fun travelDistance(from: RestaurantNpcSpot, to: RestaurantNpcSpot): Int =
        route(from, to).zipWithNext().sumOf { (a, b) -> maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY)) }

    private fun RestaurantSeat.waypoint() = RestaurantWaypoint(x, floorY, facing)
}
