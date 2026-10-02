package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.HoodiePainter.WIDTH

internal data class R(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int = 2, val round: Boolean = false) {
    fun dy(d: Int) = copy(y0 = y0 + d, y1 = y1 + d)
    fun dx(d: Int) = copy(x0 = x0 + d, x1 = x1 + d)
    fun mirror() = copy(x0 = WIDTH - 1 - x1, x1 = WIDTH - 1 - x0)
    fun inside(x: Int, y: Int): Boolean {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false
        val dx = minOf(x - x0, x1 - x)
        val dy = minOf(y - y0, y1 - y)
        if (!round) return dx + dy >= r
        if (dx >= r || dy >= r) return true
        val ex = r - dx - 0.5; val ey = r - dy - 0.5
        return ex * ex + ey * ey <= r * r
    }
}

internal class ArmShape(val parts: List<R>, val paw: R, val hx: Int, val hy: Int)

internal val STRIDE_LIFT = intArrayOf(0, 0, 0, 0, 0, 1, 2, 1)
internal val STRIDE_X = intArrayOf(-5, -3, -1, 2, 5, 3, 0, -3)
internal val ARM_SWING = intArrayOf(3, 2, 0, -2, -3, -2, 0, 2)

