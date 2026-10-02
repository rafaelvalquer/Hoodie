package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType

/** Ponto em pixels lógicos do mapa (0..[DiaryMapTiles.WIDTH] × 0..[DiaryMapTiles.HEIGHT]). */
data class MapPoint(val x: Float, val y: Float) {
    constructor(x: Int, y: Int) : this(x.toFloat(), y.toFloat())
}

/** Posição em tiles (8×8). */
data class TilePos(val col: Int, val row: Int) {
    /** Centro do tile em pixels lógicos. */
    val center: MapPoint get() = MapPoint(col * DiaryMapTiles.SIZE + DiaryMapTiles.SIZE / 2f, row * DiaryMapTiles.SIZE + DiaryMapTiles.SIZE / 2f)
}

/** Retângulo em tiles, inclusivo. */
data class TileRect(val col: Int, val row: Int, val cols: Int, val rows: Int) {
    val lastCol get() = col + cols - 1
    val lastRow get() = row + rows - 1
    operator fun contains(t: TilePos) = t.col in col..lastCol && t.row in row..lastRow
    fun intersects(o: TileRect) = col <= o.lastCol && o.col <= lastCol && row <= o.lastRow && o.row <= lastRow
    fun grow(n: Int) = TileRect(col - n, row - n, cols + 2 * n, rows + 2 * n)
    /** Retângulo em pixels lógicos: x, y, largura, altura. */
    val pixels: IntArray get() = intArrayOf(col * DiaryMapTiles.SIZE, row * DiaryMapTiles.SIZE, cols * DiaryMapTiles.SIZE, rows * DiaryMapTiles.SIZE)
    operator fun contains(p: MapPoint): Boolean {
        val (x, y, w, h) = pixels.toList()
        return p.x >= x && p.x < x + w && p.y >= y && p.y < y + h
    }
}

/**
 * Um lugar no mapa. Visitas repetidas ao mesmo lugar reutilizam o mesmo nó
 * (a casa aparece uma vez, mesmo saindo e voltando três vezes).
 */
data class DiaryMapPlaceNode(
    val id: String,
    val placeId: Long?,
    val label: String,
    val type: PlaceType,
    /** Prédio (3×3 tiles). */
    val footprint: TileRect,
    /** Tile da calçada em frente à porta: onde o Hoodie chega e espera. */
    val door: TilePos,
    /** Índices (em [DiaryMapLayout.visits]) das visitas a este lugar. */
    val visitIndices: List<Int>,
) {
    val entrance: MapPoint get() = door.center
}

/** Uma visita do dia, na ordem. O id segue o do replay ("visit-<i>"). */
data class DiaryMapVisit(
    val id: String,
    val index: Int,
    val nodeId: String,
    val arrivalAt: Long,
    val departureAt: Long?,
    val durationMs: Long,
)

/** Deslocamento entre duas visitas consecutivas, pelas ruas. O id segue o do replay ("edge-<i>"). */
data class DiaryMapTrip(
    val id: String,
    val index: Int,
    val fromNodeId: String,
    val toNodeId: String,
    /** Caminho em tiles, passo a passo (só movimentos horizontais/verticais). */
    val path: List<TilePos>,
)

data class DiaryMapLayout(
    val nodes: List<DiaryMapPlaceNode>,
    val visits: List<DiaryMapVisit>,
    val trips: List<DiaryMapTrip>,
) {
    val isEmpty get() = nodes.isEmpty()
    fun node(id: String?) = nodes.firstOrNull { it.id == id }
    fun nodeOfVisit(visitIndex: Int) = visits.getOrNull(visitIndex)?.let { node(it.nodeId) }

    /** Hitbox: o prédio inteiro e a calçada da porta. */
    fun nodeAt(p: MapPoint): DiaryMapPlaceNode? =
        nodes.firstOrNull { p in it.footprint || p in TileRect(it.door.col, it.door.row, 1, 1) }

    companion object {
        val EMPTY = DiaryMapLayout(emptyList(), emptyList(), emptyList())
    }
}
