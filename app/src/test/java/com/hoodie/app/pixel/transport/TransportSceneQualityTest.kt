package com.hoodie.app.pixel.transport

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.art.SceneArt
import com.hoodie.app.pixel.art.SceneArtCompiler
import com.hoodie.app.pixel.art.SceneArtStore
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.LayeredTransportScene
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.sprite.ArtReviewStatus
import com.hoodie.app.pixel.sprite.HoodiePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Portões de qualidade das cenas de transporte V3 (docs/transport-art-bible.md). Reprovam arte
 * ruim de forma objetiva; a aprovação final continua sendo humana (scene-art-status.json).
 */
class TransportSceneQualityTest {
    private val scenes = mapOf("car" to MovementMode.CAR, "train" to MovementMode.TRAIN, "metro" to MovementMode.METRO, "bus" to MovementMode.BUS)

    // ───────────── Medidas ─────────────

    private fun luma(c: Int) = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)

    /** Matiz (0..360) e saturação (0..1). */
    private fun hueSat(c: Int): Pair<Double, Double> {
        val r = (c shr 16 and 0xFF) / 255.0; val g = (c shr 8 and 0xFF) / 255.0; val b = (c and 0xFF) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d < 1e-6) return 0.0 to 0.0
        val h = when (mx) { r -> 60 * (((g - b) / d) % 6); g -> 60 * ((b - r) / d + 2); else -> 60 * ((r - g) / d + 4) }
        return ((h + 360) % 360) to (if (mx == 0.0) 0.0 else d / mx)
    }

    private fun hueDistance(a: Double, b: Double) = abs(a - b).let { min(it, 360 - it) }

    private data class HoodieContrast(val lumaDiff: Double, val hueDiff: Double, val hoodiePixels: Int)

    /** Hoodie visível (difere da cena vazia) × anel de 3 px em volta, na cena renderizada. */
    private fun contrast(full: PixelBuffer, empty: PixelBuffer): HoodieContrast {
        val w = full.width; val h = full.height
        val hoodie = BooleanArray(w * h) { full.pixels[it] != empty.pixels[it] }
        val ring = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (hoodie[y * w + x]) {
            for (dy in -3..3) for (dx in -3..3) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && !hoodie[yy * w + xx]) ring[yy * w + xx] = true
            }
        }
        fun stats(mask: BooleanArray): Triple<Double, Double, Int> {
            var l = 0.0; var hx = 0.0; var hy = 0.0; var n = 0
            mask.forEachIndexed { i, on ->
                if (!on) return@forEachIndexed
                val c = full.pixels[i]
                if (luma(c) < DARK_LUMA) return@forEachIndexed   // contorno: igual nos dois lados, não conta
                l += luma(c)
                val (hu, s) = hueSat(c); hx += kotlin.math.cos(Math.toRadians(hu)) * s; hy += kotlin.math.sin(Math.toRadians(hu)) * s; n++
            }
            return Triple(l / n.coerceAtLeast(1), (Math.toDegrees(kotlin.math.atan2(hy, hx)) + 360) % 360, n)
        }
        val (lh, hh, n) = stats(hoodie)
        val (lr, hr, _) = stats(ring)
        return HoodieContrast(abs(lh - lr), hueDistance(hh, hr), n)
    }

    /** Maior retângulo de uma só cor (px) fora do céu — mede painel chapado. */
    private var lastFlat = ""

    private fun largestFlatRect(img: PixelBuffer, ignore: BooleanArray): Int {
        val w = img.width; val h = img.height
        val heights = IntArray(w)
        var best = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                heights[x] = if (ignore[i]) 0 else if (y > 0 && !ignore[i - w] && img.pixels[i] == img.pixels[i - w]) heights[x] + 1 else 1
            }
            // Histograma por sequência de mesma cor na linha.
            var x = 0
            while (x < w) {
                val color = img.pixels[y * w + x]
                var end = x
                while (end + 1 < w && img.pixels[y * w + end + 1] == color && !ignore[y * w + end + 1] && !ignore[y * w + x]) end++
                val stack = ArrayDeque<Int>()
                for (k in x..end + 1) {
                    val hk = if (k > end) 0 else heights[k]
                    while (stack.isNotEmpty() && heights[stack.last()] >= hk) {
                        val top = stack.removeLast()
                        val left = if (stack.isEmpty()) x else stack.last() + 1
                        val area = heights[top] * (k - left)
                        // Painel = área com as duas dimensões >= 6 px; frisos, molduras e barras são linhas.
                        if (heights[top] >= PANEL_MIN_SIDE && k - left >= PANEL_MIN_SIDE && area > best) { best = area; lastFlat = "x $left..${k - 1}, y ${y - heights[top] + 1}..$y, cor #%08X".format(color) }
                    }
                    if (k <= end) stack.addLast(k)
                }
                x = end + 1
            }
        }
        return best
    }

    /** Pixels que só o `bg_far` cobre (céu e silhueta distante), na composição estática. */
    private fun skyMask(art: SceneArt, p: DayPeriod): BooleanArray {
        val n = art.width * art.height
        val other = BooleanArray(n)
        listOf("bg_mid", "bg_near", "vehicle_back", "vehicle_front", "foreground").forEach { l ->
            art.layer(l, p)?.pixels?.forEachIndexed { i, c -> if (c ushr 24 != 0) other[i] = true }
        }
        return BooleanArray(n) { !other[it] }
    }

    // ───────────── Portões ─────────────

    @Test fun hoodieStandsOutFromWhatSurroundsHim() {
        scenes.forEach { (name, mode) ->
            DayPeriod.entries.forEach { p ->
                val shot = TransportSceneV3Review.render(mode, p)
                val empty = withV3 { SceneRenderer().renderEmpty(shot.frame.scene, shot.frame.env, shot.timeMs).let { b -> PixelBuffer(b.width, b.height).also { it.copyFrom(b) } } }
                val c = contrast(shot.image, empty)
                assertTrue("$name/$p: Hoodie quase invisível (${c.hoodiePixels} px)", c.hoodiePixels > 300)
                assertTrue("$name/$p: pouco contraste em volta do Hoodie (Δluma ${"%.0f".format(c.lumaDiff)}, Δmatiz ${"%.0f".format(c.hueDiff)}°)", c.lumaDiff >= 35 || c.hueDiff >= 45)
            }
        }
    }

    @Test fun theContrastGateRejectsAHoodieOnBlue() {
        // Controle: o mesmo Hoodie sobre um fundo azul do próprio Hoodie precisa reprovar.
        val shot = TransportSceneV3Review.render(MovementMode.CAR, DayPeriod.DAY)
        val empty = PixelBuffer(240, 320).apply { fill(HoodiePalette.FUR) }
        val full = PixelBuffer(240, 320).apply { fill(HoodiePalette.FUR) }
        val f = shot.frame
        full.blit(f.sprite.image, f.x - f.sprite.anchors.feet.x, f.y - f.sprite.anchors.feet.y)
        val c = contrast(full, empty)
        assertFalse("o portão deveria reprovar Hoodie sobre azul (Δluma ${c.lumaDiff}, Δmatiz ${c.hueDiff})", c.lumaDiff >= 35 || c.hueDiff >= 45)
    }

    @Test fun noFlatPanelsOutsideTheSky() {
        // Medido na cena como o usuário vê (com o Hoodie na frente), fora do céu.
        scenes.forEach { (name, mode) ->
            val art = SceneArtStore.get(name).also { assertNotNull("arte $name ausente", it) }!!
            DayPeriod.entries.forEach { p ->
                val img = TransportSceneV3Review.render(mode, p).image
                // Fora do céu e fora de sombra profunda/contorno (sólidos por convenção).
                val sky = skyMask(art, p)
                val ignore = BooleanArray(sky.size) { sky[it] || luma(img.pixels[it]) < DARK_LUMA }
                val flat = largestFlatRect(img, ignore)
                assertTrue("$name/$p: painel chapado de $flat px ($lastFlat; máx. ${FLAT_MAX})", flat <= FLAT_MAX)
            }
        }
    }

    @Test fun noLargeSurfaceInTheHoodieBlue() {
        val (furHue, _) = hueSat(HoodiePalette.FUR)
        scenes.keys.forEach { name ->
            val art = SceneArtStore.get(name)!!
            listOf("vehicle_back", "vehicle_front", "foreground").forEach { l ->
                val blue = art.layer(l, DayPeriod.DAY)?.pixels?.count { c ->
                    c ushr 24 != 0 && luma(c) >= DARK_LUMA && hueSat(c).let { (h, s) -> s > 0.2 && hueDistance(h, furHue) < 25 }
                } ?: 0
                assertTrue("$name/$l: $blue px no azul do Hoodie (máx. $BLUE_MAX)", blue <= BLUE_MAX)
            }
        }
    }

    @Test fun paletteStaysWithinTwentyFourColors() {
        File("../assets-source/scenes/transport").listFiles { f -> f.extension == "aseprite" }.orEmpty().forEach { f ->
            val colors = SceneArtCompiler.sceneColors(com.hoodie.app.pixel.art.AsepriteFile.read(f)).size
            assertTrue("${f.name}: $colors cores", colors <= SceneArtCompiler.MAX_SCENE_COLORS)
        }
    }

    @Test fun hoodieIsDrawnAtOneToOneAndSeatedOnTheSlot() {
        scenes.forEach { (name, mode) ->
            val shot = TransportSceneV3Review.render(mode, DayPeriod.DAY)
            val scene = withV3 { SceneRegistry[shot.frame.scene.id] } as LayeredTransportScene
            val art = SceneArtStore.get(name)!!
            val sprite = shot.frame.sprite
            val hipSlot = art.slot("seat_hip")
            // Onde o sprite deve cair: quadril no `seat_hip` (de frente) ou pés no `seat_feet` (carro).
            val (left, top) = if (hipSlot != null) {
                val hip = sprite.anchors.seatHip
                assertNotNull("$name: o Hoodie deveria sentar de frente (SIT_FRONT com seat_hip)", hip)
                (hipSlot.x - hip!!.x) to (hipSlot.y - hip.y)
            } else {
                val seat = art.slot("seat_feet")!!
                (seat.x - sprite.anchors.feet.x) to (seat.y - sprite.anchors.feet.y)
            }
            val spot = scene.spot(SpotId.SEAT)
            assertEquals("$name: Hoodie fora do banco no meio da viagem", spot.x to spot.y, shot.frame.x to shot.frame.y)
            // 1:1: a cena desenha o sprite sem escala, exatamente no slot.
            val out = PixelBuffer(240, 320)
            scene.drawCharacter(out, sprite, spot.x - sprite.anchors.feet.x, spot.y - sprite.anchors.feet.y, 0L, shot.frame.env.copy(transportAmbient = null))
            var same = 0; var total = 0
            for (y in 0 until sprite.image.height) for (x in 0 until sprite.image.width) if (sprite.image[x, y] ushr 24 != 0) {
                total++; if (out[left + x, top + y] == sprite.image[x, y]) same++
            }
            assertEquals("$name: Hoodie redimensionado ou fora do slot", total, same)
        }
    }

    @Test fun exportSilhouettes() {
        // Só exporta: o veículo em preto sólido, para o revisor conferir que dá para reconhecer o que é.
        scenes.keys.forEach { name ->
            val art = SceneArtStore.get(name)!!
            val img = PixelBuffer(art.width, art.height).apply { fill(0xFFF2EFE8.toInt()) }
            listOf("vehicle_back", "vehicle_front", "foreground").forEach { l ->
                art.layer(l, DayPeriod.DAY)?.pixels?.forEachIndexed { i, c -> if (c ushr 24 != 0) img.pixels[i] = 0xFF101010.toInt() }
            }
            PreviewExport.write(File(PreviewExport.dir, "transport-v3/$name-silhouette.png"), img, 2, null)
        }
    }

    // ───────────── Interior que não vaza ─────────────

    private class CabinLeak(val pixels: Int, val box: String)

    /** Transparente na frente do veículo e ligado à borda da tela = fora do veículo (4-vizinhança: contornos diagonais fecham). */
    private fun exteriorOf(front: PixelBuffer): BooleanArray {
        val w = front.width; val h = front.height
        val out = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        fun push(i: Int) { if (!out[i] && front.pixels[i] ushr 24 == 0) { out[i] = true; stack.addLast(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast(); val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return out
    }

    /** Silhueta livre: a frente do veículo não encosta em nenhuma borda (senão é interior de quadro cheio). */
    private fun hasFreeSilhouette(front: PixelBuffer): Boolean {
        val w = front.width; val h = front.height
        for (x in 0 until w) if (front.pixels[x] ushr 24 != 0 || front.pixels[(h - 1) * w + x] ushr 24 != 0) return false
        for (y in 0 until h) if (front.pixels[y * w] ushr 24 != 0 || front.pixels[y * w + w - 1] ushr 24 != 0) return false
        return true
    }

    /** Pixels opacos de `vehicle_back` que a frente não cobre e que ficam fora do veículo. */
    private fun interiorLeak(back: PixelBuffer, front: PixelBuffer): CabinLeak {
        val w = back.width
        val exterior = exteriorOf(front)
        var n = 0; var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
        back.pixels.forEachIndexed { i, c ->
            if (c ushr 24 != 0 && exterior[i]) { n++; x0 = min(x0, i % w); x1 = max(x1, i % w); y0 = min(y0, i / w); y1 = max(y1, i / w) }
        }
        return CabinLeak(n, if (n == 0) "-" else "x $x0..$x1, y $y0..$y1")
    }

    /** Aberturas fechadas da frente (janelas): regiões transparentes que não alcançam a borda. */
    private fun enclosedOpenings(front: PixelBuffer): Int {
        val w = front.width; val h = front.height
        val exterior = exteriorOf(front)
        val seen = BooleanArray(w * h)
        var count = 0
        for (start in 0 until w * h) {
            if (seen[start] || exterior[start] || front.pixels[start] ushr 24 != 0) continue
            count++
            val stack = ArrayDeque<Int>().apply { addLast(start) }
            seen[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast(); val x = i % w; val y = i / w
                fun visit(j: Int) { if (!seen[j] && !exterior[j] && front.pixels[j] ushr 24 == 0) { seen[j] = true; stack.addLast(j) } }
                if (x > 0) visit(i - 1)
                if (x < w - 1) visit(i + 1)
                if (y > 0) visit(i - w)
                if (y < h - 1) visit(i + w)
            }
        }
        return count
    }

    /** O interior só aparece pelas janelas: nada de `vehicle_back` pode surgir fora da carroceria. */
    @Test fun interiorNeverLeaksOutsideTheWindows() {
        var checked = 0
        scenes.keys.forEach { name ->
            val art = SceneArtStore.get(name).also { assertNotNull("arte $name ausente", it) }!!
            DayPeriod.entries.forEach { p ->
                val back = art.layer("vehicle_back", p) ?: return@forEach
                val front = art.layer("vehicle_front", p) ?: return@forEach
                // Interior de quadro cheio (trem, metrô, ônibus): não há "fora do veículo" para vazar.
                if (!hasFreeSilhouette(front)) return@forEach
                checked++
                val leak = interiorLeak(back, front)
                assertEquals("$name/$p: interior vaza para fora da carroceria (${leak.pixels} px em ${leak.box})", 0, leak.pixels)
            }
        }
        assertTrue("a trava não pode se desligar sozinha: nenhuma cena com silhueta livre foi verificada", checked >= DayPeriod.entries.size)
    }

    @Test fun theCarWindowsAreClosedOpenings() {
        val front = SceneArtStore.get("car")!!.layer("vehicle_front", DayPeriod.DAY)!!
        assertTrue("o carro precisa ter silhueta livre", hasFreeSilhouette(front))
        val openings = enclosedOpenings(front)
        assertTrue("janelas do carro: $openings abertura(s) fechada(s); esperadas ao menos 2 (frente e traseira)", openings >= 2)
    }

    /** Controle: a trava reprova um interior maior que a janela e aceita o mesmo interior recortado. */
    @Test fun theLeakGuardCatchesALeakAndAcceptsAClippedInterior() {
        // Escreve direto nos pixels: box() com cor transparente não apaga nada.
        fun raw(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0..y1) for (x in x0..x1) b.pixels[y * b.width + x] = c }
        val front = PixelBuffer(60, 40)
        raw(front, 10, 10, 49, 29, 0xFF884433.toInt())      // carroceria
        raw(front, 20, 15, 39, 24, 0)                         // janela (transparente, fechada)
        val leaking = PixelBuffer(60, 40).also { raw(it, 14, 6, 45, 26, 0xFF3B2C34.toInt()) } // passa do teto (y 6..9)
        val clipped = PixelBuffer(60, 40).also { raw(it, 20, 15, 39, 24, 0xFF3B2C34.toInt()) }
        assertTrue(hasFreeSilhouette(front))
        assertEquals(1, enclosedOpenings(front))
        assertEquals("o vazamento deveria ser de 32 x 4 px", 32 * 4, interiorLeak(leaking, front).pixels)
        assertEquals(0, interiorLeak(clipped, front).pixels)
    }

    // ───────────── Revisão humana ─────────────

    private val status by lazy { ArtReviewStatus.parse(File("../assets-source/scenes/transport/scene-art-status.json").readText()) }

    @Test fun everyV3SceneIsTrackedInTheReviewManifest() {
        val sources = File("../assets-source/scenes/transport").listFiles { f -> f.extension == "aseprite" }.orEmpty().map { it.nameWithoutExtension }.toSet()
        assertEquals(sources, status.keys)
    }

    /** As cenas em camadas estão no app: todas precisam de revisão humana registrada. */
    @Test fun everySceneInTheAppHasAHumanReview() {
        val pending = status.filterValues { !it.manualReview || !it.final }.keys
        assertTrue("Cenas sem revisão humana: $pending", pending.isEmpty())
    }

    private fun <T> withV3(block: () -> T): T = block()

    private companion object {
        const val FLAT_MAX = 600
        const val BLUE_MAX = 600
        /** Lado mínimo para contar como painel (abaixo disso é linha: friso, moldura, barra). */
        const val PANEL_MIN_SIDE = 6
        /** Abaixo disto é contorno/sombra profunda: igual em qualquer cena, não conta para matiz/contraste. */
        const val DARK_LUMA = 50.0
    }
}
