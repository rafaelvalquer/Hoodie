package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.abs

data class ShoppingNpcPoint(val x: Int, val floorY: Int, val facing: Facing = Facing.SIDE, val facesRight: Boolean = true)

/** Caminhos pelo corredor livre à frente das gôndolas; as rotas não cruzam as prateleiras. */
object ShoppingNavigationGraph {
    val spots = mapOf(
        ShoppingNpcSpot.OFFSCREEN to ShoppingNpcPoint(-26, 160),
        ShoppingNpcSpot.DOOR to ShoppingNpcPoint(26, 160),
        ShoppingNpcSpot.CENTER to ShoppingNpcPoint(122, 214),
        ShoppingNpcSpot.AISLE_A_START to ShoppingNpcPoint(42, 214),
        ShoppingNpcSpot.AISLE_A_MIDDLE to ShoppingNpcPoint(82, 214),
        ShoppingNpcSpot.AISLE_A_END to ShoppingNpcPoint(120, 214),
        ShoppingNpcSpot.AISLE_B_START to ShoppingNpcPoint(136, 214, facesRight = false),
        ShoppingNpcSpot.AISLE_B_MIDDLE to ShoppingNpcPoint(173, 214, facesRight = false),
        ShoppingNpcSpot.AISLE_B_END to ShoppingNpcPoint(210, 214, facesRight = false),
        ShoppingNpcSpot.PROMOTION_SIGN to ShoppingNpcPoint(214, 206, facesRight = false),
        ShoppingNpcSpot.CART_AREA to ShoppingNpcPoint(112, 218),
        ShoppingNpcSpot.CHECKOUT_QUEUE to ShoppingNpcPoint(158, 228),
        ShoppingNpcSpot.CHECKOUT_COUNTER to ShoppingNpcPoint(158, 250),
    )

    private val edges = mapOf(
        ShoppingNpcSpot.OFFSCREEN to listOf(ShoppingNpcSpot.DOOR),
        ShoppingNpcSpot.DOOR to listOf(ShoppingNpcSpot.OFFSCREEN, ShoppingNpcSpot.AISLE_A_START),
        ShoppingNpcSpot.AISLE_A_START to listOf(ShoppingNpcSpot.DOOR, ShoppingNpcSpot.AISLE_A_MIDDLE),
        ShoppingNpcSpot.AISLE_A_MIDDLE to listOf(ShoppingNpcSpot.AISLE_A_START, ShoppingNpcSpot.AISLE_A_END),
        ShoppingNpcSpot.AISLE_A_END to listOf(ShoppingNpcSpot.AISLE_A_MIDDLE, ShoppingNpcSpot.CENTER),
        ShoppingNpcSpot.CENTER to listOf(ShoppingNpcSpot.AISLE_A_END, ShoppingNpcSpot.AISLE_B_START, ShoppingNpcSpot.CART_AREA),
        ShoppingNpcSpot.AISLE_B_START to listOf(ShoppingNpcSpot.CENTER, ShoppingNpcSpot.AISLE_B_MIDDLE),
        ShoppingNpcSpot.AISLE_B_MIDDLE to listOf(ShoppingNpcSpot.AISLE_B_START, ShoppingNpcSpot.AISLE_B_END),
        ShoppingNpcSpot.AISLE_B_END to listOf(ShoppingNpcSpot.AISLE_B_MIDDLE, ShoppingNpcSpot.PROMOTION_SIGN),
        ShoppingNpcSpot.PROMOTION_SIGN to listOf(ShoppingNpcSpot.AISLE_B_END),
        ShoppingNpcSpot.CART_AREA to listOf(ShoppingNpcSpot.CENTER, ShoppingNpcSpot.CHECKOUT_QUEUE),
        ShoppingNpcSpot.CHECKOUT_QUEUE to listOf(ShoppingNpcSpot.CART_AREA, ShoppingNpcSpot.CHECKOUT_COUNTER),
        ShoppingNpcSpot.CHECKOUT_COUNTER to listOf(ShoppingNpcSpot.CHECKOUT_QUEUE),
    )

    fun route(from: ShoppingNpcSpot, to: ShoppingNpcSpot): List<ShoppingNpcSpot> {
        if (from == to) return listOf(to)
        val previous = mutableMapOf<ShoppingNpcSpot, ShoppingNpcSpot?>(from to null)
        val queue = ArrayDeque<ShoppingNpcSpot>().apply { addLast(from) }
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            for (next in edges[at].orEmpty()) if (next !in previous) {
                previous[next] = at
                if (next == to) {
                    val path = mutableListOf(to)
                    var cursor = to
                    while (previous[cursor] != null) { cursor = previous.getValue(cursor)!!; path += cursor }
                    return path.asReversed()
                }
                queue.addLast(next)
            }
        }
        error("Sem rota entre $from e $to")
    }

    fun distance(a: ShoppingNpcSpot, b: ShoppingNpcSpot): Int {
        val p = spots.getValue(a); val q = spots.getValue(b)
        return maxOf(abs(q.x - p.x), abs(q.floorY - p.floorY))
    }

    fun isAdjacent(a: ShoppingNpcSpot, b: ShoppingNpcSpot): Boolean = b in edges[a].orEmpty()
}
