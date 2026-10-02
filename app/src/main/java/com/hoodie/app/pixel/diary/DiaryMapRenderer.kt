package com.hoodie.app.pixel.diary

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.DiaryMapPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Tudo o que o renderer precisa para um quadro do mapa. */
data class DiaryMapScene(
    val layout: DiaryMapLayout,
    /** Replay em andamento (ou pausado) — sem replay, o mapa mostra o dia inteiro. */
    val replaying: Boolean = false,
    /** Visita em que o Hoodie está agora ("visit-<i>" → i). */
    val activeVisitIndex: Int? = null,
    /** Deslocamento em andamento ("edge-<i>" → i) e quanto já andou. */
    val activeTripIndex: Int? = null,
    val tripProgress: Float = 0f,
    val period: DayPeriod = DayPeriod.DAY,
    val timeMs: Long = 0L,
) {
    /** Até que visita o dia já chegou no replay. */
    val reachedVisit: Int get() = activeVisitIndex ?: activeTripIndex ?: -1

    companion object {
        /** Converte os ids do replay ("visit-3", "edge-2") em índices. */
        fun indexOf(id: String?, prefix: String): Int? = id?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
    }
}

/**
 * Desenha a cidade do diário em 240×160 lógicos: grade, decoração, caminhos do
 * dia, prédios com estado, efeitos, horário (no replay) e o mini Hoodie.
 * Mesma cena → mesmos pixels.
 */
object DiaryMapRenderer {

    fun render(scene: DiaryMapScene, out: PixelBuffer = PixelBuffer(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT)): PixelBuffer {
        val layout = scene.layout
        val lightsOn = scene.period == DayPeriod.EVENING || scene.period == DayPeriod.NIGHT
        val decor = DiaryMapDecoration.tiles(layout)
        for (row in 0 until DiaryMapTiles.ROWS) for (col in 0 until DiaryMapTiles.COLS) {
            if (layout.nodes.any { TilePos(col, row) in it.footprint }) { DiaryMapTiles.paint(out, Tile.GRASS, col, row, lightsOn, scene.timeMs); continue }
            val tile = decor[TilePos(col, row)] ?: DiaryMapTiles.baseTile(col, row)
            DiaryMapTiles.paint(out, tile, col, row, lightsOn, scene.timeMs)
        }
        layout.trips.forEach { trip -> paintTrip(out, trip, tripStyle(scene, trip.index)) }
        layout.nodes.forEach { node -> DiaryMapBuildings.paint(out, node, buildingState(scene, node), lightsOn, scene.timeMs) }
        DiaryMapDecoration.effects(out, layout, lightsOn, scene.timeMs)
        P.applyTint(out, scene.period)
        if (lightsOn) decor.forEach { (t, tile) -> if (tile == Tile.LAMP) out.disc(t.col * DiaryMapTiles.SIZE + 4, t.row * DiaryMapTiles.SIZE + 1, 1, P.LAMP_LIGHT and 0x70FFFFFF) }
        marker(scene)?.let { DiaryHoodieMarker.paint(out, it, scene.timeMs) }
        return out
    }

    fun buildingState(scene: DiaryMapScene, node: DiaryMapPlaceNode): BuildingState = when {
        !scene.replaying -> BuildingState.VISITED
        scene.activeVisitIndex != null && scene.activeVisitIndex in node.visitIndices -> BuildingState.ACTIVE
        node.visitIndices.first() <= scene.reachedVisit -> BuildingState.VISITED
        else -> BuildingState.UPCOMING
    }

    enum class TripStyle { DONE, ACTIVE, HIDDEN }

    fun tripStyle(scene: DiaryMapScene, index: Int): TripStyle = when {
        !scene.replaying -> TripStyle.DONE
        index == scene.activeTripIndex -> TripStyle.ACTIVE
        index < scene.reachedVisit -> TripStyle.DONE
        else -> TripStyle.HIDDEN
    }

    /** Onde o mini Hoodie está: andando na rua, parado na porta, ou onde o dia terminou. */
    fun marker(scene: DiaryMapScene): MarkerState? {
        val layout = scene.layout
        if (layout.isEmpty) return null
        if (scene.replaying) {
            scene.activeTripIndex?.let { i -> layout.trips.getOrNull(i)?.let { return DiaryHoodieMarker.onTrip(it, scene.tripProgress) } }
            scene.activeVisitIndex?.let { i -> layout.nodeOfVisit(i)?.let { return DiaryHoodieMarker.atNode(it) } }
        }
        return layout.nodeOfVisit(layout.visits.lastIndex)?.let(DiaryHoodieMarker::atNode)
    }

    private fun paintTrip(b: PixelBuffer, trip: DiaryMapTrip, style: TripStyle) {
        if (style == TripStyle.HIDDEN || trip.path.size < 2) return
        val color = if (style == TripStyle.ACTIVE) P.PATH_ACTIVE else P.PATH and 0xB0FFFFFF.toInt()
        // Pontilhado ao longo do centro dos tiles: dá para seguir o caminho sem esconder a rua.
        var step = 0
        trip.path.zipWithNext().forEach { (a, c) ->
            val ax = a.center.x.toInt(); val ay = a.center.y.toInt(); val cx = c.center.x.toInt(); val cy = c.center.y.toInt()
            val n = maxOf(kotlin.math.abs(cx - ax), kotlin.math.abs(cy - ay))
            for (k in 0 until n) {
                if (step++ % 3 == 2) continue
                val x = ax + (cx - ax) * k / n; val y = ay + (cy - ay) * k / n
                b.set(x, y, color)
                if (style == TripStyle.ACTIVE) b.set(x, y - 1, color)
            }
        }
    }
}
