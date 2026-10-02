package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.DiaryMapPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Vida da cidade: parques nos terrenos vazios, lago, postes nas calçadas e
 * pequenos efeitos animados (fumaça da chaminé, placa piscando, carro passando).
 * Tudo determinístico — a posição de cada árvore depende só do tile.
 */
object DiaryMapDecoration {

    /** Tiles decorados por cima da grade, para um layout. Nunca cobre ruas, portas ou prédios. */
    fun tiles(layout: DiaryMapLayout): Map<TilePos, Tile> {
        val out = HashMap<TilePos, Tile>()
        val used = layout.nodes.map { it.footprint }
        val doors = layout.nodes.map { it.door }.toSet()
        // Parques nos terrenos livres.
        DiaryMapTiles.LOTS.forEachIndexed { i, lot ->
            if (used.any { it == lot }) return@forEachIndexed
            val pattern = when (i % 3) {
                0 -> listOf(Tile.TREE, Tile.FLOWER, Tile.TREE, Tile.BUSH, Tile.BENCH, Tile.FLOWER, Tile.TREE, Tile.GRASS, Tile.TREE)
                1 -> listOf(Tile.BUSH, Tile.TREE, Tile.BUSH, Tile.FLOWER, Tile.GRASS, Tile.FLOWER, Tile.TREE, Tile.BENCH, Tile.TREE)
                else -> listOf(Tile.TREE, Tile.GRASS, Tile.TREE, Tile.GRASS, Tile.FLOWER, Tile.GRASS, Tile.BUSH, Tile.TREE, Tile.BUSH)
            }
            for (r in 0 until 3) for (c in 0 until 3) out[TilePos(lot.col + c, lot.row + r)] = pattern[r * 3 + c]
        }
        for (row in 0 until DiaryMapTiles.ROWS) for (col in 0 until DiaryMapTiles.COLS) {
            val t = TilePos(col, row)
            val base = DiaryMapTiles.baseTile(col, row)
            if (t in out) continue
            when {
                // Lago no parque de baixo.
                row >= 18 && col in 9..16 -> out[t] = Tile.WATER
                base == Tile.SIDEWALK && col % 6 == 4 && t !in doors -> out[t] = Tile.LAMP
                base == Tile.GRASS && used.none { t in it } && DiaryMapTiles.LOTS.none { t in it } -> {
                    when ((col * 7 + row * 13) % 11) {
                        0, 6 -> out[t] = Tile.TREE
                        3 -> out[t] = Tile.BUSH
                        8 -> out[t] = Tile.FLOWER
                    }
                }
            }
        }
        return out
    }

    /** Efeitos animados por cima dos prédios. */
    fun effects(b: PixelBuffer, layout: DiaryMapLayout, lightsOn: Boolean, timeMs: Long) {
        layout.nodes.forEach { n ->
            val x = n.footprint.col * DiaryMapTiles.SIZE; val y = n.footprint.row * DiaryMapTiles.SIZE
            when (n.type) {
                PlaceType.HOME -> smoke(b, x + 17, y - 1, timeMs)
                PlaceType.RESTAURANT, PlaceType.MARKET, PlaceType.LEISURE -> {
                    // Placa piscando (mais visível à noite).
                    if ((timeMs / 700) % 2 == 0L || !lightsOn) return@forEach
                    b.set(x + 6, y + 2, P.LAMP_LIGHT); b.set(x + 16, y + 2, P.LAMP_LIGHT)
                }
                else -> Unit
            }
        }
        car(b, timeMs)
    }

    /** Três baforadas subindo e sumindo a partir de (x, y). */
    fun smoke(b: PixelBuffer, x: Int, y: Int, timeMs: Long) {
        for (k in 0 until 3) {
            val t = ((timeMs / 120 + k * 9) % 27).toInt() // 0..26
            val py = y - t / 3
            if (py < 0) continue
            val px = x + listOf(0, 1, 1, 0, -1)[(t / 5) % 5]
            val c = P.SMOKE and (((26 - t) * 255 / 26).coerceIn(60, 230) shl 24 or 0xFFFFFF)
            b.set(px, py, c); b.set(px + 1, py, c)
        }
    }

    /** Um carro cruza a rua do meio de vez em quando (6 s a cada 14 s). */
    fun car(b: PixelBuffer, timeMs: Long) {
        val cycle = timeMs % 14_000
        if (cycle >= 6_000) return
        val row = DiaryMapTiles.STREET_ROWS[1]
        val x = (cycle * (DiaryMapTiles.WIDTH + 12) / 6_000).toInt() - 8
        val y = row * DiaryMapTiles.SIZE + 1
        val color = if ((timeMs / 14_000) % 2 == 0L) P.CAR_A else P.CAR_B
        b.box(x, y, x + 6, y + 3, P.OUTLINE)
        b.box(x + 1, y, x + 5, y + 2, color)
        b.box(x + 3, y, x + 4, y + 1, P.WINDOW)
        b.set(x + 1, y + 3, P.OUTLINE); b.set(x + 5, y + 3, P.OUTLINE)
    }
}
