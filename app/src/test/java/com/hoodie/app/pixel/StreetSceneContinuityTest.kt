package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.P
import com.hoodie.app.pixel.scene.SceneArt
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.StreetScene
import com.hoodie.app.pixel.scene.StreetSkyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A cidade da rua corre como um cenário contínuo: cada pixel andado é só uma translação de 1 px
 * (nenhuma janela troca de estado, nenhum prédio surge no meio), o ciclo fecha sem emenda e não
 * há buracos maiores que a folga entre prédios.
 */
class StreetSceneContinuityTest {
    private val bg = 0xFF000000.toInt()
    private val periods = DayPeriod.entries
    private val nearBase = 200
    private val farBase = 150

    private fun near(scroll: Long, period: DayPeriod) = PixelBuffer(240, 320).apply {
        fill(bg)
        StreetSkyline.drawNear(this, 0, 239, nearBase, scroll, PixelBuffer.mix(SceneArt.sky(period).city, P.OUTLINE, .25f), P.OUTLINE, period)
    }

    private fun far(scroll: Long, period: DayPeriod) = PixelBuffer(240, 320).apply {
        fill(bg)
        StreetSkyline.drawFar(this, 0, 239, farBase, scroll, period)
    }

    /** [next] é [prev] deslocado 1 px para a esquerda nas linhas [y0]..[y1]. */
    private fun assertShifted(prev: PixelBuffer, next: PixelBuffer, y0: Int, y1: Int, label: String) {
        for (y in y0..y1) for (x in 0..238) {
            if (next[x, y] != prev[x + 1, y]) throw AssertionError("$label: pixel ($x,$y) mudou além da translação")
        }
    }

    private fun samples(period: Int) = listOf(0L, 1L, 37L, period / 2L, period - 2L, period - 1L, period.toLong(), 3L * period - 1)

    @Test fun eachStepIsAPureOnePixelTranslation() {
        periods.forEach { p ->
            samples(StreetSkyline.NEAR.period).forEach { s -> assertShifted(near(s, p), near(s + 1, p), 80, nearBase, "near $p s=$s") }
            samples(StreetSkyline.FAR.period).forEach { s -> assertShifted(far(s, p), far(s + 1, p), 100, farBase, "far $p s=$s") }
        }
    }

    @Test fun theCycleClosesWithoutASeam() {
        periods.forEach { p ->
            listOf(StreetSkyline.NEAR.period.toLong() to ::near, StreetSkyline.FAR.period.toLong() to ::far).forEach { (period, draw) ->
                val start = draw(0, p).pixels
                for (k in 1..3) assertTrue("$p: ciclo $k não fecha", start.contentEquals(draw(k * period, p).pixels))
                assertTrue("$p: scroll negativo", start.contentEquals(draw(-period, p).pixels))
            }
        }
    }

    @Test fun nearBuildingsNeverLeaveAHoleWiderThanTheirGap() {
        val strip = StreetSkyline.NEAR
        for (s in 0 until strip.period) {
            val img = near(s.toLong(), DayPeriod.DAY)
            var run = 0; var worst = 0
            for (x in 0..239) { if (img[x, nearBase] == bg) run++ else run = 0; worst = maxOf(worst, run) }
            assertTrue("scroll=$s: buraco de $worst px", worst <= strip.gap - 1)
        }
        assertTrue("o trecho próximo não pode repetir na mesma tela", strip.period > 240 + 56)
    }

    @Test fun windowsLightOnlyAtEveningAndNightAndKeepTheirState() {
        val lit = 0xFFFFD86B.toInt()
        listOf(DayPeriod.MORNING, DayPeriod.DAY).forEach { p ->
            for (s in 0L until 80L step 7) assertFalse("$p acendeu janela", near(s, p).pixels.contains(lit))
        }
        listOf(DayPeriod.EVENING, DayPeriod.NIGHT).forEach { p -> assertTrue("$p sem janela acesa", near(0, p).pixels.contains(lit)) }
        // Mesma regra, chamada só com identidade (sem posição): estável por construção.
        StreetSkyline.NEAR.buildings.forEach { b ->
            val a = (0..8).flatMap { r -> (0..3).map { c -> StreetSkyline.isWindowLit(b.id, r, c, DayPeriod.NIGHT) } }
            assertEquals(a, (0..8).flatMap { r -> (0..3).map { c -> StreetSkyline.isWindowLit(b.id, r, c, DayPeriod.NIGHT) } })
        }
    }

    private val sceneCity = StreetScene().props().first { it.baseline == 0 }

    private fun scene(t: Long, p: DayPeriod) = PixelBuffer(240, 320).apply { fill(bg); sceneCity.draw(this, SceneEnv(p, 22 * 60), t) }

    @Test fun theRealStreetSceneScrollsWithoutFlicker() {
        // 6060 → 6120 ms: prédios próximos andam 1 px e o fundo (t/140) fica parado.
        periods.forEach { p -> assertShifted(scene(6_060, p), scene(6_120, p), farBase + 1, nearBase, "cena $p") }
    }

    @Test fun fiveMinutesOfStreetRenderWithoutErrors() {
        val scene = StreetScene()
        val b = PixelBuffer(240, 320)
        val env = SceneEnv(DayPeriod.NIGHT, 22 * 60)
        var t = 0L
        repeat(9_000) { b.fill(bg); scene.props().forEach { it.draw(b, env, t) }; t += 33 }
        assertEquals(9_000 * 33L, t)
    }

    /** Tira para revisão em build/pixel-preview/street/: 12 quadros em volta da emenda de cada trecho. */
    @Test fun exportSeamStrips() {
        val dir = File(PreviewExport.dir, "street")
        listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { p ->
            val seamNear = StreetSkyline.NEAR.period * 60L
            val street = StreetScene()
            val env = SceneEnv(p, 22 * 60)
            val frames = (-6..5).map { k ->
                val t = seamNear + k * 60L * 20
                PixelBuffer(240, 320).apply { street.drawBackground(this, env); street.props().sortedBy { it.baseline }.forEach { it.draw(this, env, t) } }
            }
            val sheet = PixelBuffer(240 * 4 + 12, 220 * 3 + 8).apply {
                fill(0xFF2B2E4A.toInt())
                frames.forEachIndexed { i, f ->
                    val crop = PixelBuffer(240, 220).apply { for (y in 0 until 220) for (x in 0 until 240) set(x, y, f[x, y]) }
                    blit(crop, (i % 4) * 244, (i / 4) * 224)
                }
            }
            PreviewExport.write(File(dir, "seam-${p.name.lowercase()}.png"), sheet, 1, null)
        }
    }
}
