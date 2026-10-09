package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

/**
 * Cidade da rua em coordenadas de mundo: cada trecho é gerado uma vez, com largura conhecida
 * ([Strip.period]), e se repete sem emenda porque a cópia seguinte começa exatamente onde a
 * anterior termina. O estado das janelas depende só do prédio, da linha e da coluna — nunca da
 * posição na tela —, então nada pisca enquanto a cidade corre.
 */
object StreetSkyline {
    /** [x] é relativo ao início do trecho; o prédio ocupa as colunas x..x+w. */
    data class Building(val id: Int, val x: Int, val w: Int, val h: Int)

    /** [gap] é a distância entre o fim de um prédio e o começo do próximo. */
    class Strip(val buildings: List<Building>, val period: Int, val gap: Int)

    private const val LIT = 0xFFFFD86B.toInt()
    private const val GLASS = 0xFF9DB7DE.toInt()

    fun strip(seed: Int, count: Int, gap: Int, width: (Int) -> Int, height: (Int) -> Int): Strip {
        var x = 0
        val list = (0 until count).map { k ->
            val id = seed * 1000 + k
            val w = width(id)
            Building(id, x, w, height(id)).also { x += w + gap }
        }
        return Strip(list, x, gap)
    }

    /** Silhuetas do fundo (mesmas proporções da antiga `SceneArt.city`). */
    val FAR = strip(seed = 3, count = 24, gap = 2, width = { 10 + (it * 7) % 12 }, height = { 12 + (it * 13) % 26 })

    /** Prédios próximos: 56 px de largura, 3 alturas, 80 px de passo. */
    val NEAR = strip(seed = 11, count = 7, gap = 24, width = { 56 }, height = { 70 + Math.floorMod(it * 7 + it / 3, 3) * 18 })

    fun isWindowLit(buildingId: Int, row: Int, col: Int, period: DayPeriod): Boolean {
        if (period == DayPeriod.DAY || period == DayPeriod.MORNING) return false
        return Math.floorMod(buildingId * 31 + row * 17 + col * 7, 4) != 0
    }

    /** Chama [block] para cada prédio de [strip] que aparece em [x0]..[x1] com o deslocamento [scroll]. */
    inline fun forEachVisible(strip: Strip, x0: Int, x1: Int, scroll: Long, block: (Building, Int) -> Unit) {
        var base = x0 - Math.floorMod(scroll, strip.period.toLong()).toInt()
        val list = strip.buildings
        while (base <= x1) {
            for (i in list.indices) {
                val bd = list[i]
                val bx = base + bd.x
                if (bx > x1) break
                if (bx + bd.w >= x0) block(bd, bx)
            }
            base += strip.period
        }
    }

    fun drawFar(b: PixelBuffer, x0: Int, x1: Int, baseY: Int, scroll: Long, period: DayPeriod) {
        val s = SceneArt.sky(period)
        forEachVisible(FAR, x0, x1, scroll) { bd, x ->
            box(b, x, baseY - bd.h, x + bd.w, baseY, s.city, x0, x1)
            val lit = s.cityWindow ?: return@forEachVisible
            var row = 0
            var wy = baseY - bd.h + 3
            while (wy < baseY - 1) {
                var col = 0
                var wx = x + 2
                while (wx < x + bd.w - 1) {
                    if (wx in x0..x1 && isWindowLit(bd.id, row, col, period)) b.set(wx, wy, lit)
                    wx += 3; col++
                }
                wy += 4; row++
            }
        }
    }

    fun drawNear(b: PixelBuffer, x0: Int, x1: Int, baseY: Int, scroll: Long, wall: Int, outline: Int, period: DayPeriod) {
        val night = period == DayPeriod.NIGHT || period == DayPeriod.EVENING
        forEachVisible(NEAR, x0, x1, scroll) { bd, x ->
            val top = baseY - bd.h
            box(b, x, top, x + bd.w, baseY, outline, x0, x1)
            box(b, x + 1, top + 1, x + bd.w - 1, baseY - 1, wall, x0, x1)
            var row = 0
            var wy = top + 6
            while (wy < baseY - 6) {
                var col = 0
                var wx = x + 6
                while (wx < x + bd.w - 6) {
                    val c = when {
                        isWindowLit(bd.id, row, col, period) -> LIT
                        night -> wall
                        else -> GLASS
                    }
                    box(b, wx, wy, wx + 5, wy + 5, c, x0, x1)
                    wx += 12; col++
                }
                wy += 10; row++
            }
        }
    }

    /** Caixa recortada em [x0]..[x1] (o `PixelBuffer.rect` não recorta caixas fora da borda direita). */
    private fun box(b: PixelBuffer, ax: Int, ay: Int, bx: Int, by: Int, color: Int, x0: Int, x1: Int) {
        val l = maxOf(ax, x0, 0); val r = minOf(bx, x1, b.width - 1)
        if (l <= r) b.box(l, ay, r, by, color)
    }
}
