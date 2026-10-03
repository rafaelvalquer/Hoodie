package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.DiaryMapPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Estado visual do prédio: visitado (contorno claro), atual no replay (dourado), ainda não visitado (escuro). */
enum class BuildingState { VISITED, ACTIVE, UPCOMING }

/**
 * Prédios 24×24 em vista 3/4 (telhado em cima, fachada embaixo, porta voltada
 * para a calçada). Cada tipo tem silhueta e detalhe próprios além da cor do
 * telhado e da placa com ícone.
 */
object DiaryMapBuildings {
    const val SIZE = 24

    fun paint(b: PixelBuffer, node: DiaryMapPlaceNode, state: BuildingState, lightsOn: Boolean, timeMs: Long) {
        val (x, y) = node.footprint.pixels.let { it[0] to it[1] }
        val tmp = PixelBuffer(SIZE, SIZE)
        draw(tmp, node.type, lightsOn, timeMs)
        if (state == BuildingState.UPCOMING) for (i in tmp.pixels.indices) if (tmp.pixels[i] ushr 24 != 0) tmp.pixels[i] = P.dim(tmp.pixels[i])
        // Sombra no chão (luz de cima/esquerda).
        b.box(x + 3, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, P.GRASS_DARK)
        b.blit(tmp, x, y)
        when (state) {
            BuildingState.ACTIVE -> {
                // Pulso discreto: contorno 1 → 2 → 1 px (sem brilho "moderno").
                frame(b, x - 1, y - 1, x + SIZE, y + SIZE, P.GOLD)
                if (pulseWidth(timeMs) == 2) frame(b, x - 2, y - 2, x + SIZE + 1, y + SIZE + 1, P.GOLD)
            }
            BuildingState.VISITED -> frame(b, x - 1, y - 1, x + SIZE, y + SIZE, P.VISITED and 0x99FFFFFF.toInt())
            BuildingState.UPCOMING -> Unit
        }
    }

    /** Espessura do contorno do prédio ativo: ciclo de 3 tempos (1, 2, 1). */
    fun pulseWidth(timeMs: Long): Int = if ((timeMs / 300) % 3 == 1L) 2 else 1

    private fun frame(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        b.hline(x0 + 1, x1 - 1, y0, c); b.hline(x0 + 1, x1 - 1, y1, c); b.vline(x0, y0 + 1, y1 - 1, c); b.vline(x1, y0 + 1, y1 - 1, c)
    }

    private fun draw(b: PixelBuffer, type: PlaceType, lightsOn: Boolean, timeMs: Long) {
        val (roof, roofShade) = P.roof(type)
        val win = if (lightsOn) P.WINDOW_LIT else P.WINDOW
        when (type) {
            PlaceType.WORK -> {
                // Prédio alto de escritório: telhado reto e grade de janelas.
                b.outlined(2, 0, 21, 23, P.WALL, P.OUTLINE)
                b.box(3, 1, 20, 4, roof); b.hline(3, 20, 4, roofShade)
                for (r in 0 until 4) for (c in 0 until 4) {
                    val wx = 4 + c * 4; val wy = 7 + r * 3
                    b.box(wx, wy, wx + 1, wy + 1, if (lightsOn && (r + c) % 3 != 0) P.WINDOW_LIT else P.WINDOW)
                }
                b.vline(18, 5, 22, P.WALL_SHADE); b.vline(19, 5, 22, P.WALL_SHADE); b.vline(20, 5, 22, P.WALL_SHADE)
                door(b, 10, 19)
                sign(b, type, 8, 0, roof)
            }
            PlaceType.SCHOOL -> {
                house(b, roof, roofShade, win, wide = true)
                // Mastro com bandeira.
                b.vline(21, 0, 8, P.OUTLINE); b.box(17, 0, 20, 2, if ((timeMs / 500) % 2 == 0L) P.GOLD else roof)
                sign(b, type, 8, 4, roof)
            }
            else -> {
                house(b, roof, roofShade, win, wide = type == PlaceType.MARKET || type == PlaceType.STORE || type == PlaceType.GYM)
                when (type) {
                    PlaceType.HOME -> { b.outlined(16, 0, 19, 5, 0xFFA05540.toInt(), P.OUTLINE) } // chaminé
                    PlaceType.RESTAURANT, PlaceType.MARKET, PlaceType.STORE -> {
                        // Toldo listrado.
                        for (x in 2..21) b.vline(x, 12, 13, if ((x / 2) % 2 == 0) roof else P.WALL)
                        b.hline(2, 21, 14, P.OUTLINE)
                    }
                    PlaceType.GYM -> { b.hline(4, 19, 12, P.OUTLINE); b.box(5, 11, 6, 13, P.OUTLINE); b.box(17, 11, 18, 13, P.OUTLINE) }
                    PlaceType.LEISURE -> { b.set(4, 2, P.FLOWER_A); b.set(19, 3, P.FLOWER_B) }
                    PlaceType.FAMILY -> { b.set(5, 20, P.FLOWER_A); b.set(18, 20, P.FLOWER_A) }
                    else -> Unit
                }
                sign(b, type, 8, 3, roof)
            }
        }
    }

    /** Casa padrão: telhado de duas águas e fachada com janelas e porta. */
    private fun house(b: PixelBuffer, roof: Int, roofShade: Int, win: Int, wide: Boolean) {
        val x0 = if (wide) 1 else 3; val x1 = if (wide) 22 else 20
        b.outlined(x0, 9, x1, 23, P.WALL, P.OUTLINE)
        b.box(x1 - 3, 10, x1 - 1, 22, P.WALL_SHADE)
        // Telhado.
        for (r in 0..9) {
            val inset = (9 - r) / 3
            b.hline(x0 - 1 + inset, x1 + 1 - inset, r + 2, if (r == 0 || r == 9) P.OUTLINE else if (r > 6) roofShade else roof)
            b.set(x0 - 1 + inset, r + 2, P.OUTLINE); b.set(x1 + 1 - inset, r + 2, P.OUTLINE)
        }
        b.outlined(x0 + 2, 15, x0 + 5, 18, win, P.OUTLINE)
        b.outlined(x1 - 5, 15, x1 - 2, 18, win, P.OUTLINE)
        door(b, 10, 18)
    }

    private fun door(b: PixelBuffer, x: Int, y: Int) {
        b.outlined(x, y, x + 3, 23, P.DOOR, P.OUTLINE)
        b.set(x + 2, y + 3, P.GOLD)
    }

    private fun sign(b: PixelBuffer, type: PlaceType, x: Int, y: Int, roof: Int) {
        b.outlined(x - 1, y - 1, x + DiaryMapIcons.SIZE, y + DiaryMapIcons.SIZE, P.WALL, P.OUTLINE)
        DiaryMapIcons.draw(b, type, x, y, P.OUTLINE, roof)
    }
}
