package com.hoodie.app.pixel.diary

import com.hoodie.app.pixel.diary.DiaryMapPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer

enum class Tile { GRASS, ROAD_H, ROAD_V, ROAD_T, ROAD_CROSS, ROAD_CORNER, SIDEWALK, TREE, BUSH, LAMP, BENCH, FLOWER, WATER }

/**
 * Grade da cidade: 30×20 tiles de 8×8 (240×160 lógicos). Três fileiras de
 * terrenos 3×3; abaixo de cada fileira, calçada (onde ficam as portas) e uma rua.
 * Quatro ruas verticais ligam as fileiras pelos vãos entre os terrenos, então
 * nenhuma rua passa por dentro de um prédio.
 *
 * ```
 * row 1-3   [lot] [lot] [lot] [lot] [lot]
 * row 4     calçada (portas)
 * row 5     ═══════╦═════╦═════╦═════╦═══   rua
 * row 7-9   [lot]  ║[lot]║[lot]║[lot]║[lot]
 * row 10    calçada║     ║     ║     ║
 * row 11    ═══════╬═════╬═════╬═════╬═══
 * row 13-15 [lot]  ║[lot]║[lot]║[lot]║[lot]
 * row 16    calçada║     ║     ║     ║
 * row 17    ═══════╩═════╩═════╩═════╩═══
 * row 18-19 parque / lago
 * ```
 */
object DiaryMapTiles {
    const val SIZE = 8
    const val COLS = 30
    const val ROWS = 20
    const val WIDTH = COLS * SIZE
    const val HEIGHT = ROWS * SIZE

    val LOT_COLS = intArrayOf(2, 8, 14, 20, 26)
    val LOT_ROWS = intArrayOf(1, 7, 13)
    val STREET_ROWS = LOT_ROWS.map { it + 4 }.toIntArray()
    val SIDEWALK_ROWS = LOT_ROWS.map { it + 3 }.toIntArray()
    val STREET_COLS = intArrayOf(6, 12, 18, 24)
    const val STREET_FIRST_COL = 1
    const val STREET_LAST_COL = 28

    /** Terrenos (prédio 3×3), em ordem de leitura. */
    val LOTS: List<TileRect> = LOT_ROWS.flatMap { r -> LOT_COLS.map { c -> TileRect(c, r, 3, 3) } }

    fun door(lot: TileRect) = TilePos(lot.col + 1, lot.lastRow + 1)

    fun isStreetRow(row: Int) = row in STREET_ROWS
    fun isStreetCol(col: Int) = col in STREET_COLS

    /** Tile fixo da grade (sem decoração). */
    fun baseTile(col: Int, row: Int): Tile {
        val hStreet = isStreetRow(row) && col in STREET_FIRST_COL..STREET_LAST_COL
        val vStreet = isStreetCol(col) && row in STREET_ROWS.first()..STREET_ROWS.last()
        return when {
            hStreet && vStreet -> when (row) {
                STREET_ROWS.first(), STREET_ROWS.last() -> Tile.ROAD_T
                else -> Tile.ROAD_CROSS
            }
            hStreet && (col == STREET_FIRST_COL || col == STREET_LAST_COL) -> Tile.ROAD_CORNER
            hStreet -> Tile.ROAD_H
            vStreet -> Tile.ROAD_V
            row in SIDEWALK_ROWS && col in STREET_FIRST_COL..STREET_LAST_COL -> Tile.SIDEWALK
            else -> Tile.GRASS
        }
    }

    fun isRoad(t: Tile) = t == Tile.ROAD_H || t == Tile.ROAD_V || t == Tile.ROAD_T || t == Tile.ROAD_CROSS || t == Tile.ROAD_CORNER

    /** Pode andar por aqui (rua ou calçada). */
    fun walkable(col: Int, row: Int) = baseTile(col, row).let { isRoad(it) || it == Tile.SIDEWALK }

    // ───────────── Pintura ─────────────

    fun paint(b: PixelBuffer, tile: Tile, col: Int, row: Int, lightsOn: Boolean, timeMs: Long) {
        val x = col * SIZE; val y = row * SIZE
        when (tile) {
            Tile.GRASS -> grass(b, x, y, col, row)
            Tile.ROAD_H -> { road(b, x, y); for (i in 1..6 step 3) b.hline(x + i, x + i + 1, y + 4, P.ROAD_LINE); b.hline(x, x + 7, y, P.ROAD_EDGE); b.hline(x, x + 7, y + 7, P.ROAD_EDGE) }
            Tile.ROAD_V -> { road(b, x, y); for (i in 1..6 step 3) b.vline(x + 4, y + i, y + i + 1, P.ROAD_LINE); b.vline(x, y, y + 7, P.ROAD_EDGE); b.vline(x + 7, y, y + 7, P.ROAD_EDGE) }
            Tile.ROAD_T, Tile.ROAD_CROSS -> { road(b, x, y); b.set(x, y, P.ROAD_EDGE); b.set(x + 7, y, P.ROAD_EDGE); b.set(x, y + 7, P.ROAD_EDGE); b.set(x + 7, y + 7, P.ROAD_EDGE) }
            Tile.ROAD_CORNER -> {
                // Fim de rua arredondado (esquerda ou direita).
                grass(b, x, y, col, row)
                val (x0, x1, cap) = if (col == STREET_FIRST_COL) Triple(x + 1, x + 7, x + 1) else Triple(x, x + 6, x + 6)
                b.box(x0, y, x1, y + 7, P.ROAD); b.vline(cap, y + 1, y + 6, P.ROAD_EDGE)
                b.hline(x0, x1, y, P.ROAD_EDGE); b.hline(x0, x1, y + 7, P.ROAD_EDGE)
            }
            Tile.SIDEWALK -> { b.box(x, y, x + 7, y + 7, P.SIDEWALK); b.vline(x + 7, y, y + 7, P.SIDEWALK_DARK); b.hline(x, x + 7, y + 7, P.SIDEWALK_DARK) }
            Tile.TREE -> { grass(b, x, y, col, row); b.box(x + 3, y + 5, x + 4, y + 7, P.TRUNK); b.disc(x + 4, y + 3, 3, P.OUTLINE); b.disc(x + 4, y + 3, 2, P.TREE); b.set(x + 3, y + 2, P.TREE_LIGHT); b.set(x + 4, y + 1, P.TREE_LIGHT) }
            Tile.BUSH -> {
                grass(b, x, y, col, row)
                b.disc(x + 2, y + 5, 2, P.OUTLINE); b.disc(x + 5, y + 5, 2, P.OUTLINE)
                b.disc(x + 2, y + 5, 1, P.TREE_LIGHT); b.disc(x + 5, y + 5, 1, P.TREE_LIGHT); b.set(x + 2, y + 4, P.GRASS_LIGHT)
            }
            Tile.FLOWER -> { grass(b, x, y, col, row); b.set(x + 2, y + 2, P.FLOWER_A); b.set(x + 5, y + 4, P.FLOWER_B); b.set(x + 1, y + 6, P.FLOWER_B); b.set(x + 6, y + 1, P.FLOWER_A) }
            Tile.BENCH -> { grass(b, x, y, col, row); b.hline(x + 1, x + 6, y + 3, P.BENCH); b.hline(x + 1, x + 6, y + 5, P.BENCH); b.set(x + 1, y + 6, P.OUTLINE); b.set(x + 6, y + 6, P.OUTLINE) }
            Tile.LAMP -> {
                paint(b, Tile.SIDEWALK, col, row, lightsOn, timeMs)
                b.vline(x + 4, y + 1, y + 7, P.LAMP); b.hline(x + 3, x + 5, y, P.LAMP)
                if (lightsOn) { b.set(x + 4, y + 1, P.LAMP_LIGHT); b.set(x + 3, y + 1, P.LAMP_LIGHT and 0x80FFFFFF.toInt()); b.set(x + 5, y + 1, P.LAMP_LIGHT and 0x80FFFFFF.toInt()) }
            }
            Tile.WATER -> {
                b.box(x, y, x + 7, y + 7, P.WATER)
                // Reflexo andando devagar.
                val phase = ((timeMs / 600) % 8).toInt()
                b.hline(x + (phase + col) % 6, x + (phase + col) % 6 + 1, y + 2 + row % 3, P.WATER_LIGHT)
            }
        }
    }

    private fun road(b: PixelBuffer, x: Int, y: Int) = b.box(x, y, x + 7, y + 7, P.ROAD)

    private fun grass(b: PixelBuffer, x: Int, y: Int, col: Int, row: Int) {
        b.box(x, y, x + 7, y + 7, P.GRASS)
        // Tufos determinísticos (sem aleatoriedade: o mesmo dia desenha igual).
        val h = (col * 73 + row * 151) and 0xFF
        b.set(x + h % 7, y + (h / 7) % 7, P.GRASS_DARK)
        b.set(x + (h / 3) % 7, y + (h / 11) % 7, P.GRASS_LIGHT)
    }
}
