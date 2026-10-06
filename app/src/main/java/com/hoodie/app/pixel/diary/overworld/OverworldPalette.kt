package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.DiaryMapPalette
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Estado visual de uma construção no overworld. */
enum class BiomeState { FUTURE, VISITED, CURRENT, GHOST }

/**
 * Paleta limitada do overworld, da mesma família do Hoodie e do mapa clássico.
 * Os estados são transformações de paleta (sem alfa contínuo): futura = dessaturada,
 * fantasma = meio caminho até a grama.
 */
object OverworldPalette {
    const val OUTLINE = DiaryMapPalette.OUTLINE
    const val GRASS = 0xFF6FB073.toInt()
    const val GRASS_DARK = 0xFF5A9862.toInt()
    const val GRASS_LIGHT = 0xFF8CC583.toInt()
    const val GRASS_TUFT = 0xFF4E8757.toInt()
    const val DIRT = 0xFFC9A46E.toInt()
    const val DIRT_DARK = 0xFFA7834F.toInt()
    const val STONE = 0xFF9A9CA8.toInt()
    const val STONE_DARK = 0xFF6F7282.toInt()
    const val STONE_LIGHT = 0xFFC3C5CE.toInt()
    const val WATER = 0xFF5B95D3.toInt()
    const val WATER_LIGHT = 0xFF9CC6EF.toInt()
    const val TREE = 0xFF2F6E46.toInt()
    const val TREE_LIGHT = 0xFF4E9663.toInt()
    const val TRUNK = 0xFF7A4E36.toInt()
    const val WOOD = 0xFFB07848.toInt()
    const val WOOD_DARK = 0xFF7F5233.toInt()
    const val WOOD_LIGHT = 0xFFD39B66.toInt()
    const val WALL = 0xFFEDE4D3.toInt()
    const val WALL_SHADE = 0xFFCFC3AE.toInt()
    const val ROOF_RED = 0xFFC9544F.toInt()
    const val ROOF_RED_DARK = 0xFF95393A.toInt()
    const val GLASS = 0xFF8FC2EC.toInt()
    const val GLASS_DARK = 0xFF5B86B8.toInt()
    const val WINDOW_LIT = DiaryMapPalette.WINDOW_LIT
    const val DOOR = DiaryMapPalette.DOOR
    const val FLAG = 0xFFE58AAE.toInt()
    const val GOLD = DiaryMapPalette.GOLD
    const val GOLD_DARK = 0xFFC9A03A.toInt()
    const val FIRE = 0xFFF09A3E.toInt()
    const val FIRE_CORE = 0xFFFFE07A.toInt()
    const val SMOKE = DiaryMapPalette.SMOKE
    const val PURPLE = 0xFF8E6BC4.toInt()
    const val PURPLE_DARK = 0xFF5F4592.toInt()
    const val CANVAS = 0xFFEFE2C0.toInt()
    const val AWNING_A = 0xFFE05D5D.toInt()
    const val AWNING_B = 0xFFF6F1E4.toInt()
    const val AWNING_C = 0xFF5BA4D9.toInt()
    const val PAPER = 0xFFFFFDF2.toInt()
    const val FLOWER_A = DiaryMapPalette.FLOWER_A
    const val FLOWER_B = DiaryMapPalette.FLOWER_B
    const val BIRD = 0xFF2B2E4A.toInt()
    const val SIGN = 0xFFC48A55.toInt()
    const val SIGN_DARK = 0xFF8C5A34.toInt()
    const val SIGN_LIGHT = 0xFFE2B07C.toInt()
    const val ARROW = 0xFFFFF4B8.toInt()
    const val FIREFLY = 0xFFF8FF9A.toInt()

    /** Aplica o estado a uma cor de construção. */
    fun state(color: Int, s: BiomeState): Int = when (s) {
        BiomeState.VISITED, BiomeState.CURRENT -> color
        BiomeState.FUTURE -> desaturate(color)
        BiomeState.GHOST -> PixelBuffer.mix(desaturate(color), GRASS, 0.5f)
    }

    fun desaturate(c: Int): Int {
        val r = (c ushr 16) and 0xFF; val g = (c ushr 8) and 0xFF; val b = c and 0xFF
        val l = (r * 30 + g * 59 + b * 11) / 100
        fun mixc(v: Int) = (v + l * 2) / 3
        return (c and 0xFF000000.toInt()) or (mixc(r) shl 16) or (mixc(g) shl 8) or mixc(b)
    }

    /**
     * Trilha por meio: cor de base, cor do padrão e o padrão (acessível sem depender
     * só da cor: pontilhado, tracejado, faixa dupla, marcos, dormentes).
     */
    enum class Pattern { DOTTED, DASHED, DOUBLE, MARKERS, SLEEPERS }

    data class TrailStyle(val bed: Int, val bedEdge: Int, val mark: Int, val pattern: Pattern, val width: Int)

    fun trail(mode: MovementMode?): TrailStyle = when (mode) {
        MovementMode.WALKING, MovementMode.RUNNING -> TrailStyle(DIRT, DIRT_DARK, 0xFF4FB676.toInt(), Pattern.DOTTED, 5)
        MovementMode.BICYCLE -> TrailStyle(0xFFD8B47E.toInt(), DIRT_DARK, 0xFFF2CF5B.toInt(), Pattern.DASHED, 5)
        MovementMode.CAR, MovementMode.VEHICLE_UNKNOWN -> TrailStyle(STONE, STONE_DARK, 0xFF5C5F70.toInt(), Pattern.DOUBLE, 7)
        MovementMode.BUS, MovementMode.PUBLIC_TRANSPORT -> TrailStyle(0xFF8EA0C0.toInt(), 0xFF5F6F91.toInt(), 0xFF3F72C4.toInt(), Pattern.MARKERS, 7)
        MovementMode.TRAIN -> TrailStyle(0xFFA89A8A.toInt(), 0xFF7A6C5E.toInt(), 0xFF9A6FE0.toInt(), Pattern.SLEEPERS, 7)
        MovementMode.METRO -> TrailStyle(0xFFA89A8A.toInt(), 0xFF7A6C5E.toInt(), 0xFF3FC9D6.toInt(), Pattern.SLEEPERS, 7)
        else -> TrailStyle(0xFFE0CFA6.toInt(), 0xFFB9A57D.toInt(), 0xFFF3E6C4.toInt(), Pattern.DOTTED, 5)
    }
}

/**
 * Pincel de uma construção 32×32: coordenadas locais e TODA cor passa pelo estado.
 * Assim cada bioma é pintado uma vez e ganha futura/visitada/atual/fantasma de graça.
 */
class BiomeCanvas(val b: PixelBuffer, val ox: Int, val oy: Int, val state: BiomeState) {
    private fun c(color: Int) = OverworldPalette.state(color, state)
    fun set(x: Int, y: Int, color: Int) = b.set(ox + x, oy + y, c(color))
    fun box(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) = b.box(ox + x0, oy + y0, ox + x1, oy + y1, c(color))
    fun hline(x0: Int, x1: Int, y: Int, color: Int) = b.hline(ox + x0, ox + x1, oy + y, c(color))
    fun vline(x: Int, y0: Int, y1: Int, color: Int) = b.vline(ox + x, oy + y0, oy + y1, c(color))
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) = b.line(ox + x0, oy + y0, ox + x1, oy + y1, c(color))
    fun disc(cx: Int, cy: Int, r: Int, color: Int) = b.disc(ox + cx, oy + cy, r, c(color))
    fun outlined(x0: Int, y0: Int, x1: Int, y1: Int, fill: Int) = b.outlined(ox + x0, oy + y0, ox + x1, oy + y1, c(fill), c(OverworldPalette.OUTLINE))

    /** Triângulo (telhado/frontão) de base y1 entre x0..x1 e ápice em (ax, ay), com contorno. */
    fun roof(ax: Int, ay: Int, x0: Int, x1: Int, y1: Int, fill: Int, shade: Int) {
        val h = (y1 - ay).coerceAtLeast(1)
        for (y in ay..y1) {
            val t = (y - ay).toFloat() / h
            val l = Math.round(ax + (x0 - ax) * t); val r = Math.round(ax + (x1 - ax) * t)
            hline(l, r, y, fill)
            set(l, y, OverworldPalette.OUTLINE); set(r, y, OverworldPalette.OUTLINE)
            if (r - l > 4) set(r - 1, y, shade)
        }
        hline(x0, x1, y1, OverworldPalette.OUTLINE)
    }

    /** Sombra de contato no chão (escurece a grama; não muda a construção). */
    fun shadow(x0: Int, x1: Int, y: Int) {
        for (x in x0..x1) for (dy in 0..1) {
            val px = ox + x; val py = oy + y + dy
            if (px in 0 until b.width && py in 0 until b.height) b.set(px, py, PixelBuffer.mix(b[px, py], OverworldPalette.OUTLINE, 0.25f))
        }
    }
}
