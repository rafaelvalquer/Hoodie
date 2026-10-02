package com.hoodie.app.pixel.diary

import kotlin.math.abs
import kotlin.math.sign

/**
 * Caminhos "Manhattan" pelas ruas: porta → desce para a rua → anda na horizontal
 * até uma rua vertical → muda de fileira → horizontal até a porta de destino.
 * Ruas verticais só existem nos vãos entre terrenos, então o caminho nunca
 * atravessa um prédio; o desvio fica em 1–2 tiles (sair e voltar da calçada).
 */
object DiaryRoadBuilder {

    fun route(from: TilePos, to: TilePos): List<TilePos> {
        val path = mutableListOf(from)
        if (from == to) return path
        val startStreet = from.row + 1
        val endStreet = to.row + 1
        fun goTo(col: Int, row: Int) {
            var cur = path.last()
            while (cur.col != col) { cur = TilePos(cur.col + (col - cur.col).sign, cur.row); path += cur }
            while (cur.row != row) { cur = TilePos(cur.col, cur.row + (row - cur.row).sign); path += cur }
        }
        goTo(from.col, startStreet)
        if (startStreet != endStreet) {
            val v = DiaryMapTiles.STREET_COLS.minWith(compareBy<Int> { abs(it - from.col) + abs(it - to.col) }.thenBy { it })
            goTo(v, startStreet)
            goTo(v, endStreet)
        }
        goTo(to.col, endStreet)
        goTo(to.col, to.row)
        return path
    }

    /** Ponto ao longo do caminho para [progress] 0..1 (pela distância, não por tile). */
    fun pointAt(path: List<TilePos>, progress: Float): MapPoint {
        if (path.isEmpty()) return MapPoint(0f, 0f)
        if (path.size == 1) return path[0].center
        val steps = path.size - 1
        val t = progress.coerceIn(0f, 1f) * steps
        val i = t.toInt().coerceAtMost(steps - 1)
        val f = t - i
        val a = path[i].center; val b = path[i + 1].center
        return MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    /** Direção do passo atual (dx, dy em tiles) para [progress]. */
    fun stepAt(path: List<TilePos>, progress: Float): Pair<Int, Int> {
        if (path.size < 2) return 0 to 0
        val steps = path.size - 1
        val i = (progress.coerceIn(0f, 1f) * steps).toInt().coerceAtMost(steps - 1)
        return (path[i + 1].col - path[i].col) to (path[i + 1].row - path[i].row)
    }
}
