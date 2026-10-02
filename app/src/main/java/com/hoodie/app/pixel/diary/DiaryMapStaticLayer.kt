package com.hoodie.app.pixel.diary

import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Camada estática da cidade: grama, ruas, calçadas, árvores, arbustos, flores,
 * bancos, base do lago e postes apagados. Depende só do [DiaryMapLayout] — é
 * desenhada uma vez por layout e copiada a cada quadro.
 */
object DiaryMapStaticLayer {

    /** Tiles que mudam com o tempo/horário: ficam de fora (ou só com a base) e são animados na camada dinâmica. */
    val ANIMATED = setOf(Tile.WATER, Tile.LAMP)

    fun render(layout: DiaryMapLayout, decorations: Map<TilePos, Tile>): PixelBuffer {
        val out = PixelBuffer(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT)
        for (row in 0 until DiaryMapTiles.ROWS) for (col in 0 until DiaryMapTiles.COLS) {
            val pos = TilePos(col, row)
            // Terreno de prédio vira grama: o prédio é desenhado por cima, com estado.
            val tile = if (layout.nodes.any { pos in it.footprint }) Tile.GRASS else decorations[pos] ?: DiaryMapTiles.baseTile(col, row)
            when (tile) {
                Tile.WATER -> DiaryMapTiles.paintWaterBase(out, col, row)
                else -> DiaryMapTiles.paint(out, tile, col, row, lightsOn = false, timeMs = 0)
            }
        }
        return out
    }
}

/** Cache por layout: a camada estática e a decoração são calculadas uma vez. */
data class DiaryMapRenderCache(
    val layoutKey: Int,
    val staticLayer: PixelBuffer,
    val decorations: Map<TilePos, Tile>,
) {
    /** Tiles animados (água, postes) — a camada dinâmica só percorre estes. */
    val animatedTiles: List<Pair<TilePos, Tile>> = decorations.filter { it.value in DiaryMapStaticLayer.ANIMATED }.toList()

    fun matches(layout: DiaryMapLayout) = layoutKey == layout.hashCode()

    companion object {
        fun create(layout: DiaryMapLayout): DiaryMapRenderCache {
            DiaryMapPerf.staticBuilds++
            val decorations = DiaryMapDecoration.tiles(layout)
            return DiaryMapRenderCache(layout.hashCode(), DiaryMapStaticLayer.render(layout, decorations), decorations)
        }
    }
}

/** Números do mapa para o Developer Lab (DIARY MAP PERFORMANCE). */
object DiaryMapPerf {
    @Volatile var staticBuilds = 0
    @Volatile var cacheHits = 0
    @Volatile var lastRenderNanos = 0L
    @Volatile var lastCacheHit = false
    private val frameTimes = ArrayDeque<Long>()

    /** Quadros renderizados no último segundo. */
    val fpsActual: Int get() = synchronized(frameTimes) { frameTimes.size }

    fun frameRendered(nowNanos: Long) = synchronized(frameTimes) {
        frameTimes.addLast(nowNanos)
        while (frameTimes.isNotEmpty() && nowNanos - frameTimes.first() > 1_000_000_000L) frameTimes.removeFirst()
    }
}
