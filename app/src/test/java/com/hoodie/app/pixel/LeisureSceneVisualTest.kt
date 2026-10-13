package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.LeisureTestFixtures.MOUSE
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Quadros do Passeio 2.0 nas três variantes e períodos, mais as situações-chave (encontro,
 * banco ocupado e liberado, noite). Exporta folhas em build/pixel-preview/leisure-v2 para revisão
 * humana; os asserts aqui são estruturais (renderiza, anima, não sobrepõe no banco).
 */
class LeisureSceneVisualTest {
    private val scene = SceneRegistry[SceneId.LEISURE]

    private fun copy(b: PixelBuffer) = PixelBuffer(b.width, b.height).also { it.copyFrom(b) }

    private fun empty(variant: Int, period: DayPeriod, t: Long, daySeed: Int = 80_000): PixelBuffer =
        copy(SceneRenderer().renderEmpty(scene, LeisureTestFixtures.env(variant, daySeed, period), t))

    @Test fun exportVariantsAndPeriods() {
        val shots = (0 until 3).flatMap { v ->
            listOf(DayPeriod.MORNING, DayPeriod.DAY, DayPeriod.EVENING, DayPeriod.NIGHT).map { p -> empty(v, p, 45_000) }
        }
        PreviewExport.sheet("leisure-v2/variants_periods", shots, columns = 4, scale = 2)
        shots.forEach { s -> assertTrue(s.pixels.any { it != 0 }) }
    }

    @Test fun theSceneIsAliveOverTime() {
        (0 until 3).forEach { v ->
            val frames = (0L..60_000L step 15_000L).map { empty(v, DayPeriod.DAY, it) }
            PreviewExport.sheet("leisure-v2/variant_${v}_timeline", frames, columns = 5, scale = 2)
            frames.zipWithNext().forEach { (a, b) -> assertFalse("variante $v parada no tempo", a.pixels.contentEquals(b.pixels)) }
        }
    }

    @Test fun exportAnEncounter() {
        val seeds = (0 until 20).map { 81_000 + it }
        val (seed, encounter) = seeds.firstNotNullOf { s ->
            LeisureTestFixtures.session(0, daySeed = s).let { session -> session.snapshotAt(600_000); session.encounters().firstOrNull()?.let { s to it } }
        }
        val renderer = SceneRenderer()
        val frames = listOf(encounter.meetAt - 3_000, encounter.meetAt + 600, encounter.meetAt + 2_400, encounter.meetAt + 4_200, encounter.endAt - 400, encounter.endAt + 2_000)
            .map { t -> copy(renderer.renderEmpty(scene, LeisureTestFixtures.env(0, seed, DayPeriod.DAY), t)) }
        PreviewExport.sheet("leisure-v2/encounter", frames, columns = 3, scale = 2)
        assertEquals(6, frames.size)
    }

    /** Quadro completo com o Hoodie, como no app. */
    private fun withHoodie(activity: HoodieActivity, untilMs: Long, period: DayPeriod = DayPeriod.DAY, variant: Int = 0, daySeed: Int = 82_000, sample: (Long, LeisureNpcSession?) -> Unit = { _, _ -> }): PixelBuffer {
        val sm = AnimationStateMachine(Random(7))
        // Pesos neutros: o roteiro (Hoodie caminhando com a semente 7) não depende dos traços de personalidade.
        sm.setVisual(VisualDirector.resolve(activity, UserContextType.LEISURE, variant = variant, personality = null).copy(daySeed = daySeed), 1)
        val renderer = SceneRenderer()
        var t = 1L
        var out: PixelBuffer? = null
        while (t <= untilMs) {
            val frame = sm.frame(t, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, period)!!
            out = renderer.render(frame, t)
            sample(t, sessionOf(renderer))
            t += 33
        }
        return copy(out!!)
    }

    private fun sessionOf(renderer: SceneRenderer): LeisureNpcSession? =
        SceneRenderer::class.java.getDeclaredField("leisureSession").let { f -> f.isAccessible = true; f.get(renderer) as LeisureNpcSession? }

    /** Hoodie descansando no banco: o rato nunca está sentado no mesmo assento. */
    @Test fun benchOccupiedByTheHoodie() {
        var checked = 0
        val frame = withHoodie(HoodieActivity.RESTING, 20_000) { t, session ->
            val mouse = session?.snapshotAt(t)?.npcs?.singleOrNull { it.npcId == MOUSE } ?: return@withHoodie
            assertFalse("t=$t: rato no banco com o Hoodie", LeisureTestFixtures.onBenchSeat(mouse) || mouse.seated)
            checked++
        }
        PreviewExport.save("leisure-v2/bench_occupied_by_hoodie", frame, scale = 3)
        assertTrue(checked > 100)
    }

    /** Hoodie passeando: o banco fica para o rato, que lê nele. */
    @Test fun benchFreeForTheVisitorWhileTheHoodieWalks() {
        var mouseSeated = false
        val frame = withHoodie(HoodieActivity.SIGHTSEEING, 6_000) { t, session ->
            val mouse = session?.snapshotAt(t)?.npcs?.singleOrNull { it.npcId == MOUSE } ?: return@withHoodie
            if (mouse.seated) mouseSeated = true
        }
        PreviewExport.save("leisure-v2/bench_with_visitor", frame, scale = 3)
        assertTrue("o rato começa sentado quando o banco está livre", mouseSeated)
    }

    @Test fun nightIsQuieterWithTwoVisitors() {
        val frames = (0 until 3).map { v -> withHoodie(HoodieActivity.SIGHTSEEING, 8_000, DayPeriod.NIGHT, v) }
        PreviewExport.sheet("leisure-v2/night", frames, columns = 3, scale = 2)
        val session = LeisureTestFixtures.session(period = DayPeriod.NIGHT)
        assertNotNull(session)
        assertEquals(2, session.npcSlots.size)
    }

    /** Linha de quadros do Hoodie indo ao banco ocupado pelo rato: mostra a troca acontecer. */
    @Test fun exportBenchHandOver() {
        val sm = AnimationStateMachine(Random(3))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, variant = 0).copy(daySeed = 83_000), 1)
        val renderer = SceneRenderer()
        var t = 1L
        while (t < 3_000) { renderer.render(sm.frame(t, 600, DayPeriod.DAY)!!, t); t += 33 }
        // O Hoodie passa a descansar no banco: a intenção chega pelo destino da microação.
        sm.setVisual(VisualDirector.resolve(HoodieActivity.RESTING, UserContextType.LEISURE, variant = 0).copy(daySeed = 83_000), t)
        val frames = ArrayList<PixelBuffer>()
        val handOverEnd = t + 9_000
        var overlap = 0
        while (t < handOverEnd) {
            val frame = sm.frame(t, 600, DayPeriod.DAY)!!
            val image = renderer.render(frame, t)
            val mouse = sessionOf(renderer)?.snapshotAt(t)?.npcs?.singleOrNull { it.npcId == MOUSE }
            val hoodieOnBench = frame.occupancy.seated && frame.occupancy.targetSpot == SpotId.BENCH
            if (hoodieOnBench && mouse != null && LeisureTestFixtures.onBenchSeat(mouse)) overlap++
            if ((t / 33) % 30 == 0L) frames += copy(image)
            t += 33
        }
        PreviewExport.sheet("leisure-v2/bench_hand_over", frames.take(12), columns = 4, scale = 2)
        assertEquals("Hoodie e rato no mesmo assento", 0, overlap)
    }
}
