package com.hoodie.app.pixel.npc.office

import java.util.concurrent.ConcurrentHashMap

/** Corredores de circulação do escritório; rotas passam pelo corredor central. */
object OfficeNavigationGraph {
    val spots = mapOf(
        OfficeNpcSpot.DESK_LEFT to OfficeSpot(34, 204, com.hoodie.app.pixel.sprite.Facing.FRONT),
        OfficeNpcSpot.WINDOW to OfficeSpot(60, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.COFFEE to OfficeSpot(86, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.CENTER to OfficeSpot(112, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.CENTER_LEFT to OfficeSpot(100, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.CENTER_RIGHT to OfficeSpot(124, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.WHITEBOARD to OfficeSpot(152, 198, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.PRINTER to OfficeSpot(180, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.DESK_RIGHT to OfficeSpot(206, 204, com.hoodie.app.pixel.sprite.Facing.FRONT),
        OfficeNpcSpot.DOOR to OfficeSpot(234, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
    )

    private val aisle = mapOf(
        OfficeNpcSpot.DESK_LEFT to listOf(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.WINDOW),
        OfficeNpcSpot.DESK_RIGHT to listOf(OfficeNpcSpot.DESK_RIGHT, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.WINDOW to listOf(OfficeNpcSpot.WINDOW, OfficeNpcSpot.COFFEE),
        OfficeNpcSpot.COFFEE to listOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.CENTER to listOf(OfficeNpcSpot.CENTER, OfficeNpcSpot.WHITEBOARD),
        OfficeNpcSpot.CENTER_LEFT to listOf(OfficeNpcSpot.CENTER_LEFT, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.CENTER_RIGHT to listOf(OfficeNpcSpot.CENTER_RIGHT, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.WHITEBOARD to listOf(OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.PRINTER),
        OfficeNpcSpot.PRINTER to listOf(OfficeNpcSpot.PRINTER, OfficeNpcSpot.DOOR),
        OfficeNpcSpot.DOOR to listOf(OfficeNpcSpot.DOOR, OfficeNpcSpot.PRINTER),
    )

    /** Immutable topology: route computation allocates, so cache by the collision-safe spot pair. */
    private val routeCache = ConcurrentHashMap<Int, List<OfficeSpot>>(OfficeNpcSpot.entries.size * OfficeNpcSpot.entries.size)

    fun route(from: OfficeNpcSpot, to: OfficeNpcSpot): List<OfficeSpot> {
        val key = from.ordinal * OfficeNpcSpot.entries.size + to.ordinal
        return routeCache[key] ?: computeRoute(from, to).let { computed ->
            routeCache.putIfAbsent(key, computed) ?: computed
        }
    }

    private fun computeRoute(from: OfficeNpcSpot, to: OfficeNpcSpot): List<OfficeSpot> {
        if (from == to) return listOf(spots.getValue(to))
        if (from == OfficeNpcSpot.DESK_RIGHT && to == OfficeNpcSpot.WHITEBOARD) {
            return listOf(OfficeNpcSpot.DESK_RIGHT, OfficeNpcSpot.PRINTER, OfficeNpcSpot.WHITEBOARD).map(spots::getValue)
        }
        if (from == OfficeNpcSpot.WHITEBOARD && to == OfficeNpcSpot.DESK_RIGHT) {
            return listOf(OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.PRINTER, OfficeNpcSpot.DESK_RIGHT).map(spots::getValue)
        }
        val previous = mutableMapOf<OfficeNpcSpot, OfficeNpcSpot?>(); val queue = ArrayDeque<OfficeNpcSpot>()
        previous[from] = null; queue += from
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            val neighbors = aisle[at].orEmpty().filter { it != at } + when (at) {
                OfficeNpcSpot.COFFEE -> listOf(OfficeNpcSpot.WINDOW, OfficeNpcSpot.CENTER)
                OfficeNpcSpot.CENTER -> listOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.DESK_RIGHT)
                OfficeNpcSpot.CENTER_LEFT -> listOf(OfficeNpcSpot.CENTER)
                OfficeNpcSpot.CENTER_RIGHT -> listOf(OfficeNpcSpot.CENTER)
                OfficeNpcSpot.WHITEBOARD -> listOf(OfficeNpcSpot.CENTER, OfficeNpcSpot.PRINTER)
                OfficeNpcSpot.PRINTER -> listOf(OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.DOOR)
                OfficeNpcSpot.WINDOW -> listOf(OfficeNpcSpot.COFFEE)
                else -> emptyList()
            }
            for (next in neighbors) if (next !in previous) {
                previous[next] = at
                if (next == to) {
                    val path = mutableListOf(to); var cursor = to
                    while (previous[cursor] != null) { cursor = previous.getValue(cursor)!!; path += cursor }
                    return path.asReversed().map(spots::getValue)
                }
                queue += next
            }
        }
        return listOf(spots.getValue(from), spots.getValue(to))
    }
}
