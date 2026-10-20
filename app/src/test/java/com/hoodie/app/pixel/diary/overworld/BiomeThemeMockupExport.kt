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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MOCKUP (só exporta, não muda o app): temas Gatópolis e Cidade moderna no mesmo mapa de 9 paradas do
 * [BiomeMockupExport]. Saída em build/pixel-preview/journey-mockup/.
 */
class BiomeThemeMockupExport {
    private val W = 240
    private val H = 300

    data class Mat(val l: Int, val m: Int, val d: Int, val dd: Int)
    private fun mat(a: Long, b: Long, c: Long, d: Long) = Mat(a.toInt(), b.toInt(), c.toInt(), d.toInt())
    private fun k(v: Long) = v.toInt()

    private val OUT = k(0xFF2A2238)
    private val WHITE = k(0xFFF8F4EA)
    private val LIT = k(0xFFFFD877)
    private val SIGN_CUR = k(0xFFF2CF5B); private val SIGN_CUR_L = k(0xFFF8E08A)
    private val NEON_P = k(0xFFFF5FA2); private val NEON_O = k(0xFFFF9A3D)
    private val KEEP = setOf(LIT, SIGN_CUR, SIGN_CUR_L, NEON_P, NEON_O)

    // Gatópolis: pastel, tudo em escala de gato.
    private val C_GRASS = k(0xFFA9DCA4); private val C_GRASS_D = k(0xFF8CC58F); private val C_GRASS_L = k(0xFFC6EDBB)
    private val C_FLOWER = k(0xFFF7B6CC)
    private val C_PATH = k(0xFFF5E6C8); private val C_PATH_D = k(0xFFDCC49B); private val C_PAW = k(0xFFCFAF84)
    private val CREAM = mat(0xFFFFF8EA, 0xFFF3E3C3, 0xFFD9C29A, 0xFFB39C74)
    private val CARD = mat(0xFFE8BC82, 0xFFCF9A5E, 0xFFAB7844, 0xFF815A33)
    private val PINK = mat(0xFFFFD0DF, 0xFFF5A3C0, 0xFFD47A9F, 0xFFA6567C)
    private val MINT = mat(0xFFC8F0DA, 0xFF95D8B5, 0xFF6BB391, 0xFF4C8D71)
    private val LILAC = mat(0xFFE3D4F6, 0xFFC4ADE8, 0xFF9C86C9, 0xFF735FA0)
    private val SISAL = mat(0xFFF2DDAE, 0xFFDCC089, 0xFFBC9E63, 0xFF927847)
    private val SALMON = mat(0xFFFFC3A8, 0xFFF59A7A, 0xFFD27358, 0xFFA2533F)
    private val GINGER = k(0xFFF2A65A); private val SMOKE_CAT = k(0xFFB8B4C0)

    // Cidade moderna: concreto, vidro verde-água (nada de azul perto do Hoodie), neon.
    private val PAVE = k(0xFFD3D0CA); private val PAVE_D = k(0xFFBFBBB4); private val PAVE_L = k(0xFFE4E1DB)
    private val ASPHALT = k(0xFF54535E); private val CURB = k(0xFF9A98A0); private val LANE = k(0xFFF2D06B)
    private val CONCRETE = mat(0xFFEAE7E1, 0xFFCECAC3, 0xFFA8A49D, 0xFF7D7A74)
    private val STUCCO = mat(0xFFF7E7D2, 0xFFE9CFB0, 0xFFC9AB87, 0xFFA08462)
    private val GLASS = mat(0xFFB7EEE4, 0xFF7FCDC0, 0xFF559F95, 0xFF36746C)
    private val CHAR = mat(0xFF6E6A78, 0xFF55525F, 0xFF403D49, 0xFF2E2B36)
    private val BRICK = mat(0xFFE08A64, 0xFFC4683F, 0xFF9C4B2D, 0xFF733421)
    private val WOODP = mat(0xFFD9A774, 0xFFB9824F, 0xFF946238, 0xFF6E4627)
    private val RED = mat(0xFFF07A68, 0xFFD9503F, 0xFFAC3A33, 0xFF7E2A2A)
    private val YELLOW = mat(0xFFFFE7A0, 0xFFF6CF5E, 0xFFD6A93C, 0xFFA67E2A)
    private val LEAF = mat(0xFF94CC7C, 0xFF6FAE62, 0xFF4F8E4F, 0xFF3A6B40)

    private val COLS = listOf(44, 120, 196)
    private val ROW_TOP = listOf(14, 100, 186)
    private val LAMPS = listOf(76 to 80, 166 to 80, 76 to 166, 76 to 252, 166 to 252)
    private val DECOR = listOf(6 to 52, 232 to 40, 8 to 140, 232 to 250, 120 to 292)

    private fun hash(a: Int, b: Int): Int { var x = a * -0x61c88647 + b * 0x45d9f3b; x = (x xor (x ushr 16)) * 0x45d9f3b; return x xor (x ushr 16) }

    // ───────────── Peças comuns ─────────────

    private fun Canvas.oval(cx: Int, cy: Int, rx: Int, ry: Int, col: Int) {
        for (y in -ry..ry) for (x in -rx..rx) if (x * x * ry * ry + y * y * rx * rx <= rx * rx * ry * ry) set(cx + x, cy + y, col)
    }

    /** Bloco em 3/4: fachada, lateral recuando para cima e à direita, topo visto de cima. */
    private fun Canvas.prism(x0: Int, yt: Int, x1: Int, yb: Int, d: Int, m: Mat, top: Int) {
        poly(m.d, x1 to yt, x1 + d to yt - d / 2, x1 + d to yb - d / 2, x1 to yb)
        poly(top, x0 to yt, x0 + d to yt - d / 2, x1 + d to yt - d / 2, x1 to yt)
        rect(x0, yt, x1, yb, m.m)
        vline(x0, yt, yb, m.l); vline(x1, yt, yb, m.dd)
        outlinePoly(m.dd, x0 to yt, x0 + d to yt - d / 2, x1 + d to yt - d / 2, x1 + d to yb - d / 2, x1 to yb, x0 to yb)
    }

    private fun Canvas.cylinder(x0: Int, x1: Int, y0: Int, y1: Int, m: Mat) {
        for (x in x0..x1) {
            val t = (x - x0).toDouble() / maxOf(1, x1 - x0)
            vline(x, y0, y1, when { t < .2 -> m.l; t < .6 -> m.m; t < .85 -> m.d; else -> m.dd })
        }
        vline(x0 - 1, y0, y1, m.dd); vline(x1 + 1, y0, y1, m.dd)
    }

    private fun Canvas.shadow(x0: Int, x1: Int, y: Int, col: Int) {
        for (x in x0..x1) for (yy in y - 1..y + 2) if (yy == y || (x + yy) % 2 == 0) set(x, yy, col)
    }

    private fun Canvas.win(x: Int, y: Int, w: Int, h: Int, lit: Boolean, trim: Int, glass: Int) {
        rect(x - 1, y - 1, x + w, y + h, trim)
        rect(x, y, x + w - 1, y + h - 1, if (lit) LIT else glass)
        if (!lit) { set(x, y, WHITE); set(x + 1, y, WHITE) }
    }

    private fun Canvas.door(x: Int, y: Int, w: Int, h: Int, fill: Int, night: Boolean) {
        rect(x - 1, y - 1, x + w, y + h, OUT); rect(x, y, x + w - 1, y + h - 1, fill)
        set(x + w - 2, y + h / 2, if (night) LIT else WHITE)
    }

    private fun Canvas.smoke(x: Int, y: Int) { listOf(0 to 0, 2 to -3, 1 to -6).forEach { (dx, dy) -> disc(x + dx, y + dy, 1 + (-dy / 5), k(0xFFE8E4DC)) } }

    private fun pathPoints(): List<Pair<Int, Int>> {
        val ys = ROW_TOP.map { it + 74 }
        val pts = mutableListOf<Pair<Int, Int>>()
        fun seg(a: Int, b: Int, y: Int) { if (b >= a) { for (x in a..b) pts += x to y } else { for (x in a downTo b) pts += x to y } }
        fun turn(x: Int, y0: Int, y1: Int, right: Boolean) {
            val r = (y1 - y0) / 2; val cy = (y0 + y1) / 2
            for (a in 0..180 step 2) {
                val t = Math.toRadians(a - 90.0)
                pts += (x + ((if (right) 1 else -1) * cos(t) * r).toInt()) to (cy + (sin(t) * r).toInt())
            }
        }
        seg(COLS[0], COLS[2], ys[0]); turn(COLS[2], ys[0], ys[1], true)
        seg(COLS[2], COLS[0], ys[1]); turn(COLS[0], ys[1], ys[2], false)
        seg(COLS[0], COLS[2], ys[2])
        return pts
    }

    private fun Canvas.road(r: Int, edge: Int, fill: Int) {
        val p = pathPoints(); p.forEach { (x, y) -> disc(x, y, r, edge) }; p.forEach { (x, y) -> disc(x, y, r - 1, fill) }
    }

    // ───────────── Gatópolis ─────────────

    private fun Canvas.ears(xl: Int, xr: Int, y: Int, m: Mat) {
        listOf(xl, xr).forEach { x ->
            poly(m.m, x to y, x + 3 to y - 6, x + 7 to y); outlinePoly(m.dd, x to y, x + 3 to y - 6, x + 7 to y)
            poly(PINK.l, x + 2 to y - 1, x + 3 to y - 3, x + 5 to y - 1)
        }
    }

    private fun Canvas.paw(x: Int, y: Int, col: Int) { rect(x, y, x + 2, y + 1, col); set(x - 1, y - 2, col); set(x + 1, y - 3, col); set(x + 3, y - 2, col) }

    private fun Canvas.fish(cx: Int, cy: Int, len: Int, m: Mat) {
        val h = len / 2; val ry = maxOf(1, len / 4)
        poly(m.d, cx + h - 1 to cy, cx + h + ry + 1 to cy - ry - 1, cx + h + ry + 1 to cy + ry + 1)
        oval(cx, cy, h, ry, m.m)
        hline(cx - h + 2, cx + h - 2, cy - ry + 1, m.l); hline(cx - h + 2, cx + h - 2, cy + ry - 1, m.d)
        set(cx - h + 2, cy - ry / 2, OUT)
    }

    private fun Canvas.kitten(x: Int, y: Int, col: Int) {
        disc(x, y, 3, col)
        listOf(-1, 1).forEach { s -> set(x + 3 * s, y - 3, col); set(x + 2 * s, y - 3, col); set(x + 2 * s, y - 4, col) }
        set(x - 1, y, OUT); set(x + 1, y, OUT); set(x, y + 1, PINK.d)
    }

    private fun Canvas.yarn(x: Int, y: Int, r: Int, m: Mat) {
        disc(x, y, r, m.m)
        for (o in -r + 2 until r step 2) line(x - r + 1, y + o, x + o, y - r + 1, m.d)
        set(x - r / 2, y - r / 2, m.l)
    }

    private fun Canvas.candyTree(x: Int, y: Int, s: Int, m: Mat) {
        rect(x - 1, y, x + 1, y + s, CARD.d)
        disc(x, y - s / 2, s, m.d); disc(x - 1, y - s / 2 - 1, s - 1, m.m); disc(x - 3, y - s / 2 - 3, s - 4, m.l)
    }

    private fun Canvas.plot(ox: Int, oy: Int, light: Int, alt: Int, path: Int, pathD: Int) {
        for (y in oy + 40..oy + 50) for (x in ox + 2..ox + 46) {
            val dx = (x - ox - 24) / 23.0; val dy = (y - oy - 45) / 6.0
            if (dx * dx + dy * dy <= 1.0) set(x, y, if ((x + y) % 7 == 0) alt else light)
        }
        for (y in oy + 45..oy + 55) { set(ox + 19, y, pathD); for (x in ox + 20..ox + 25) set(x, y, path); set(ox + 26, y, pathD) }
    }
    private fun Canvas.catPlot(ox: Int, oy: Int) = plot(ox, oy, C_GRASS_L, C_GRASS, C_PATH, C_PATH_D)

    private fun Canvas.catHome(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        val x0 = ox + 8; val x1 = ox + 32; val yt = oy + 22; val yb = oy + 44; val d = 10
        shadow(x0, x1 + d + 2, yb, C_GRASS_D)
        // Aba de trás em pé, caixa, abas laterais abertas.
        poly(CARD.d, x0 + d to yt - d / 2, x0 + d + 2 to yt - d / 2 - 9, x1 + d + 2 to yt - d / 2 - 9, x1 + d to yt - d / 2)
        outlinePoly(CARD.dd, x0 + d to yt - d / 2, x0 + d + 2 to yt - d / 2 - 9, x1 + d + 2 to yt - d / 2 - 9, x1 + d to yt - d / 2)
        prism(x0, yt, x1, yb, d, CARD, CARD.dd)
        poly(CARD.l, x0 to yt, x0 + d to yt - d / 2, x0 + d - 8 to yt - d / 2 - 3, x0 - 8 to yt - 3)
        outlinePoly(CARD.dd, x0 to yt, x0 + d to yt - d / 2, x0 + d - 8 to yt - d / 2 - 3, x0 - 8 to yt - 3)
        poly(CARD.m, x1 to yt, x1 + d to yt - d / 2, x1 + d + 6 to yt - d / 2 + 2, x1 + 6 to yt + 2)
        outlinePoly(CARD.dd, x1 to yt, x1 + d to yt - d / 2, x1 + d + 6 to yt - d / 2 + 2, x1 + 6 to yt + 2)
        // Fita, porta em arco, janela com cortina, carimbo de patinha.
        rect(ox + 19, yt + 1, ox + 21, yt + 6, k(0xFFEBD9A8))
        val hole = k(0xFF4A3842)
        oval(ox + 16, oy + 36, 4, 4, hole); rect(ox + 12, oy + 36, ox + 20, yb - 1, hole)
        if (n) { rect(ox + 14, oy + 38, ox + 18, yb - 2, LIT) }
        win(ox + 25, oy + 29, 4, 4, n, CARD.dd, MINT.l); vline(ox + 25, oy + 29, oy + 32, PINK.m)
        paw(ox + 27, oy + 39, CARD.d)
        oval(ox + 42, oy + 44, 5, 2, PINK.d); oval(ox + 42, oy + 43, 4, 1, PINK.l)
    }

    private fun Canvas.catCafe(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        val cx = ox + 22
        shadow(ox + 10, ox + 40, oy + 44, C_GRASS_D)
        // Garrafa de leite gigante.
        cylinder(ox + 11, ox + 33, oy + 20, oy + 44, CREAM)
        for (i in 0..4) cylinder(ox + 15 - i, ox + 29 + i, oy + 15 + i, oy + 15 + i, CREAM)
        cylinder(ox + 16, ox + 28, oy + 9, oy + 14, CREAM)
        cylinder(ox + 15, ox + 29, oy + 5, oy + 8, PINK); oval(cx, oy + 5, 7, 1, PINK.l)
        hline(ox + 11, ox + 33, oy + 44, CREAM.dd)
        for (y in oy + 26..oy + 33) for (x in ox + 11..ox + 33) set(x, y, if (x < ox + 15) PINK.l else if (x < ox + 27) PINK.m else PINK.d)
        listOf(-2, -1, 1, 2).forEach { set(cx + it, oy + 28, WHITE) }
        for (x in cx - 2..cx + 2) set(x, oy + 29, WHITE)
        for (x in cx - 1..cx + 1) set(x, oy + 30, WHITE)
        set(cx, oy + 31, WHITE)
        win(cx - 2, oy + 10, 4, 3, n, CREAM.dd, MINT.l)
        door(cx - 3, oy + 36, 6, 8, MINT.d, n)
        // Mesinha com pires de leite.
        rect(ox + 38, oy + 36, ox + 46, oy + 37, MINT.m); hline(ox + 38, ox + 46, oy + 38, MINT.dd); vline(ox + 42, oy + 39, oy + 44, MINT.dd)
        oval(ox + 42, oy + 35, 3, 1, WHITE); smoke(ox + 42, oy + 31)
    }

    private fun Canvas.catOffice(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        val x0 = ox + 13; val x1 = ox + 31; val yt = oy + 8; val yb = oy + 44; val d = 8
        shadow(x0 - 8, x1 + d + 6, yb, C_GRASS_D)
        // Torre-arranhador de sisal com plataformas almofadadas.
        prism(x0, yt, x1, yb, d, SISAL, LILAC.m)
        for (y in yt + 2 until yb step 2) { hline(x0 + 1, x1 - 1, y, SISAL.d); line(x1 + 1, y, x1 + d - 1, y - d / 2 + 1, SISAL.dd) }
        ears(x0 + 1, x1 + d - 9, yt - d / 2 + 1, LILAC)
        listOf(oy + 19, oy + 31).forEachIndexed { i, y -> prism(ox + 5 + i * 3, y, ox + 39 - i * 2, y + 3, 6, LILAC, LILAC.l) }
        listOf(oy + 12, oy + 24).forEachIndexed { i, y ->
            win(x0 + 3, y, 4, 4, n && i == 0, SISAL.dd, MINT.l); win(x0 + 11, y, 4, 4, n, SISAL.dd, MINT.l)
        }
        door(ox + 20, oy + 37, 5, 7, LILAC.d, n)
        // Pena pendurada e bandeirinha de peixe.
        vline(ox + 38, oy + 23, oy + 29, OUT); oval(ox + 38, oy + 31, 1, 3, PINK.m); set(ox + 38, oy + 29, PINK.l)
        vline(ox + 22, oy - 6, yt - 4, OUT); rect(ox + 23, oy - 6, ox + 30, oy - 2, PINK.m); hline(ox + 25, ox + 28, oy - 4, WHITE); set(ox + 29, oy - 5, WHITE); set(ox + 29, oy - 3, WHITE)
    }

    private fun Canvas.catTavern(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        val x0 = ox + 6; val x1 = ox + 32; val yt = oy + 24; val yb = oy + 44; val d = 10
        shadow(x0, x1 + d + 2, yb, C_GRASS_D)
        // Peixaria: casinha creme, telhado menta e um peixe gigante no topo.
        prism(x0, yt, x1, yb, d, CREAM, MINT.m)
        prism(x0 - 2, yt - 3, x1 + 2, yt, d, MINT, MINT.l)
        vline(ox + 20, oy + 15, yt - 6, OUT)
        fish(ox + 19, oy + 12, 16, SALMON)
        for (x in x0 + 1..x0 + 14) for (y in yt + 3..yt + 6) set(x, y, if ((x / 3) % 2 == 0) MINT.m else WHITE)
        for (x in x0 + 1..x0 + 14 step 3) set(x, yt + 7, MINT.d)
        win(x0 + 3, yt + 10, 9, 5, n, CREAM.dd, MINT.l)
        door(x0 + 18, yt + 9, 6, 11, SALMON.d, n)
        // Caixote de peixes.
        rect(ox + 38, oy + 38, ox + 46, oy + 44, CARD.m); hline(ox + 38, ox + 46, oy + 41, CARD.d)
        outlinePoly(CARD.dd, ox + 38 to oy + 38, ox + 46 to oy + 38, ox + 46 to oy + 44, ox + 38 to oy + 44)
        listOf(ox + 40, ox + 44).forEach { x -> hline(x - 1, x + 1, oy + 37, SALMON.m); set(x + 2, oy + 36, SALMON.d); set(x + 2, oy + 38, SALMON.d) }
    }

    private fun Canvas.catGym(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        shadow(ox + 4, ox + 46, oy + 44, C_GRASS_D)
        // Arranhador em pórtico com bolinhas e pena pendurados.
        prism(ox + 4, oy + 40, ox + 38, oy + 44, 6, PINK, PINK.l)
        listOf(ox + 8, ox + 31).forEach { px ->
            for (y in oy + 12..oy + 39) hline(px, px + 5, y, if (y % 2 == 0) SISAL.m else SISAL.d)
            vline(px, oy + 12, oy + 39, SISAL.l); vline(px - 1, oy + 12, oy + 39, SISAL.dd); vline(px + 6, oy + 12, oy + 39, SISAL.dd)
        }
        prism(ox + 5, oy + 8, ox + 39, oy + 12, 5, LILAC, LILAC.l)
        ears(ox + 6, ox + 32, oy + 6, LILAC)
        listOf(Triple(ox + 17, oy + 24, PINK), Triple(ox + 22, oy + 31, MINT), Triple(ox + 27, oy + 21, YELLOW)).forEach { (x, y, m) ->
            vline(x, oy + 13, y - 3, OUT); disc(x, y, 2, m.m); set(x - 1, y - 1, m.l); set(x + 1, y + 1, m.d)
        }
        // Halter de novelos no tapete.
        hline(ox + 17, ox + 25, oy + 41, LILAC.dd); disc(ox + 16, oy + 41, 2, PINK.m); disc(ox + 26, oy + 41, 2, PINK.m)
        if (n) for (x in ox + 8..ox + 38 step 5) set(x, oy + 10, LIT)
    }

    private fun Canvas.catMarket(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        listOf(Triple(ox + 1, oy + 14, PINK), Triple(ox + 24, oy + 18, MINT)).forEachIndexed { s, (x, y, m) ->
            shadow(x, x + 22, y + 27, C_GRASS_D)
            vline(x + 1, y + 4, y + 26, CARD.dd); vline(x + 20, y + 4, y + 26, CARD.dd)
            ears(x + 1, x + 13, y, m)
            for (xx in x..x + 21) for (yy in y..y + 5) set(xx, yy, if ((xx / 3) % 2 == 0) m.m else WHITE)
            for (xx in x..x + 21 step 3) { set(xx, y + 6, m.m); set(xx + 1, y + 6, m.m); set(xx + 1, y + 7, m.d) }
            rect(x + 2, y + 18, x + 19, y + 26, CARD.m); hline(x + 2, x + 19, y + 18, CARD.l); hline(x + 2, x + 19, y + 26, CARD.dd)
            if (s == 0) listOf(5, 11, 17).forEach { i -> fish(x + i - 1, y + 15, 6, SALMON) }
            else listOf(PINK, LILAC, YELLOW).forEachIndexed { i, c ->
                rect(x + 3 + i * 6, y + 12, x + 7 + i * 6, y + 17, c.m); hline(x + 3 + i * 6, x + 7 + i * 6, y + 12, c.l); paw(x + 5 + i * 6, y + 15, c.d)
            }
            if (n) set(x + 10, y + 8, LIT)
        }
    }

    private fun Canvas.catSchool(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        shadow(ox + 6, ox + 44, oy + 44, C_GRASS_D)
        // Pilha de livros gigantes com novelo no topo.
        val books = listOf(Triple(oy + 36, 30, PINK), Triple(oy + 28, 26, MINT), Triple(oy + 20, 28, LILAC), Triple(oy + 13, 22, SALMON))
        books.forEachIndexed { i, (y, w, m) ->
            val x0 = ox + 20 - w / 2 + listOf(0, 2, -1, 1)[i]; val x1 = x0 + w; val d = 7
            prism(x0, y, x1, y + 7, d, m, m.l)
            poly(CREAM.m, x1 + 1 to y, x1 + d to y - d / 2, x1 + d to y + 7 - d / 2, x1 + 1 to y + 7)
            for (yy in y + 2..y + 6 step 2) line(x1 + 1, yy, x1 + d - 1, yy - d / 2 + 1, CREAM.d)
            vline(x1 + d, y - d / 2, y + 7 - d / 2, m.dd)
            hline(x0 + 3, x1 - 3, y + 2, YELLOW.m); hline(x0 + 3, x1 - 3, y + 5, YELLOW.m)
        }
        door(ox + 18, oy + 37, 5, 7, CARD.dd, n)
        win(ox + 17, oy + 23, 4, 3, n, LILAC.dd, MINT.l)
        yarn(ox + 22, oy + 5, 5, PINK); line(ox + 26, oy + 8, ox + 36, oy + 14, PINK.d)
        kitten(ox + 41, oy + 40, SMOKE_CAT)
    }

    private fun Canvas.catPark(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        candyTree(ox + 24, oy + 22, 9, PINK)
        // Moitas de erva-de-gato, almofada ao sol, potinho d'água, borboleta.
        listOf(ox + 6 to oy + 34, ox + 42 to oy + 32).forEach { (x, y) ->
            disc(x, y, 5, MINT.d); disc(x - 1, y - 1, 4, MINT.m); disc(x - 2, y - 2, 2, MINT.l)
            set(x + 1, y - 3, LILAC.m); set(x - 3, y, LILAC.m); set(x + 2, y + 1, LILAC.l)
        }
        oval(ox + 24, oy + 40, 12, 5, PINK.d); oval(ox + 24, oy + 39, 11, 4, PINK.m); oval(ox + 24, oy + 39, 8, 2, CREAM.l)
        oval(ox + 26, oy + 39, 4, 2, GINGER); kitten(ox + 21, oy + 38, GINGER)
        oval(ox + 41, oy + 45, 4, 2, LILAC.d); oval(ox + 41, oy + 44, 3, 1, MINT.l)
        set(ox + 36, oy + 14, LILAC.m); set(ox + 38, oy + 14, LILAC.m); set(ox + 37, oy + 15, OUT)
        if (n) listOf(ox + 8 to oy + 24, ox + 40 to oy + 20, ox + 14 to oy + 14).forEach { (x, y) -> set(x, y, LIT) }
    }

    private fun Canvas.catFamily(ox: Int, oy: Int, n: Boolean) {
        catPlot(ox, oy)
        val cx = ox + 22
        shadow(cx - 18, cx + 22, oy + 44, C_GRASS_D)
        // Cesto de vime com a ninhada.
        for (a in 0..180 step 3) { val t = Math.toRadians(a.toDouble()); disc(cx + (cos(t) * 15).toInt(), oy + 27 - (sin(t) * 16).toInt(), 1, SISAL.d) }
        oval(cx, oy + 28, 18, 6, SISAL.dd); oval(cx, oy + 29, 16, 4, PINK.m)
        kitten(cx - 8, oy + 27, GINGER); kitten(cx, oy + 25, SMOKE_CAT); kitten(cx + 8, oy + 27, CREAM.m)
        for (x in cx - 18..cx + 18) {
            val dx = (x - cx) / 18.0; val rim = oy + 28 + (6 * sqrt(maxOf(0.0, 1 - dx * dx))).toInt()
            for (y in rim..oy + 43) {
                val w = 18 - (y - oy - 30).coerceAtLeast(0) / 4
                if (kotlin.math.abs(x - cx) <= w) set(x, y, if (((x / 2) + (y / 2)) % 2 == 0) SISAL.m else SISAL.d)
            }
            set(x, rim, SISAL.l); set(x, rim + 1, SISAL.l)
        }
        hline(cx - 14, cx + 14, oy + 44, SISAL.dd)
        yarn(ox + 44, oy + 42, 3, LILAC)
        if (n) set(cx, oy + 9, LIT)
    }

    private fun Canvas.catGround() {
        rect(0, 0, W - 1, H - 1, C_GRASS)
        for (y in 0 until H) for (x in 0 until W) when (hash(x, y) and 127) {
            0 -> set(x, y, C_GRASS_D)
            1 -> { set(x, y, C_GRASS_L); set(x, y - 1, C_GRASS_L) }
            2 -> if (y % 3 == 0) set(x, y, C_FLOWER)
            3 -> if (x % 4 == 0) set(x, y, WHITE)
        }
    }

    private fun Canvas.catRoad() {
        road(4, C_PATH_D, C_PATH)
        pathPoints().forEachIndexed { i, (x, y) -> if (i % 16 == 0) paw(x - 1, y + if ((i / 16) % 2 == 0) -1 else 2, C_PAW) }
    }

    private fun Canvas.catDecor() {
        listOf(PINK, MINT, LILAC, PINK, MINT).zip(DECOR).forEach { (m, p) -> candyTree(p.first, p.second, 6, m) }
        yarn(82, 40, 3, LILAC); yarn(158, 128, 3, MINT)
    }

    private fun Canvas.catSign(cx: Int, y: Int, cur: Boolean) {
        val x0 = cx - 22; val x1 = cx + 22
        ears(x0 + 2, x1 - 9, y + 1, if (cur) YELLOW else PINK)
        rect(x0, y, x1, y + 11, if (cur) SIGN_CUR else PINK.m)
        rect(x0 + 1, y + 1, x1 - 1, y + 10, if (cur) SIGN_CUR_L else CREAM.l)
        outlinePoly(PINK.dd, x0 to y, x1 to y, x1 to y + 11, x0 to y + 11)
        hline(x0 + 4, x0 + 22, y + 4, PINK.dd); hline(x0 + 4, x0 + 16, y + 7, PINK.d)
        paw(x1 - 7, y + 6, PINK.d)
    }

    private fun Canvas.catLamp(x: Int, y: Int, n: Boolean): Pair<Int, Int> {
        vline(x, y - 10, y, LILAC.dd)
        rect(x - 2, y - 14, x + 2, y - 11, if (n) LIT else PINK.l)
        outlinePoly(LILAC.dd, x - 3 to y - 15, x + 3 to y - 15, x + 3 to y - 10, x - 3 to y - 10)
        set(x - 3, y - 16, LILAC.dd); set(x + 3, y - 16, LILAC.dd)
        return x to y - 12
    }

    // ───────────── Cidade moderna ─────────────

    private fun Canvas.cityPlot(ox: Int, oy: Int) {
        rect(ox + 1, oy + 40, ox + 47, oy + 49, PAVE_L); hline(ox + 1, ox + 47, oy + 49, CONCRETE.d)
        rect(ox + 2, oy + 46, ox + 17, oy + 48, LEAF.m); rect(ox + 28, oy + 46, ox + 46, oy + 48, LEAF.m)
        hline(ox + 2, ox + 17, oy + 46, LEAF.l); hline(ox + 28, ox + 46, oy + 46, LEAF.l)
        for (y in oy + 49..oy + 55) { set(ox + 19, y, CONCRETE.d); for (x in ox + 20..ox + 25) set(x, y, PAVE_L); set(ox + 26, y, CONCRETE.d) }
    }

    private fun Canvas.cityTree(x: Int, y: Int, s: Int) {
        vline(x, y, y + s, WOODP.d)
        rect(x - 3, y + s, x + 3, y + s + 2, CONCRETE.m); hline(x - 3, x + 3, y + s, CONCRETE.l)
        disc(x, y - s / 2, s, LEAF.d); disc(x - 1, y - s / 2 - 1, s - 1, LEAF.m); disc(x - 3, y - s / 2 - 3, s - 4, LEAF.l)
    }

    private fun Canvas.car(x: Int, y: Int, m: Mat, n: Boolean) {
        rect(x - 3, y - 6, x + 3, y - 3, m.m); rect(x - 2, y - 5, x + 2, y - 4, GLASS.l)
        rect(x - 6, y - 3, x + 6, y + 1, m.m); hline(x - 6, x + 6, y - 3, m.l); hline(x - 6, x + 6, y + 1, m.dd)
        disc(x - 4, y + 2, 1, OUT); disc(x + 4, y + 2, 1, OUT)
        set(x + 6, y - 1, if (n) LIT else YELLOW.l)
    }

    private fun Canvas.cityHome(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 9; val x1 = ox + 31; val yt = oy + 4; val yb = oy + 44; val d = 9
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        // Prédio residencial com varandas e plantas.
        prism(x0, yt, x1, yb, d, STUCCO, CONCRETE.m)
        for (f in 0..2) {
            val y = yt + 5 + f * 9
            win(x0 + 3, y, 5, 5, n && hash(f, 1) and 1 == 0, STUCCO.dd, GLASS.l)
            win(x0 + 14, y, 5, 5, n && hash(f, 2) and 1 == 0, STUCCO.dd, GLASS.l)
            hline(x0 - 1, x1 + 1, y + 6, CONCRETE.d); for (x in x0 - 1..x1 + 1 step 2) set(x, y + 5, CONCRETE.dd)
            set(x0 + 10, y + 4, LEAF.m); set(x0 + 11, y + 4, LEAF.d); set(x0 + 10, y + 3, LEAF.l)
            rect(x1 + 3, y - 2, x1 + 5, y + 1, if (n && f != 1) LIT else GLASS.d)
        }
        hline(ox + 16, ox + 26, oy + 35, CHAR.m)
        door(ox + 18, oy + 36, 6, 8, GLASS.d, n)
        cylinder(ox + 14, ox + 19, oy - 3, oy + 1, CONCRETE)
        rect(ox + 24, oy - 1, ox + 28, oy + 1, CONCRETE.d)
    }

    private fun Canvas.cityOffice(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 11; val x1 = ox + 33; val yt = oy - 4; val yb = oy + 44; val d = 9
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        // Torre de vidro verde-água com letreiro em neon.
        prism(x0, yt, x1, yb, d, GLASS, CONCRETE.l)
        if (n) for (cx in x0 + 1 until x1 step 4) for (cy in yt + 6 until yb - 8 step 5) if (hash(cx, cy) and 3 != 0) rect(cx, cy, cx + 2, cy + 3, LIT)
        for (x in x0 + 4 until x1 step 4) vline(x, yt + 5, yb - 1, GLASS.d)
        for (y in yt + 5 until yb step 5) { hline(x0 + 1, x1 - 1, y, GLASS.dd); line(x1 + 1, y, x1 + d - 1, y - d / 2 + 1, GLASS.dd) }
        if (!n) for (t in 0..10) { set(x0 + 2 + t, yt + 22 - t, GLASS.l); set(x0 + 3 + t, yt + 22 - t, GLASS.l) }
        rect(x0 + 1, yt + 1, x1 - 1, yt + 4, CHAR.dd); for (x in x0 + 4..x1 - 4) if (x % 3 != 0) set(x, yt + 2, NEON_O)
        hline(x0 + 4, x1 - 4, yb - 8, CHAR.m); rect(x0 + 6, yb - 7, x1 - 6, yb - 1, if (n) LIT else CHAR.d); vline((x0 + x1) / 2, yb - 7, yb - 1, CHAR.dd)
        vline(x1 + 4, yt - 12, yt - 4, CHAR.m); set(x1 + 4, yt - 13, RED.m)
    }

    private fun Canvas.cityCafe(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 4; val x1 = ox + 30; val yt = oy + 18; val yb = oy + 44; val d = 9
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        prism(x0, yt, x1, yb, d, CHAR, CONCRETE.m)
        for (x in x0 + 2 until x1 step 3) vline(x, yt + 1, yt + 10, CHAR.d)
        // Xícara em neon + letreiro.
        hline(x0 + 4, x0 + 10, yt + 3, NEON_P); vline(x0 + 4, yt + 3, yt + 7, NEON_P); vline(x0 + 10, yt + 3, yt + 7, NEON_P); hline(x0 + 5, x0 + 9, yt + 8, NEON_P)
        set(x0 + 11, yt + 4, NEON_P); set(x0 + 12, yt + 5, NEON_P); set(x0 + 11, yt + 6, NEON_P); set(x0 + 6, yt + 1, NEON_P); set(x0 + 8, yt, NEON_P)
        for (x in x0 + 15..x1 - 3) if (x % 4 != 3) set(x, yt + 5, NEON_O)
        for (x in x0..x1) for (y in yt + 11..yt + 13) set(x, y, if ((x / 3) % 2 == 0) RED.m else WHITE)
        for (x in x0..x1 step 3) set(x, yt + 14, RED.d)
        win(x0 + 3, yt + 17, 12, 7, n, CHAR.dd, GLASS.l)
        door(x0 + 18, yt + 17, 6, 9, GLASS.d, n)
        // Mesa na calçada com guarda-sol.
        poly(RED.m, ox + 35 to oy + 31, ox + 41 to oy + 26, ox + 47 to oy + 31); hline(ox + 35, ox + 47, oy + 31, RED.d)
        vline(ox + 41, oy + 32, oy + 43, CHAR.m)
        oval(ox + 41, oy + 39, 4, 1, CONCRETE.l); rect(ox + 35, oy + 40, ox + 36, oy + 44, WOODP.d); rect(ox + 46, oy + 40, ox + 47, oy + 44, WOODP.d)
    }

    private fun Canvas.cityTavern(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 3; val x1 = ox + 37; val yt = oy + 22; val yb = oy + 39; val d = 6
        shadow(x0, x1 + d + 2, oy + 43, PAVE_D)
        vline(ox + 46, oy + 12, oy + 44, CHAR.m)
        // Food truck.
        prism(x0, yt, x1, yb, d, YELLOW, CONCRETE.l)
        rect(x1 - 9, yt + 2, x1 - 2, yt + 8, GLASS.l); vline(x1 - 10, yt + 1, yb - 1, YELLOW.dd)
        for (x in x0 + 2..x0 + 21) for (y in yt + 1..yt + 3) set(x, y, if ((x / 3) % 2 == 0) RED.m else WHITE)
        rect(x0 + 3, yt + 5, x0 + 20, yt + 11, if (n) LIT else CHAR.dd)
        hline(x0 + 2, x0 + 21, yt + 12, YELLOW.dd)
        rect(x0 + 12, yb - 4, x0 + 19, yb - 1, CHAR.d); for (x in x0 + 13..x0 + 18 step 2) set(x, yb - 3, WHITE)
        listOf(x0 + 7, x1 - 6).forEach { wx -> disc(wx, yb + 1, 3, CHAR.dd); disc(wx, yb + 1, 1, CONCRETE.m) }
        // Varal de luzes até o poste.
        val sx = x0 + 4; val sy = yt - 4; val ex = ox + 46; val ey = oy + 12
        for (x in sx..ex) {
            val t = (x - sx).toDouble() / (ex - sx); val y = (sy + (ey - sy) * t + 4 * sin(Math.PI * t)).toInt()
            set(x, y, CHAR.dd)
            if ((x - sx) % 4 == 2) set(x, y + 1, if (n) LIT else listOf(RED.l, YELLOW.l, LEAF.l)[(x / 4) % 3])
        }
    }

    private fun Canvas.cityGym(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 5; val x1 = ox + 33; val yt = oy + 14; val yb = oy + 44; val d = 9
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        prism(x0, yt, x1, yb, d, CONCRETE, CHAR.m)
        // Letreiro com halter + fachada de vidro com esteiras.
        rect(x0 + 1, yt + 2, x1 - 1, yt + 7, CHAR.dd)
        val sign = if (n) NEON_O else RED.l
        hline(x0 + 10, x0 + 18, yt + 4, sign); rect(x0 + 8, yt + 3, x0 + 9, yt + 6, sign); rect(x0 + 19, yt + 3, x0 + 20, yt + 6, sign)
        rect(x0 + 6, yt + 4, x0 + 7, yt + 5, sign); rect(x0 + 21, yt + 4, x0 + 22, yt + 5, sign)
        rect(x0 + 2, yt + 10, x1 - 2, yb - 1, if (n) LIT else GLASS.m)
        for (x in x0 + 2..x1 - 2 step 7) vline(x, yt + 10, yb - 1, CONCRETE.d)
        hline(x0 + 2, x1 - 2, yt + 19, CONCRETE.d)
        for (i in 0..2) { val tx = x0 + 4 + i * 8; rect(tx, yb - 4, tx + 5, yb - 3, CHAR.dd); vline(tx + 5, yb - 9, yb - 3, CHAR.dd); hline(tx + 4, tx + 6, yb - 9, CHAR.dd) }
        for (i in 0..3) { val wx = x0 + 4 + i * 6; hline(wx, wx + 3, yt + 15, CHAR.dd); set(wx, yt + 14, CHAR.dd); set(wx + 3, yt + 14, CHAR.dd) }
        if (!n) for (t in 0..6) set(x0 + 3 + t, yt + 17 - t, GLASS.l)
    }

    private fun Canvas.cityMarket(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 2; val x1 = ox + 34; val yt = oy + 22; val yb = oy + 44; val d = 8
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        // Supermercado: faixa verde, vitrine, banca de frutas e carrinhos.
        prism(x0, yt, x1, yb, d, CONCRETE, CONCRETE.l)
        rect(x0 + 1, yt + 2, x1 - 1, yt + 7, LEAF.d); for (x in x0 + 4..x1 - 4) if (x % 4 != 0) set(x, yt + 4, WHITE); hline(x0 + 1, x1 - 1, yt + 7, LEAF.dd)
        rect(x0 + 3, yt + 10, x1 - 3, yb - 1, if (n) LIT else GLASS.l)
        for (x in x0 + 3..x1 - 3 step 6) vline(x, yt + 10, yb - 1, CONCRETE.d)
        rect(x0 + 13, yt + 12, x0 + 20, yb - 1, CHAR.d); vline(x0 + 16, yt + 12, yb - 1, CHAR.dd)
        rect(x0 + 3, yb - 5, x0 + 11, yb - 1, WOODP.m); hline(x0 + 3, x0 + 11, yb - 5, WOODP.l)
        listOf(RED.m, YELLOW.m, LEAF.l, GINGER).forEachIndexed { i, f -> set(x0 + 4 + i * 2, yb - 6, f); set(x0 + 5 + i * 2, yb - 6, f) }
        listOf(ox + 40 to oy + 42, ox + 45 to oy + 43).forEach { (cx, cy) ->
            outlinePoly(CHAR.m, cx - 2 to cy - 3, cx + 2 to cy - 3, cx + 1 to cy, cx - 1 to cy)
            set(cx, cy - 2, CONCRETE.l); set(cx + 3, cy - 4, CHAR.m); set(cx - 1, cy + 1, OUT); set(cx + 1, cy + 1, OUT)
        }
    }

    private fun Canvas.citySchool(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 2; val x1 = ox + 36; val yt = oy + 22; val yb = oy + 44; val d = 7
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        // Prédio de tijolo com torre do relógio, escadaria e mastro.
        prism(x0, yt, x1, yb, d, BRICK, CONCRETE.m)
        for (y in yt + 1 until yb) for (x in x0 + 1 until x1) if (y % 3 == 0 || ((x + (y / 3) * 3) % 6 == 0)) set(x, y, BRICK.d)
        listOf(ox + 5, ox + 29).forEach { wx -> win(wx, yt + 4, 4, 5, n, CONCRETE.l, GLASS.l); win(wx, yt + 13, 4, 5, n && wx > ox + 10, CONCRETE.l, GLASS.l) }
        prism(ox + 14, oy + 4, ox + 24, yt + 6, 5, CONCRETE, RED.m)
        poly(RED.m, ox + 13 to oy + 4, ox + 25 to oy + 4, ox + 30 to oy + 1, ox + 21 to oy - 5)
        outlinePoly(RED.dd, ox + 13 to oy + 4, ox + 25 to oy + 4, ox + 30 to oy + 1, ox + 21 to oy - 5)
        disc(ox + 19, oy + 11, 3, WHITE); set(ox + 19, oy + 9, OUT); set(ox + 19, oy + 10, OUT); set(ox + 20, oy + 11, OUT)
        door(ox + 16, yb - 9, 7, 8, WOODP.d, n)
        for (s in 0..2) hline(ox + 14 - s, ox + 24 + s, yb + s - 1, if (s % 2 == 0) CONCRETE.l else CONCRETE.d)
        vline(ox + 43, oy + 12, oy + 44, CONCRETE.d); rect(ox + 44, oy + 12, ox + 48, oy + 15, LEAF.m)
    }

    private fun Canvas.cityPark(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        // Quadra, tabela, rampa de skate e árvore no canteiro.
        rect(ox + 3, oy + 26, ox + 29, oy + 44, k(0xFF6FA67A))
        outlinePoly(WHITE, ox + 4 to oy + 27, ox + 28 to oy + 27, ox + 28 to oy + 43, ox + 4 to oy + 43)
        hline(ox + 4, ox + 28, oy + 35, WHITE)
        for (a in 0 until 360 step 20) { val t = Math.toRadians(a.toDouble()); set(ox + 16 + (cos(t) * 4).toInt(), oy + 35 + (sin(t) * 2).toInt(), WHITE) }
        vline(ox + 16, oy + 16, oy + 27, CHAR.m); rect(ox + 12, oy + 11, ox + 20, oy + 16, WHITE)
        outlinePoly(CHAR.m, ox + 12 to oy + 11, ox + 20 to oy + 11, ox + 20 to oy + 16, ox + 12 to oy + 16); hline(ox + 14, ox + 18, oy + 17, RED.m)
        disc(ox + 9, oy + 40, 1, GINGER)
        for (i in 0..11) { val h = i * i / 10 + 2; vline(ox + 33 + i, oy + 44 - h, oy + 44, if (i < 4) CONCRETE.l else CONCRETE.m); set(ox + 33 + i, oy + 44 - h, CONCRETE.dd) }
        rect(ox + 45, oy + 30, ox + 47, oy + 44, CONCRETE.d); hline(ox + 43, ox + 47, oy + 30, CHAR.m)
        cityTree(ox + 40, oy + 14, 7)
        if (n) set(ox + 16, oy + 12, LIT)
    }

    private fun Canvas.cityFamily(ox: Int, oy: Int, n: Boolean) {
        cityPlot(ox, oy)
        val x0 = ox + 5; val x1 = ox + 31; val yt = oy + 14; val yb = oy + 44; val d = 9
        shadow(x0, x1 + d + 2, yb, PAVE_D)
        // Sobrado moderno com madeira, janelões e churrasqueira.
        prism(x0, yt, x1, yb, d, STUCCO, CHAR.m)
        rect(x0 + 13, yt + 1, x1 - 1, yt + 13, WOODP.m); for (x in x0 + 14 until x1 step 2) vline(x, yt + 1, yt + 13, WOODP.d); vline(x0 + 13, yt + 1, yt + 13, WOODP.l)
        hline(x0, x1, yt + 14, CHAR.m)
        win(x0 + 3, yt + 4, 6, 6, n, CHAR.dd, GLASS.l)
        win(x0 + 16, yt + 4, 8, 5, false, CHAR.dd, GLASS.l)
        win(x0 + 3, yt + 18, 10, 9, n, CHAR.dd, GLASS.l)
        door(x0 + 17, yt + 19, 6, 11, WOODP.d, n)
        oval(ox + 41, oy + 37, 3, 2, CHAR.dd); vline(ox + 39, oy + 38, oy + 43, CHAR.m); vline(ox + 43, oy + 38, oy + 43, CHAR.m)
        set(ox + 40, oy + 35, RED.l); set(ox + 42, oy + 35, YELLOW.m)
        smoke(ox + 41, oy + 32)
        cityTree(ox + 44, oy + 16, 6)
    }

    private fun Canvas.cityGround() {
        rect(0, 0, W - 1, H - 1, PAVE)
        for (y in 0 until H) for (x in 0 until W) {
            if (x % 12 == 0 || y % 12 == 0) set(x, y, PAVE_D) else if (hash(x, y) and 63 == 0) set(x, y, PAVE_L)
        }
    }

    private fun Canvas.cityRoad() {
        road(6, CURB, ASPHALT)
        pathPoints().forEachIndexed { i, (x, y) -> if (i % 8 < 4) set(x, y, LANE) }
    }

    private fun Canvas.cityDecor(n: Boolean) {
        DECOR.forEach { (x, y) -> cityTree(x, y, 6) }
        car(150, ROW_TOP[0] + 74, RED, n); car(90, ROW_TOP[2] + 74, YELLOW, n)
    }

    private fun Canvas.citySign(cx: Int, y: Int, cur: Boolean) {
        val x0 = cx - 22; val x1 = cx + 22
        rect(x0, y, x1, y + 11, if (cur) SIGN_CUR else k(0xFF2F7D5B))
        rect(x0 + 1, y + 1, x1 - 1, y + 10, if (cur) SIGN_CUR_L else k(0xFF3D9970))
        outlinePoly(OUT, x0 to y, x1 to y, x1 to y + 11, x0 to y + 11)
        val ink = if (cur) OUT else WHITE
        hline(x0 + 4, x0 + 24, y + 4, ink); hline(x0 + 4, x0 + 16, y + 7, ink)
    }

    private fun Canvas.cityLamp(x: Int, y: Int, n: Boolean): Pair<Int, Int> {
        vline(x, y - 12, y, CHAR.m); hline(x, x + 3, y - 12, CHAR.m)
        rect(x + 2, y - 11, x + 4, y - 10, if (n) LIT else CONCRETE.l)
        return x + 3 to y - 9
    }

    // ───────────── Mapa ─────────────

    private fun glow(b: PixelBuffer, cx: Int, cy: Int, r: Int) {
        val px = b.pixels
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (x !in 0 until W || y !in 0 until H) continue
            val dd = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()); if (dd > r) continue
            val a = (1 - dd / r) * 0.45; val i = y * W + x; val p = px[i]
            val rr = ((p shr 16 and 0xFF) * (1 - a) + 0xFF * a).toInt()
            val gg = ((p shr 8 and 0xFF) * (1 - a) + 0xD8 * a).toInt()
            val bb = ((p and 0xFF) * (1 - a) + 0x77 * a).toInt()
            px[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }

    private fun map(
        night: Boolean,
        ground: Canvas.() -> Unit,
        road: Canvas.() -> Unit,
        decor: Canvas.(Boolean) -> Unit,
        stops: List<Canvas.(Int, Int, Boolean) -> Unit>,
        sign: Canvas.(Int, Int, Boolean) -> Unit,
        lamp: Canvas.(Int, Int, Boolean) -> Pair<Int, Int>,
        tint: Triple<Int, Int, Int> = Triple(120, 132, 200),
    ): PixelBuffer {
        val c = Canvas(PixelBuffer(W, H))
        c.ground(); c.road(); c.decor(night)
        val glows = LAMPS.map { (x, y) -> c.lamp(x, y, night) }
        val order = listOf(0, 1, 2, 2, 1, 0, 0, 1, 2)                     // serpentina: linha 2 volta
        stops.forEachIndexed { i, draw ->
            val cx = COLS[order[i]]; val top = ROW_TOP[i / 3]
            c.draw(cx - 24, top, night)
            c.sign(cx, top + 56, i == 4)
        }
        if (night) {
            val px = c.b.pixels
            for (i in px.indices) {
                val p = px[i]; if (p in KEEP) continue
                val r = (p shr 16 and 0xFF) * tint.first / 256; val g = (p shr 8 and 0xFF) * tint.second / 256; val b = (p and 0xFF) * tint.third / 256
                px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            glows.forEach { (x, y) -> glow(c.b, x, y, 9) }
        }
        JourneyHoodieMarker.paint(c.b, JourneyMarkerState(MapPoint((COLS[1] + 36).toFloat(), (ROW_TOP[1] + 76).toFloat()), MarkerDirection.FRONT, false, JourneyVehicle.ON_FOOT, true), 0)
        return c.b
    }

    private fun catMap(n: Boolean) = map(
        n, { catGround() }, { catRoad() }, { catDecor() },
        listOf(
            { x, y, nn -> catHome(x, y, nn) }, { x, y, nn -> catCafe(x, y, nn) }, { x, y, nn -> catOffice(x, y, nn) },
            { x, y, nn -> catTavern(x, y, nn) }, { x, y, nn -> catGym(x, y, nn) }, { x, y, nn -> catMarket(x, y, nn) },
            { x, y, nn -> catSchool(x, y, nn) }, { x, y, nn -> catPark(x, y, nn) }, { x, y, nn -> catFamily(x, y, nn) },
        ),
        { x, y, cur -> catSign(x, y, cur) }, { x, y, nn -> catLamp(x, y, nn) },
    )

    private fun cityMap(n: Boolean) = map(
        n, { cityGround() }, { cityRoad() }, { nn -> cityDecor(nn) },
        listOf(
            { x, y, nn -> cityHome(x, y, nn) }, { x, y, nn -> cityCafe(x, y, nn) }, { x, y, nn -> cityOffice(x, y, nn) },
            { x, y, nn -> cityTavern(x, y, nn) }, { x, y, nn -> cityGym(x, y, nn) }, { x, y, nn -> cityMarket(x, y, nn) },
            { x, y, nn -> citySchool(x, y, nn) }, { x, y, nn -> cityPark(x, y, nn) }, { x, y, nn -> cityFamily(x, y, nn) },
        ),
        { x, y, cur -> citySign(x, y, cur) }, { x, y, nn -> cityLamp(x, y, nn) }, Triple(92, 94, 116),
    )

    @Test fun exportThemes() {
        val dir = File(PreviewExport.dir, "journey-mockup")
        val shots = listOf("cat-day" to catMap(false), "cat-night" to catMap(true), "city-day" to cityMap(false), "city-night" to cityMap(true))
        shots.forEach { (name, b) -> PreviewExport.write(File(dir, "$name.png"), b, 3, null) }
        val grid = PixelBuffer(W * 4 + 24, H).apply { fill(0xFF2B2E4A.toInt()); shots.forEachIndexed { i, (_, b) -> blit(b, i * (W + 8), 0) } }
        PreviewExport.write(File(dir, "themes.png"), grid, 2, null)
    }
}
