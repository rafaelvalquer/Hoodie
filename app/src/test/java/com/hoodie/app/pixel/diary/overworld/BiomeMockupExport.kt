package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.art.SceneBootstrapStudio.Canvas
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.diary.journey.JourneyHoodieMarker
import com.hoodie.app.pixel.diary.journey.JourneyMarkerState
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * MOCKUP (só exporta, não muda o app): como ficaria o mapa da Jornada com construções de 48 px em
 * 3/4, 3–4 tons por material, textura, terreninho e identidade por local, placas embaixo.
 * Saída em build/pixel-preview/journey-mockup/.
 */
class BiomeMockupExport {
    private val W = 240
    private val H = 300
    private val OUT = 0xFF2A2238.toInt()          // contorno escuro e quente (não preto puro)

    // Chão e trilha.
    private val GRASS = 0xFF7DB86A.toInt(); private val GRASS_D = 0xFF5E9A56.toInt(); private val GRASS_L = 0xFF9ACB78.toInt()
    private val DIRT = 0xFFD7B27A.toInt(); private val DIRT_D = 0xFFB08856.toInt(); private val DIRT_L = 0xFFE9CC98.toInt()
    private val STONE = 0xFFB7B0A6.toInt(); private val STONE_D = 0xFF8C857D.toInt(); private val STONE_L = 0xFFD9D3C8.toInt()
    private val WOOD = 0xFF9A6438.toInt(); private val WOOD_D = 0xFF6E4527.toInt(); private val WOOD_L = 0xFFC08550.toInt()
    private val GLASS = 0xFF8EC5D6.toInt(); private val GLASS_L = 0xFFD8F0F2.toInt(); private val LIT = 0xFFFFD877.toInt()
    private val WHITE = 0xFFF6F0E2.toInt()

    /** Material de 4 tons: luz, base, sombra, sombra profunda. */
    data class Mat(val l: Int, val m: Int, val d: Int, val dd: Int)
    private val PLASTER = Mat(0xFFFBF1DC.toInt(), 0xFFEFDFC0.toInt(), 0xFFD2BE98.toInt(), 0xFFAE9874.toInt())
    private val BRICK = Mat(0xFFE08A64.toInt(), 0xFFC4683F.toInt(), 0xFF9C4B2D.toInt(), 0xFF733421.toInt())
    private val TILE_RED = Mat(0xFFE7735E.toInt(), 0xFFC64B3E.toInt(), 0xFF97343A.toInt(), 0xFF6A2330.toInt())
    private val TILE_GREEN = Mat(0xFF7FB083.toInt(), 0xFF56895E.toInt(), 0xFF3C6448.toInt(), 0xFF284636.toInt())
    private val SLATE = Mat(0xFFB4ACC0.toInt(), 0xFF8C839C.toInt(), 0xFF655E78.toInt(), 0xFF463F57.toInt())
    private val PURPLE = Mat(0xFFB892E0.toInt(), 0xFF8E66C2.toInt(), 0xFF6A4596.toInt(), 0xFF4A2E6E.toInt())
    private val TIMBER = Mat(WOOD_L, WOOD, WOOD_D, 0xFF4E3020.toInt())
    private val GRAY = Mat(STONE_L, STONE, STONE_D, 0xFF615B55.toInt())
    private val TEAL = Mat(0xFF79C7B9.toInt(), 0xFF4FA394.toInt(), 0xFF367A70.toInt(), 0xFF24564F.toInt())

    private enum class Wall { PLASTER, BRICK, WOOD, STONE }

    private fun hash(a: Int, b: Int): Int { var x = a * -0x61c88647 + b * 0x45d9f3b; x = (x xor (x ushr 16)) * 0x45d9f3b; return x xor (x ushr 16) }

    // ───────────── Peças ─────────────

    private fun Canvas.wallTexture(x0: Int, y0: Int, x1: Int, y1: Int, mat: Mat, kind: Wall) {
        rect(x0, y0, x1, y1, mat.m)
        when (kind) {
            Wall.BRICK -> for (y in y0..y1) for (x in x0..x1) {
                val row = (y - y0) / 3
                if ((y - y0) % 3 == 2 || (x + row * 3) % 6 == 0) set(x, y, mat.d)
                else if ((y - y0) % 3 == 0 && (x + row * 3) % 6 == 1) set(x, y, mat.l)
            }
            Wall.WOOD -> for (y in y0..y1) for (x in x0..x1) {
                if ((x - x0) % 4 == 3) set(x, y, mat.d)
                else if ((x - x0) % 4 == 0 && hash(x, y / 5) and 3 == 0) set(x, y, mat.l)
            }
            Wall.STONE -> for (y in y0..y1) for (x in x0..x1) {
                val row = (y - y0) / 4
                if ((y - y0) % 4 == 3 || (x + row * 5) % 9 == 0) set(x, y, mat.d)
                else if ((y - y0) % 4 == 0 && hash(x, row) and 1 == 0) set(x, y, mat.l)
            }
            Wall.PLASTER -> for (y in y0..y1) for (x in x0..x1) if (hash(x, y) and 31 == 0) set(x, y, mat.d)
        }
        vline(x0, y0, y1, mat.l)                          // luz da esquerda
        hline(x0, x1, y1, mat.dd)                         // pé da parede
    }

    private fun Canvas.window(x: Int, y: Int, w: Int, h: Int, night: Boolean, trim: Int) {
        rect(x - 1, y - 1, x + w, y + h, trim)
        rect(x, y, x + w - 1, y + h - 1, if (night) LIT else GLASS)
        if (!night) { set(x, y, GLASS_L); set(x + 1, y, GLASS_L); set(x, y + 1, GLASS_L) }
        vline(x + w / 2, y, y + h - 1, trim); hline(x, x + w - 1, y + h / 2, trim)
        hline(x - 2, x + w + 1, y + h + 1, STONE_L)       // peitoril
    }

    private fun Canvas.door(x: Int, y: Int, w: Int, h: Int, night: Boolean) {
        rect(x - 1, y - 1, x + w, y + h, OUT)
        rect(x, y, x + w - 1, y + h - 1, WOOD_D)
        for (yy in y until y + h) set(x + w / 2, yy, OUT)
        set(x + w - 2, y + h / 2, if (night) LIT else STONE_L)
        rect(x - 2, y + h, x + w + 1, y + h + 1, STONE)   // degrau
    }

    /** Casa em 3/4: fachada, parede lateral recuando para a direita, telhado de duas águas com telhas. */
    private fun Canvas.house(ox: Int, oy: Int, wall: Mat, kind: Wall, roof: Mat, night: Boolean, frontW: Int = 28, wallTop: Int = 22, depth: Int = 10) {
        val x0 = ox + 6; val x1 = x0 + frontW; val yb = oy + 44; val yt = oy + wallTop
        val ridgeX = (x0 + x1) / 2; val ridgeY = yt - frontW / 2 + 1
        // Sombra de contato (à direita, luz da esquerda).
        for (x in x0..x1 + depth + 3) for (y in yb - 1..yb + 2) if (abs(y - yb) <= 1 || (x + y) % 2 == 0) set(x, y, GRASS_D)
        // Parede lateral (recua para cima e à direita).
        poly(wall.d, x1 to yt, x1 + depth to yt - depth / 2, x1 + depth to yb - depth / 2, x1 to yb)
        for (y in yt..yb) if (y % 3 == 0) line(x1, y, x1 + depth, y - depth / 2, wall.dd)
        // Fachada com textura + empena triangular.
        wallTexture(x0, yt, x1, yb, wall, kind)
        poly(wall.m, x0 to yt, ridgeX to ridgeY, x1 to yt)
        for (k in 0..2) set(ridgeX - 1 + k, ridgeY + 6, if (night) LIT else GLASS)  // óculo na empena
        // Água direita do telhado (vista de cima) com fileiras de telhas.
        poly(roof.m, ridgeX to ridgeY, ridgeX + depth to ridgeY - depth / 2, x1 + depth + 1 to yt - depth / 2, x1 + 1 to yt + 1)
        for (t in 0..frontW / 2 step 3) {
            val ax = ridgeX + t; val ay = ridgeY + t
            line(ax, ay, ax + depth, ay - depth / 2, roof.d)
        }
        line(ridgeX, ridgeY, ridgeX + depth, ridgeY - depth / 2, roof.l)                    // cumeeira iluminada
        line(x1 + 1, yt + 1, x1 + depth + 1, yt - depth / 2 + 1, roof.dd)                    // beiral
        // Beiral esquerdo (verga) com espessura.
        line(x0 - 2, yt + 1, ridgeX, ridgeY - 1, roof.dd); line(x0 - 1, yt + 1, ridgeX, ridgeY, roof.l)
        // Contornos coloridos.
        outlinePoly(wall.dd, x0 to yt, ridgeX to ridgeY, x1 to yt, x1 to yb, x0 to yb)
    }

    private fun Canvas.plot(ox: Int, oy: Int) {
        // Terreninho: grama mais clara, caminho de terra da porta até a placa.
        for (y in oy + 40..oy + 50) for (x in ox + 2..ox + 46) {
            val d = ((x - ox - 24) / 23.0).let { it * it } + ((y - oy - 45) / 6.0).let { it * it }
            if (d <= 1.0) set(x, y, if ((x + y) % 7 == 0) GRASS else GRASS_L)
        }
        for (y in oy + 45..oy + 52) { set(ox + 19, y, DIRT_D); for (x in ox + 20..ox + 25) set(x, y, DIRT); set(ox + 26, y, DIRT_D) }
    }

    private fun Canvas.tree(x: Int, y: Int, s: Int = 7) {
        rect(x - 1, y, x + 1, y + s, WOOD_D)
        disc(x, y - s / 2, s, GRASS_D); disc(x - 2, y - s / 2 - 2, s - 2, 0xFF6FAE62.toInt()); disc(x - 3, y - s / 2 - 3, s - 5, GRASS_L)
    }

    private fun Canvas.smoke(x: Int, y: Int) { listOf(0 to 0, 2 to -3, 1 to -6, 3 to -9).forEach { (dx, dy) -> disc(x + dx, y + dy, 1 + (-dy / 5), 0xFFE8E4DC.toInt()) } }

    // ───────────── Os 9 locais ─────────────

    private fun Canvas.home(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy); tree(ox + 44, oy + 38, 6)
        rect(ox + 28, oy + 4, ox + 32, oy + 14, BRICK.m); vline(ox + 28, oy + 4, oy + 14, BRICK.l); hline(ox + 27, ox + 33, oy + 3, BRICK.dd)
        smoke(ox + 30, oy + 1)
        house(ox, oy, PLASTER, Wall.PLASTER, TILE_RED, n)
        window(ox + 9, oy + 28, 5, 5, n, WOOD); window(ox + 26, oy + 28, 5, 5, n, WOOD); door(ox + 18, oy + 34, 5, 10, n)
        // Caixa de correio e flores.
        rect(ox + 2, oy + 36, ox + 4, oy + 44, WOOD_D); rect(ox + 1, oy + 33, ox + 5, oy + 36, TILE_RED.m)
        listOf(8, 11, 14).forEach { x -> set(ox + x, oy + 44, 0xFFF07FA8.toInt()); set(ox + x + 1, oy + 43, 0xFFFFD45E.toInt()) }
    }

    private fun Canvas.cafe(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        house(ox + 4, oy + 6, BRICK, Wall.BRICK, SLATE, n, frontW = 24, wallTop = 20)
        // Toldo listrado sobre a vitrine.
        for (x in ox + 9..ox + 35) for (y in oy + 26..oy + 30) set(x, y, if ((x / 3) % 2 == 0) TEAL.m else WHITE)
        for (x in ox + 9..ox + 35 step 3) set(x, oy + 31, TEAL.d)
        window(ox + 12, oy + 34, 9, 5, n, WOOD_D); door(ox + 26, oy + 36, 5, 8, n)
        // Mesinha com guarda-sol e xícara fumegante na placa.
        rect(ox + 1, oy + 26, ox + 11, oy + 27, TILE_RED.m); vline(ox + 6, oy + 28, oy + 42, WOOD_D); hline(ox + 3, ox + 9, oy + 38, WOOD)
        rect(ox + 38, oy + 30, ox + 46, oy + 36, WOOD); rect(ox + 40, oy + 32, ox + 44, oy + 35, WHITE); smoke(ox + 42, oy + 29)
    }

    private fun Canvas.office(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        val x0 = ox + 8; val x1 = ox + 36; val yt = oy + 6; val yb = oy + 44
        for (x in x0..x1 + 12) for (y in yb - 1..yb + 2) if ((x + y) % 2 == 0 || y == yb) set(x, y, GRASS_D)
        poly(GRAY.d, x1 to yt, x1 + 10 to yt - 5, x1 + 10 to yb - 5, x1 to yb)
        wallTexture(x0, yt, x1, yb, GRAY, Wall.STONE)
        // Ameias (castelo) no topo e na lateral.
        for (x in x0 until x1 step 6) rect(x, yt - 4, x + 3, yt - 1, GRAY.m)
        hline(x0, x1, yt, GRAY.dd)
        // Janelas por andar (escritório).
        for (row in 0..3) for (col in 0..2) {
            val lit = n && hash(row, col) and 1 == 0
            rect(x0 + 4 + col * 9, yt + 5 + row * 8, x0 + 8 + col * 9, yt + 9 + row * 8, if (lit) LIT else GLASS)
            hline(x0 + 4 + col * 9, x0 + 8 + col * 9, yt + 10 + row * 8, GRAY.l)
        }
        door(ox + 19, oy + 36, 7, 8, n)
        // Bandeira e estandarte.
        vline(ox + 22, oy - 6, yt - 4, WOOD_D); rect(ox + 23, oy - 6, ox + 30, oy - 2, TILE_RED.m); rect(ox + 23, oy - 2, ox + 27, oy - 1, TILE_RED.d)
        rect(x0 - 3, yt + 6, x0 - 1, yt + 16, PURPLE.m)
    }

    private fun Canvas.tavern(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        house(ox, oy, TIMBER, Wall.WOOD, TILE_GREEN, n)
        window(ox + 9, oy + 28, 6, 5, n, WOOD_D); window(ox + 25, oy + 28, 6, 5, n, WOOD_D); door(ox + 17, oy + 33, 6, 11, n)
        // Placa pendurada com caneca e barris.
        hline(ox + 34, ox + 42, oy + 24, WOOD_D); vline(ox + 41, oy + 24, oy + 27, WOOD_D)
        rect(ox + 37, oy + 27, ox + 45, oy + 34, WOOD_L); rect(ox + 39, oy + 29, ox + 42, oy + 33, 0xFFF2C95C.toInt()); vline(ox + 43, oy + 30, oy + 32, 0xFFF2C95C.toInt())
        listOf(ox + 36, ox + 42).forEach { x -> rect(x, oy + 38, x + 5, oy + 45, WOOD); hline(x, x + 5, oy + 40, OUT); hline(x, x + 5, oy + 43, OUT) }
    }

    private fun Canvas.gym(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        val x0 = ox + 4; val x1 = ox + 40
        rect(x0 - 2, oy + 40, x1 + 2, oy + 44, STONE); hline(x0 - 2, x1 + 2, oy + 40, STONE_L); hline(x0 - 1, x1 + 1, oy + 42, STONE_D)
        poly(GRAY.m, x0 - 2 to oy + 18, (x0 + x1) / 2 to oy + 6, x1 + 2 to oy + 18)
        outlinePoly(GRAY.dd, x0 - 2 to oy + 18, (x0 + x1) / 2 to oy + 6, x1 + 2 to oy + 18)
        rect(x0 - 2, oy + 18, x1 + 2, oy + 21, GRAY.l); hline(x0 - 2, x1 + 2, oy + 21, GRAY.dd)
        rect(x0 + 2, oy + 22, x1 - 2, oy + 39, 0xFF4A3D52.toInt())                     // interior escuro
        for (c in 0..4) { val x = x0 + 2 + c * 8; rect(x, oy + 22, x + 3, oy + 39, WHITE); vline(x, oy + 22, oy + 39, STONE_L); vline(x + 3, oy + 22, oy + 39, STONE_D) }
        // Halter dourado no frontão.
        hline(ox + 18, ox + 26, oy + 13, 0xFFE2B24A.toInt()); rect(ox + 16, oy + 11, ox + 18, oy + 15, OUT); rect(ox + 26, oy + 11, ox + 28, oy + 15, OUT)
        if (n) for (c in 0..3) rect(x0 + 6 + c * 8, oy + 26, x0 + 8 + c * 8, oy + 34, LIT)
    }

    private fun Canvas.market(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        listOf(Triple(ox + 1, oy + 14, TILE_RED), Triple(ox + 24, oy + 18, TEAL)).forEach { (x, y, m) ->
            vline(x + 1, y + 4, y + 26, WOOD_D); vline(x + 20, y + 4, y + 26, WOOD_D)
            for (xx in x..x + 21) for (yy in y..y + 5) set(xx, yy, if ((xx / 3) % 2 == 0) m.m else WHITE)
            for (xx in x..x + 21 step 3) set(xx, y + 6, m.d)
            rect(x + 2, y + 18, x + 19, y + 26, WOOD); hline(x + 2, x + 19, y + 18, WOOD_L)
            // Caixas de frutas.
            listOf(0xFFE65A4A.toInt(), 0xFFF2C14E.toInt(), 0xFF8BC34A.toInt()).forEachIndexed { i, f ->
                rect(x + 3 + i * 6, y + 14, x + 7 + i * 6, y + 17, WOOD_L)
                for (k in 0..2) set(x + 4 + i * 6 + k, y + 13, f)
            }
            if (n) set(x + 10, y + 8, LIT)
        }
    }

    private fun Canvas.school(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        val cx = ox + 22
        // Torre redonda: parede de pedra com sombreado cilíndrico.
        for (x in cx - 10..cx + 10) {
            val t = (x - (cx - 10)) / 20.0
            val c = when { t < .2 -> GRAY.l; t < .6 -> GRAY.m; t < .85 -> GRAY.d; else -> GRAY.dd }
            for (y in oy + 14..oy + 44) set(x, y, c)
        }
        for (y in oy + 17..oy + 44 step 4) for (x in cx - 10..cx + 10) if ((x + y / 4 * 3) % 7 == 0) set(x, y, GRAY.dd)
        // Telhado cônico roxo com faixas.
        for (y in oy - 2..oy + 14) {
            val half = (y - (oy - 2)) * 12 / 16
            for (x in cx - half..cx + half) set(x, y, if (x < cx - half / 3) PURPLE.l else if (x < cx + half / 3) PURPLE.m else PURPLE.d)
        }
        hline(cx - 13, cx + 13, oy + 14, PURPLE.dd)
        set(cx, oy - 4, 0xFFFFE27A.toInt()); set(cx - 1, oy - 3, 0xFFFFE27A.toInt()); set(cx + 1, oy - 3, 0xFFFFE27A.toInt()) // estrela
        window(cx - 2, oy + 20, 4, 6, n, WOOD_D); door(cx - 3, oy + 34, 6, 10, n)
        // Pilha de livros e coruja.
        listOf(TILE_RED.m, TEAL.m, 0xFFF2C14E.toInt()).forEachIndexed { i, c -> rect(ox + 36, oy + 41 - i * 3, ox + 44 - i, oy + 43 - i * 3, c) }
        rect(cx + 6, oy + 9, cx + 9, oy + 13, WOOD); set(cx + 6, oy + 10, LIT); set(cx + 8, oy + 10, LIT)
    }

    private fun Canvas.park(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        tree(ox + 6, oy + 26, 8); tree(ox + 43, oy + 30, 6)
        // Coreto: telhado octogonal verde-água, colunas brancas.
        val cx = ox + 24
        poly(TEAL.m, cx - 15 to oy + 18, cx - 8 to oy + 8, cx + 8 to oy + 8, cx + 15 to oy + 18)
        poly(TEAL.l, cx - 15 to oy + 18, cx - 8 to oy + 8, cx - 2 to oy + 8, cx - 4 to oy + 18)
        hline(cx - 15, cx + 15, oy + 18, TEAL.dd); set(cx, oy + 6, WOOD_D); set(cx, oy + 5, WOOD_D)
        listOf(-12, -4, 4, 12).forEach { dx -> vline(cx + dx, oy + 19, oy + 32, WHITE); vline(cx + dx + 1, oy + 19, oy + 32, STONE_D) }
        rect(cx - 15, oy + 32, cx + 15, oy + 35, STONE); hline(cx - 15, cx + 15, oy + 32, STONE_L)
        // Chafariz e banco.
        disc(ox + 10, oy + 43, 4, STONE_D); disc(ox + 10, oy + 43, 3, GLASS); set(ox + 10, oy + 39, GLASS_L); set(ox + 10, oy + 38, GLASS_L)
        rect(ox + 34, oy + 41, ox + 44, oy + 42, WOOD); vline(ox + 35, oy + 43, oy + 45, WOOD_D); vline(ox + 43, oy + 43, oy + 45, WOOD_D)
        if (n) listOf(-12, 12).forEach { dx -> set(cx + dx, oy + 21, LIT) }
    }

    private fun Canvas.family(ox: Int, oy: Int, n: Boolean) {
        plot(ox, oy)
        house(ox, oy, TIMBER, Wall.WOOD, TILE_RED, n, frontW = 26)
        smoke(ox + 26, oy + 6)
        window(ox + 9, oy + 28, 5, 5, n, WOOD_D); door(ox + 22, oy + 33, 5, 11, n)
        // Horta e balanço na árvore.
        for (r in 0..1) for (x in ox + 2..ox + 14 step 3) { set(x, oy + 46 + r * 2, DIRT_D); set(x + 1, oy + 45 + r * 2, GRASS_D) }
        tree(ox + 43, oy + 28, 7)
        vline(ox + 40, oy + 26, oy + 38, OUT); vline(ox + 45, oy + 26, oy + 38, OUT); rect(ox + 39, oy + 38, ox + 46, oy + 39, WOOD)
    }

    // ───────────── Mapa ─────────────

    private fun Canvas.sign(cx: Int, y: Int, words: Int, current: Boolean) {
        val x0 = cx - 22; val x1 = cx + 22
        rect(x0, y, x1, y + 11, if (current) 0xFFF2CF5B.toInt() else WOOD_L)
        rect(x0 + 1, y + 1, x1 - 1, y + 10, if (current) 0xFFF8E08A.toInt() else 0xFFE2B07C.toInt())
        outlinePoly(OUT, x0 to y, x1 to y, x1 to y + 11, x0 to y + 11)
        // "Texto" da placa (nome + horário), como o app escreve por cima em Press Start 2P.
        for (k in 0 until words) hline(x0 + 4 + k * 12, x0 + 12 + k * 12, y + 4, OUT)
        hline(x0 + 4, x0 + 18, y + 7, WOOD_D)
    }

    private fun Canvas.ground() {
        rect(0, 0, W - 1, H - 1, GRASS)
        for (y in 0 until H) for (x in 0 until W) {
            val h = hash(x, y)
            when (h and 127) { 0 -> set(x, y, GRASS_D); 1 -> { set(x, y, GRASS_L); set(x, y - 1, GRASS_L) }; 2 -> if (y % 3 == 0) set(x, y, 0xFFF07FA8.toInt()) }
        }
    }

    private val COLS = listOf(44, 120, 196)
    private val ROW_TOP = listOf(14, 100, 186)

    private fun Canvas.trail() {
        fun seg(x0: Int, x1: Int, y: Int) {
            for (x in minOf(x0, x1)..maxOf(x0, x1)) {
                for (dy in -3..3) set(x, y + dy, if (abs(dy) == 3) DIRT_D else DIRT)
                if (hash(x, y) and 7 == 0) set(x, y - 1, DIRT_L)
                if (x % 9 == 0) set(x, y + 1, STONE)
            }
        }
        fun turn(x: Int, y0: Int, y1: Int, right: Boolean) {
            val r = (y1 - y0) / 2; val cy = (y0 + y1) / 2
            for (a in 0..180) {
                val t = Math.toRadians(a - 90.0)
                val px = x + ((if (right) 1 else -1) * Math.cos(t) * r).toInt(); val py = cy + (Math.sin(t) * r).toInt()
                for (dx in -3..3) for (dy in -3..3) if (dx * dx + dy * dy <= 9) set(px + dx, py + dy, if (dx * dx + dy * dy >= 8) DIRT_D else DIRT)
            }
        }
        val ys = ROW_TOP.map { it + 74 }
        seg(COLS[0], COLS[2], ys[0]); turn(COLS[2], ys[0], ys[1], true)
        seg(COLS[2], COLS[0], ys[1]); turn(COLS[0], ys[1], ys[2], false)
        seg(COLS[0], COLS[2], ys[2])
    }

    private fun mockup(night: Boolean): PixelBuffer {
        val c = Canvas(PixelBuffer(W, H))
        c.ground()
        listOf(4 to 60, 234 to 140, 6 to 230, 232 to 40, 120 to 290).forEach { (x, y) -> c.tree(x, y, 6) }
        c.trail()
        val stops: List<Canvas.(Int, Int, Boolean) -> Unit> = listOf(
            { x, y, n -> home(x, y, n) }, { x, y, n -> cafe(x, y, n) }, { x, y, n -> office(x, y, n) },
            { x, y, n -> tavern(x, y, n) }, { x, y, n -> gym(x, y, n) }, { x, y, n -> market(x, y, n) },
            { x, y, n -> school(x, y, n) }, { x, y, n -> park(x, y, n) }, { x, y, n -> family(x, y, n) },
        )
        val order = listOf(0, 1, 2, 2, 1, 0, 0, 1, 2)                     // serpentina: linha 2 volta
        stops.forEachIndexed { i, draw ->
            val cx = COLS[order[i]]; val top = ROW_TOP[i / 3]
            c.draw(cx - 24, top, night)
            c.sign(cx, top + 56, 2 + i % 2, current = i == 4)
        }
        // O Hoodie na parada atual (Academia), sobre a trilha.
        JourneyHoodieMarker.paint(c.b, JourneyMarkerState(MapPoint(COLS[1].toFloat(), (ROW_TOP[1] + 76).toFloat()), MarkerDirection.FRONT, false, JourneyVehicle.ON_FOOT, true), 0)
        if (night) {
            // Noite: escurece tudo, mas devolve janelas acesas e lampiões.
            val px = c.b.pixels
            for (i in px.indices) {
                val p = px[i]; if (p == LIT || p == 0xFFF2CF5B.toInt() || p == 0xFFF8E08A.toInt()) continue
                val r = (p shr 16 and 0xFF) * 120 / 256; val g = (p shr 8 and 0xFF) * 132 / 256; val b = (p and 0xFF) * 200 / 256
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return c.b
    }

    @Test fun exportMockup() {
        val dir = File(PreviewExport.dir, "journey-mockup")
        val day = mockup(false); val night = mockup(true)
        PreviewExport.write(File(dir, "mockup-day.png"), day, 3, null)
        PreviewExport.write(File(dir, "mockup-night.png"), night, 3, null)
        val pair = PixelBuffer(W * 2 + 8, H).apply { fill(0xFF2B2E4A.toInt()); blit(day, 0, 0); blit(night, W + 8, 0) }
        PreviewExport.write(File(dir, "mockup-day-night.png"), pair, 2, null)
    }
}
