package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine.FOOTER
import com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine.HEADER
import com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine.TILE
import com.hoodie.app.pixel.diary.journey.JourneyPalette as P
import com.hoodie.app.pixel.renderer.PixelBuffer
import kotlin.math.abs
import kotlin.random.Random

/** Árvore: base do tronco em (x, y); [phase] desencontra o balanço entre árvores. */
data class JourneyTree(val x: Int, val y: Int, val phase: Int, val big: Boolean)

/** Decoração do mapa, posicionada uma única vez por layout (determinística). */
data class JourneyDecor(
    val trees: List<JourneyTree>,
    val bushes: List<MapPoint>,
    val flowers: List<MapPoint>,
    val benches: List<MapPoint>,
    val ponds: List<JourneyRect>,
    /** Topo da cabeça de cada poste. */
    val lamps: List<MapPoint>,
    /** Saída da chaminé das casas. */
    val chimneys: List<MapPoint>,
) {
    companion object {
        val EMPTY = JourneyDecor(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * O mundo em volta da jornada. Parado o mapa ainda vive — mas sem poluição:
 * cada efeito é pequeno, lento e em poucos pixels (plano §8.2).
 *
 *  estático (cache): céu-base, grama texturizada, ruas, rua do rodapé, arbustos, flores, bancos, lagos, postes
 *  animado (antes da luz): copas balançando, sombras de nuvem, brilho da água, fumaça, carrinhos, passarinhos, folhas
 *  animado (depois da luz): postes acesos, vaga-lumes, faróis
 */
object JourneyAmbientRenderer {

    // ───────────── Posicionamento ─────────────

    fun place(layout: JourneyLayout): JourneyDecor {
        if (layout.isEmpty) return emptyDecor(layout)
        val rnd = Random(seed(layout))
        val cols = layout.width / TILE
        val rows = layout.height / TILE
        val blocked = Array(rows) { BooleanArray(cols) }
        fun block(r: JourneyRect) {
            for (row in (r.y / TILE).coerceAtLeast(0)..((r.bottom - 1) / TILE).coerceAtMost(rows - 1))
                for (col in (r.x / TILE).coerceAtLeast(0)..((r.right - 1) / TILE).coerceAtMost(cols - 1)) blocked[row][col] = true
        }
        // Céu, rodapé, prédios, plataformas, cartões e selos.
        block(JourneyRect(0, 0, layout.width, HEADER + TILE / 2))
        block(JourneyRect(0, layout.height - FOOTER, layout.width, FOOTER))
        layout.reserved.forEach(::block)
        // Ruas (com folga de um tile de cada lado).
        layout.segments.forEach { s ->
            s.path.points.zipWithNext().forEach { (a, b) ->
                block(JourneyRect(minOf(a.x, b.x).toInt() - 10, minOf(a.y, b.y).toInt() - 10, abs(b.x - a.x).toInt() + 20, abs(b.y - a.y).toInt() + 20))
            }
        }
        // Postes nas esquinas de fora de cada travessia.
        val lamps = layout.segments.flatMap { s ->
            listOf(MapPoint(26f, s.crossY - 14f), MapPoint(layout.width - 26f, s.crossY - 14f))
        }.filter { p -> layout.reserved.none { it.grow(2).contains(p) } }
        lamps.forEach { block(JourneyRect(it.x.toInt() - 4, it.y.toInt() - 2, 9, 16)) }

        // Lagos: até 1 a cada 3 paradas, em áreas livres de 4×2 tiles.
        val ponds = mutableListOf<JourneyRect>()
        val wanted = layout.nodes.size / 3
        var tries = 0
        while (ponds.size < wanted && tries++ < 200) {
            val col = rnd.nextInt(0, cols - 4); val row = rnd.nextInt(HEADER / TILE, rows - 2)
            if ((row until row + 2).all { r -> (col until col + 4).all { c -> !blocked[r][c] } }) {
                val r = JourneyRect(col * TILE, row * TILE, 4 * TILE, 2 * TILE)
                ponds += r; block(r.grow(TILE))
            }
        }
        val trees = mutableListOf<JourneyTree>()
        val bushes = mutableListOf<MapPoint>()
        val flowers = mutableListOf<MapPoint>()
        val benches = mutableListOf<MapPoint>()
        for (row in 1 until rows) for (col in 0 until cols) {
            if (blocked[row][col]) continue
            val x = col * TILE; val y = row * TILE
            val r = rnd.nextFloat()
            when {
                // Árvore precisa do tile de cima livre (a copa sobe).
                r < 0.20f && !blocked[row - 1][col] -> { trees += JourneyTree(x + 4, y + 7, rnd.nextInt(0, 4), rnd.nextFloat() < 0.4f); blocked[row - 1][col] = true }
                r < 0.30f -> bushes += MapPoint(x + 4f, y + 6f)
                r < 0.42f -> flowers += MapPoint(x + rnd.nextInt(1, 6).toFloat(), y + rnd.nextInt(2, 6).toFloat())
                r < 0.44f -> benches += MapPoint(x + 1f, y + 4f)
            }
        }
        val chimneys = layout.nodes.filter { it.type == PlaceType.HOME }.map {
            val (bx, by) = it.building.pixels.let { p -> p[0] to p[1] }
            MapPoint(bx + 18f, by + 1f)
        }
        return JourneyDecor(trees.sortedBy { it.y }, bushes, flowers, benches, ponds, lamps, chimneys)
    }

    private fun emptyDecor(layout: JourneyLayout): JourneyDecor {
        // Dia vazio: um canteiro tranquilo, sem ruas.
        val y = HEADER + 40
        return JourneyDecor(
            trees = listOf(JourneyTree(40, y, 0, true), JourneyTree(200, y + 8, 2, false), JourneyTree(120, y + 30, 1, true)),
            bushes = listOf(MapPoint(70f, y + 20f), MapPoint(170f, y + 16f)),
            flowers = listOf(MapPoint(90f, y + 40f), MapPoint(150f, y + 44f), MapPoint(60f, y + 52f)),
            benches = emptyList(), ponds = listOf(JourneyRect(152, y + 46, 32, 16)), lamps = emptyList(), chimneys = emptyList(),
        ).takeIf { layout.height > y + 60 } ?: JourneyDecor.EMPTY
    }

    /** Mesmo dia → mesma cidade (a semente depende dos tipos e da ordem das paradas). */
    fun seed(layout: JourneyLayout): Int = layout.nodes.fold(17) { acc, n -> acc * 31 + n.type.ordinal * 7 + n.index }

    // ───────────── Camada estática ─────────────

    fun paintGround(b: PixelBuffer, layout: JourneyLayout, decor: JourneyDecor) {
        // Grama com textura em xadrez e tufos.
        b.box(0, HEADER, b.width - 1, b.height - 1, P.GRASS)
        for (y in HEADER until b.height) for (x in 0 until b.width) {
            when (noise(x, y) and 31) {
                0, 1 -> b.set(x, y, P.GRASS_DARK)
                2 -> b.set(x, y, P.GRASS_LIGHT)
            }
        }
        // Horizonte: faixa de grama mais escura logo abaixo do céu.
        b.box(0, HEADER, b.width - 1, HEADER + 1, P.GRASS_DARK)
        footerStreet(b, layout)
        decor.ponds.forEach { pond(b, it) }
        decor.flowers.forEachIndexed { i, f -> flower(b, f.x.toInt(), f.y.toInt(), if (i % 2 == 0) P.FLOWER_A else P.FLOWER_B) }
        decor.bushes.forEach { bush(b, it.x.toInt(), it.y.toInt()) }
        decor.benches.forEach { bench(b, it.x.toInt(), it.y.toInt()) }
        decor.lamps.forEach { lampPost(b, it.x.toInt(), it.y.toInt()) }
    }

    /** Ruído por pixel bem misturado (sem as diagonais de um hash linear). */
    private fun noise(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) ushr 4
    }

    private fun footerStreet(b: PixelBuffer, layout: JourneyLayout) {
        val top = layout.height - FOOTER + 10
        b.box(0, top - 3, b.width - 1, top - 1, P.SIDEWALK)
        b.hline(0, b.width - 1, top - 3, P.SIDEWALK_DARK)
        b.box(0, top, b.width - 1, top + 13, P.ROAD)
        b.hline(0, b.width - 1, top, P.ROAD_EDGE)
        for (x in 0 until b.width step 12) b.hline(x, x + 5, top + 7, 0xFFE7D99A.toInt())
        b.box(0, top + 14, b.width - 1, top + 16, P.SIDEWALK)
    }

    private fun pond(b: PixelBuffer, r: JourneyRect) {
        // Borda arredondada em degraus.
        b.box(r.x + 2, r.y, r.right - 3, r.bottom - 1, P.OUTLINE)
        b.box(r.x, r.y + 2, r.right - 1, r.bottom - 3, P.OUTLINE)
        b.box(r.x + 2, r.y + 1, r.right - 3, r.bottom - 2, P.WATER)
        b.box(r.x + 1, r.y + 2, r.right - 2, r.bottom - 3, P.WATER)
        b.hline(r.x + 3, r.right - 4, r.y + 2, P.WATER_LIGHT)
    }

    private fun flower(b: PixelBuffer, x: Int, y: Int, c: Int) {
        b.set(x, y + 1, P.TREE); b.set(x, y, c); b.set(x - 1, y, c); b.set(x + 1, y, c); b.set(x, y - 1, c)
        b.set(x, y, P.FLOWER_B.takeIf { c != P.FLOWER_B } ?: P.FLOWER_A)
    }

    /** Arbusto redondinho de duas bolas, com brilho em cima. */
    private fun bush(b: PixelBuffer, x: Int, y: Int) {
        b.hline(x - 3, x + 4, y + 2, 0x44000000)
        b.disc(x - 1, y - 1, 3, P.OUTLINE); b.disc(x + 2, y, 2, P.OUTLINE)
        b.disc(x - 1, y - 1, 2, P.TREE); b.disc(x + 2, y, 1, P.TREE)
        b.set(x - 2, y - 2, P.TREE_LIGHT); b.set(x - 1, y - 3, P.TREE_LIGHT); b.set(x + 2, y - 1, P.TREE_LIGHT)
    }

    private fun bench(b: PixelBuffer, x: Int, y: Int) {
        b.box(x, y, x + 6, y + 1, P.BENCH)
        b.hline(x, x + 6, y - 2, P.BENCH)
        b.set(x, y + 2, P.OUTLINE); b.set(x + 6, y + 2, P.OUTLINE)
    }

    private fun lampPost(b: PixelBuffer, x: Int, y: Int) {
        b.vline(x, y + 2, y + 13, P.LAMP)
        b.hline(x - 1, x + 1, y + 13, P.LAMP)
        b.box(x - 1, y, x + 1, y + 1, P.LAMP)
    }

    // ───────────── Céu (dinâmico: muda com a hora) ─────────────

    fun paintSky(b: PixelBuffer, light: JourneyLight, timeMs: Long) {
        // Degradê em faixas com xadrez entre elas: "degradê" de pixel art.
        val bands = 4
        val h = HEADER
        for (i in 0 until bands) {
            val c = PixelBuffer.mix(light.skyTop, light.skyBottom, i / (bands - 1f))
            val y0 = i * h / bands; val y1 = (i + 1) * h / bands - 1
            b.box(0, y0, b.width - 1, y1, c)
            if (i > 0) b.dither(0, y0, b.width - 1, y0, PixelBuffer.mix(light.skyTop, light.skyBottom, (i - 0.5f) / (bands - 1f)))
        }
        // Estrelas piscando à noite.
        if (light.starAlpha > 0) STARS.forEachIndexed { i, (sx, sy) ->
            val on = ((timeMs / 700) + i) % 5 != 0L
            if (on) b.set(sx, sy, (light.starAlpha shl 24) or 0xFFF4C8)
        }
        // Sol ou lua.
        val moon = light.period == DayPeriod.NIGHT
        val orbX = if (moon) 200 else 34
        b.disc(orbX, 9, 4, if (moon) 0xFFE9EDFF.toInt() else 0xFFFFE08A.toInt())
        if (moon) b.disc(orbX + 2, 8, 3, light.skyTop)
        // Nuvens deslizando devagar (um pixel a cada ~0,4 s).
        CLOUDS.forEach { (baseX, y, w) ->
            val x = (((baseX + timeMs / 400) % (b.width + 40)) - 30).toInt()
            cloud(b, x, y, w, if (light.period == DayPeriod.NIGHT) 0xFF4A4E7A.toInt() else P.CLOUD)
        }
        // Silhueta de morrinhos no horizonte.
        val hill = PixelBuffer.mix(light.skyBottom, P.GRASS_DARK, 0.65f)
        for (x in 0 until b.width) {
            val top = h - 3 - ((kotlin.math.sin(x / 19.0) + kotlin.math.sin(x / 7.3) * 0.4) * 2.2 + 2).toInt()
            b.vline(x, top.coerceIn(h - 8, h - 1), h - 1, hill)
        }
    }

    private val STARS = listOf(12 to 3, 47 to 7, 83 to 2, 121 to 5, 158 to 3, 176 to 9, 214 to 4, 231 to 11, 66 to 12, 140 to 13)
    private val CLOUDS = listOf(Triple(10L, 4, 18), Triple(110L, 9, 24), Triple(190L, 2, 14))

    private fun cloud(b: PixelBuffer, x: Int, y: Int, w: Int, c: Int) {
        b.box(x + 2, y, x + w - 3, y + 4, c)
        b.box(x, y + 2, x + w - 1, y + 4, c)
        b.box(x + w / 3, y - 2, x + 2 * w / 3, y, c)
        b.hline(x + 1, x + w - 2, y + 4, P.CLOUD_SHADE and 0xAAFFFFFF.toInt())
    }

    // ───────────── Animado, antes da luz ─────────────

    fun paintAnimated(b: PixelBuffer, layout: JourneyLayout, decor: JourneyDecor, light: JourneyLight, timeMs: Long) {
        decor.ponds.forEach { waterShine(b, it, timeMs) }
        cloudShadows(b, layout, timeMs)
        decor.trees.forEach { tree(b, it, timeMs) }
        decor.chimneys.forEach { smoke(b, it, timeMs) }
        footerCars(b, layout, light, timeMs)
        if (light.period != DayPeriod.NIGHT) birds(b, layout, timeMs)
        if (light.period == DayPeriod.EVENING) leaves(b, layout, timeMs)
    }

    /** Copa balança 1 px para os lados, cada árvore no seu tempo (≈ 1,6 s por ciclo). */
    fun swayOffset(tree: JourneyTree, timeMs: Long): Int = when (((timeMs / 400) + tree.phase) % 4) {
        1L -> 1
        3L -> -1
        else -> 0
    }

    private fun tree(b: PixelBuffer, t: JourneyTree, timeMs: Long) {
        val s = swayOffset(t, timeMs)
        val r = if (t.big) 5 else 4
        b.hline(t.x - r + 1, t.x + r, t.y + 1, 0x44000000)
        b.box(t.x - 1, t.y - 4, t.x + 1, t.y, P.OUTLINE)
        b.vline(t.x, t.y - 4, t.y - 1, P.TRUNK)
        val cy = t.y - 4 - r
        b.disc(t.x + s, cy, r + 1, P.OUTLINE)
        b.disc(t.x + s, cy, r, P.TREE)
        b.disc(t.x + s - 1, cy - 1, r - 2, P.TREE_LIGHT)
        b.set(t.x + s + r - 2, cy + r - 2, P.TREE_DARK); b.set(t.x + s + 1, cy + r - 1, P.TREE_DARK)
    }

    private fun waterShine(b: PixelBuffer, r: JourneyRect, timeMs: Long) {
        val phase = ((timeMs / 300) % (r.w - 6)).toInt()
        b.hline(r.x + 3 + phase, r.x + 5 + phase, r.y + 4, P.WATER_LIGHT)
        val p2 = ((timeMs / 450 + r.w / 2) % (r.w - 8)).toInt()
        b.hline(r.x + 4 + p2, r.x + 5 + p2, r.bottom - 5, 0xFFFFFFFF.toInt() and 0xAAFFFFFF.toInt())
    }

    /** Sombras de nuvem atravessando o chão bem devagar. */
    private fun cloudShadows(b: PixelBuffer, layout: JourneyLayout, timeMs: Long) {
        val span = layout.width + 120
        listOf(0L to HEADER + 40, 170L to HEADER + 200, 80L to HEADER + 380).forEach { (base, y) ->
            if (y > layout.height - FOOTER) return@forEach
            val x = (((base + timeMs / 300) % span) - 60).toInt()
            for (dy in -6..6) {
                val half = 30 - dy * dy / 2
                b.hline(x - half, x + half, y + dy, 0x14000000)
            }
        }
    }

    private fun smoke(b: PixelBuffer, from: MapPoint, timeMs: Long) {
        for (k in 0 until 3) {
            val t = ((timeMs + k * 400) % 1_200).toInt()
            val y = from.y.toInt() - t / 120
            val x = from.x.toInt() + (t / 300) % 2
            b.set(x, y, P.SMOKE and 0xCCFFFFFF.toInt()); b.set(x + 1, y, P.SMOKE and 0x88FFFFFF.toInt())
        }
    }

    /** Dois carrinhos na rua do rodapé, em sentidos opostos. */
    private fun footerCars(b: PixelBuffer, layout: JourneyLayout, light: JourneyLight, timeMs: Long) {
        val top = layout.height - FOOTER + 10
        val span = layout.width + 30
        val a = ((timeMs / 60) % span).toInt() - 15
        val c = layout.width - (((timeMs / 85) + 90) % span).toInt() + 15
        miniCar(b, a, top + 9, P.CAR_B, facingRight = true, lights = light.lightsOn)
        miniCar(b, c, top + 4, P.CAR_C, facingRight = false, lights = light.lightsOn)
    }

    private fun miniCar(b: PixelBuffer, x: Int, y: Int, color: Int, facingRight: Boolean, lights: Boolean) {
        b.outlined(x - 5, y - 3, x + 5, y + 1, color, P.OUTLINE)
        b.box(x - 2, y - 5, x + 2, y - 3, P.OUTLINE); b.box(x - 1, y - 4, x + 1, y - 3, P.GLASS)
        b.set(x - 3, y + 2, P.TIRE); b.set(x + 3, y + 2, P.TIRE)
        if (lights) b.set(if (facingRight) x + 6 else x - 6, y - 1, P.LAMP_LIGHT)
    }

    /** Uma dupla de passarinhos atravessa o mapa a cada ~12 s, batendo as asas. */
    private fun birds(b: PixelBuffer, layout: JourneyLayout, timeMs: Long) {
        val cycle = 12_000L
        val t = timeMs % cycle
        if (t > 6_000) return
        val round = (timeMs / cycle).toInt()
        val y0 = HEADER + 12 + (round * 97) % (layout.height - HEADER - FOOTER).coerceAtLeast(1)
        val x = (t * (layout.width + 40) / 6_000).toInt() - 20
        val flap = (timeMs / 180) % 2 == 0L
        listOf(0 to 0, -7 to 3).forEach { (dx, dy) -> bird(b, x + dx, y0 + dy + ((t / 500) % 2).toInt(), flap) }
    }

    private fun bird(b: PixelBuffer, x: Int, y: Int, up: Boolean) {
        if (up) { b.set(x - 2, y - 1, P.BIRD); b.set(x - 1, y, P.BIRD); b.set(x, y, P.BIRD); b.set(x + 1, y, P.BIRD); b.set(x + 2, y - 1, P.BIRD) }
        else { b.set(x - 2, y + 1, P.BIRD); b.set(x - 1, y, P.BIRD); b.set(x, y, P.BIRD); b.set(x + 1, y, P.BIRD); b.set(x + 2, y + 1, P.BIRD) }
    }

    /** Entardecer: algumas folhas caindo em diagonal. */
    private fun leaves(b: PixelBuffer, layout: JourneyLayout, timeMs: Long) {
        val h = (layout.height - HEADER - FOOTER).coerceAtLeast(1)
        for (i in 0 until 6) {
            val t = (timeMs / 90 + i * 53) % h
            val x = ((i * 41 + t / 3 + (if ((t / 8) % 2 == 0L) 1 else 0)) % layout.width).toInt()
            b.set(x, HEADER + t.toInt(), P.LEAF)
        }
    }

    // ───────────── Animado, depois da luz (brilha no escuro) ─────────────

    fun paintLights(b: PixelBuffer, layout: JourneyLayout, decor: JourneyDecor, light: JourneyLight, timeMs: Long) {
        if (!light.lightsOn) {
            decor.lamps.forEach { b.box(it.x.toInt() - 1, it.y.toInt(), it.x.toInt() + 1, it.y.toInt() + 1, P.LAMP) }
            return
        }
        decor.lamps.forEach { JourneyLightingRenderer.lampHalo(b, it.x.toInt(), it.y.toInt()) }
        // Vaga-lumes perto das árvores, piscando fora de sincronia.
        if (light.period == DayPeriod.NIGHT) decor.trees.forEachIndexed { i, t ->
            if (i % 3 != 0) return@forEachIndexed
            if (((timeMs / 500) + i) % 4 == 0L) return@forEachIndexed
            val dx = ((timeMs / 700 + i) % 7).toInt() - 3
            b.set(t.x + 6 + dx, t.y - 6 - (i % 4), P.SPARK)
        }
    }
}
