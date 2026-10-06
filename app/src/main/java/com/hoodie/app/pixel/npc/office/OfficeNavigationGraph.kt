package com.hoodie.app.pixel.npc.office

/** Corredores de circulação do escritório; rotas passam pelo corredor central. */
object OfficeNavigationGraph {
    val spots = mapOf(
        OfficeNpcSpot.DESK_LEFT to OfficeSpot(34, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.DESK_RIGHT to OfficeSpot(211, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.COFFEE to OfficeSpot(75, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.WINDOW to OfficeSpot(35, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.WHITEBOARD to OfficeSpot(138, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.PRINTER to OfficeSpot(183, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.CENTER to OfficeSpot(120, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
        OfficeNpcSpot.DOOR to OfficeSpot(228, 204, com.hoodie.app.pixel.sprite.Facing.SIDE),
    )

    private val aisle = mapOf(
        OfficeNpcSpot.DESK_LEFT to listOf(OfficeNpcSpot.DESK_LEFT, OfficeNpcSpot.WINDOW),
        OfficeNpcSpot.DESK_RIGHT to listOf(OfficeNpcSpot.DESK_RIGHT, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.WINDOW to listOf(OfficeNpcSpot.WINDOW, OfficeNpcSpot.COFFEE),
        OfficeNpcSpot.COFFEE to listOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.CENTER),
        OfficeNpcSpot.CENTER to listOf(OfficeNpcSpot.CENTER, OfficeNpcSpot.WHITEBOARD),
        OfficeNpcSpot.WHITEBOARD to listOf(OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.PRINTER),
        OfficeNpcSpot.PRINTER to listOf(OfficeNpcSpot.PRINTER, OfficeNpcSpot.DOOR),
        OfficeNpcSpot.DOOR to listOf(OfficeNpcSpot.DOOR, OfficeNpcSpot.PRINTER),
    )

    fun route(from: OfficeNpcSpot, to: OfficeNpcSpot): List<OfficeSpot> {
        if (from == to) return listOf(spots.getValue(to))
        val previous = mutableMapOf<OfficeNpcSpot, OfficeNpcSpot?>(); val queue = ArrayDeque<OfficeNpcSpot>()
        previous[from] = null; queue += from
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            val neighbors = aisle[at].orEmpty().filter { it != at } + when (at) {
                OfficeNpcSpot.COFFEE -> listOf(OfficeNpcSpot.WINDOW, OfficeNpcSpot.CENTER)
                OfficeNpcSpot.CENTER -> listOf(OfficeNpcSpot.COFFEE, OfficeNpcSpot.WHITEBOARD, OfficeNpcSpot.DESK_RIGHT)
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
