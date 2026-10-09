package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.BicycleScene
import com.hoodie.app.pixel.scene.GenericRideScene
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneArt
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.TransitScene
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `SceneArt.city()` (transit, bicicleta, carona, lazer, janelas das casas) corre como cenário
 * contínuo: 1 px de deslocamento é só translação, o ciclo fecha sem emenda e, parada
 * (`offset = 0`), desenha exatamente o que a versão anterior desenhava.
 */
class CityContinuityTest {
    private val bg = 0xFF000000.toInt()
    private val seeds = listOf(1, 3, 7, 17, 23, 10, 86, 162, 40, 128)
    private val regions = listOf(Triple(0, 239, 150), Triple(12, 76, 118), Triple(88, 154, 118))

    private fun city(x0: Int, x1: Int, base: Int, p: DayPeriod, offset: Int, seed: Int) =
        PixelBuffer(240, 320).apply { fill(bg); SceneArt.city(this, x0, x1, base, p, offset, seed) }

    /** Cópia fiel da versão anterior (com emenda a cada 97 px e janelas pela posição na tela). */
    private fun legacy(x0: Int, x1: Int, baseY: Int, period: DayPeriod, offset: Int, seed: Int) = PixelBuffer(240, 320).apply {
        fill(bg)
        val s = SceneArt.sky(period)
        var x = x0 - ((offset % 97) + 97) % 97
        var i = seed
        while (x <= x1) {
            val w = 10 + (i * 7) % 12; val h = 12 + (i * 13) % 26
            val bx0 = maxOf(x, x0); val bx1 = minOf(x + w, x1)
            if (bx0 <= bx1) {
                box(bx0, baseY - h, bx1, baseY, s.city)
                s.cityWindow?.let { lit ->
                    for (wy in baseY - h + 3 until baseY - 1 step 4) for (wx in x + 2 until x + w - 1 step 3) {
                        if (wx in x0..x1 && (wx * 31 + wy * 17 + i) % 3 != 0) set(wx, wy, lit)
                    }
                }
            }
            x += w + 2; i++
        }
    }

    private fun assertShifted(prev: PixelBuffer, next: PixelBuffer, x0: Int, x1: Int, y0: Int, y1: Int, label: String) {
        for (y in y0..y1) for (x in x0 until x1) {
            if (next[x, y] != prev[x + 1, y]) throw AssertionError("$label: pixel ($x,$y) mudou além da translação")
        }
    }

    @Test fun aStillCityIsPixelIdenticalToThePreviousVersion() {
        DayPeriod.entries.forEach { p -> seeds.forEach { seed -> regions.forEach { (x0, x1, base) ->
            assertTrue("$p seed=$seed x0=$x0", city(x0, x1, base, p, 0, seed).pixels.contentEquals(legacy(x0, x1, base, p, 0, seed).pixels))
        } } }
    }

    @Test fun eachStepIsAPureOnePixelTranslation() {
        DayPeriod.entries.forEach { p -> seeds.forEach { seed ->
            val period = SceneArt.cityStrip(seed).period
            listOf(0, 1, 96, 97, period / 2, period - 1, period, 5 * period - 1).forEach { s ->
                regions.forEach { (x0, x1, base) ->
                    assertShifted(city(x0, x1, base, p, s, seed), city(x0, x1, base, p, s + 1, seed), x0, x1, base - 40, base, "$p seed=$seed s=$s x0=$x0")
                }
            }
        } }
    }

    @Test fun theCycleClosesWithoutASeam() {
        DayPeriod.entries.forEach { p -> seeds.forEach { seed ->
            val period = SceneArt.cityStrip(seed).period
            val start = city(0, 239, 150, p, 0, seed).pixels
            listOf(period, 3 * period, -period).forEach { s -> assertTrue("$p seed=$seed s=$s", start.contentEquals(city(0, 239, 150, p, s, seed).pixels)) }
        } }
    }

    @Test fun everyStripCoversTheWidestSceneBeforeRepeating() {
        seeds.forEach { assertTrue("seed=$it", SceneArt.cityStrip(it).period > 240 + 22) }
    }

    private fun prop0(scene: PixelScene, t: Long, p: DayPeriod) =
        PixelBuffer(240, 320).apply { fill(bg); scene.props().first { it.baseline == 0 }.draw(this, SceneEnv(p, 12 * 60), t) }

    @Test fun movingScenesScrollTheirCityWithoutFlicker() {
        listOf(DayPeriod.DAY, DayPeriod.EVENING, DayPeriod.NIGHT).forEach { p ->
            // Bicicleta: cidade em t/100 até a base 202 (as luzes fixas ficam em x 34 e 199).
            val bike = BicycleScene()
            for (t in listOf(9_900L, 19_300L, 97_000L)) {
                val a = prop0(bike, t, p); val b = prop0(bike, t + 100, p)
                for (y in 165..201) for (x in 0 until 239) {
                    if (kotlin.math.abs(x - 34) <= 4 || kotlin.math.abs(x - 199) <= 4) continue
                    if (b[x, y] != a[x + 1, y]) throw AssertionError("bicicleta $p t=$t ($x,$y)")
                }
            }
            // Carona genérica: cidade em t/120 até a base 205 (lampiões noturnos acima de 143).
            val ride = GenericRideScene()
            for (t in listOf(11_880L, 23_880L)) assertShifted(prop0(ride, t, p), prop0(ride, t + 120, p), 0, 239, 168, 205, "carona $p t=$t")
        }
    }

    /** Folha de revisão em build/pixel-preview/city/: bicicleta, carona e transit em 8 momentos. */
    @Test fun exportReviewSheets() {
        val dir = File(PreviewExport.dir, "city")
        listOf("bicycle" to BicycleScene(), "generic_ride" to GenericRideScene(), "transit" to TransitScene()).forEach { (name, scene) ->
            listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { p ->
                val env = SceneEnv(p, 12 * 60)
                val frames = (0 until 8).map { k ->
                    val t = 9_000L + k * 2_000L
                    PixelBuffer(240, 320).apply { scene.drawBackground(this, env); scene.props().sortedBy { it.baseline }.forEach { it.draw(this, env, t) } }
                }
                val sheet = PixelBuffer(244 * 4, 324 * 2).apply { fill(0xFF2B2E4A.toInt()); frames.forEachIndexed { i, f -> blit(f, (i % 4) * 244, (i / 4) * 324) } }
                PreviewExport.write(File(dir, "$name-${p.name.lowercase()}.png"), sheet, 1, null)
            }
        }
    }
}
