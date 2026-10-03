package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.pixel.diary.DiaryMapPerf
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.t
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMapRendererTest {
    private val data = JourneyTestFixtures.data
    private val layout = JourneyTestFixtures.layout
    private val idle = JourneyScene(data, layout, JourneyReplayAssembler.stateAt(data, null, false), timeMs = 1_000)

    private fun replayAt(minuteTs: Long, selected: String? = null) = JourneyScene(
        data, layout, JourneyReplayAssembler.stateAt(data, minuteTs, true),
        JourneyLightingRenderer.resolve(minuteTs, JourneyTestFixtures.ZONE), selected, timeMs = 1_000,
    )

    @Test
    fun `mesma cena desenha os mesmos pixels, sem buracos`() {
        val a = JourneyMapRenderer.render(idle); val b = JourneyMapRenderer.render(idle.copy())
        assertEquals(layout.width, a.width); assertEquals(layout.height, a.height)
        assertArrayEquals(a.pixels, b.pixels)
        assertTrue(a.pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `estados no replay - visitada, atual e ainda nao`() {
        val s = replayAt(t(12, 12)) // trecho 1 (trabalho → almoço)
        assertEquals(NodeState.VISITED, JourneyMapRenderer.nodeState(s.replay, 0))
        assertEquals(NodeState.VISITED, JourneyMapRenderer.nodeState(s.replay, 1))
        assertEquals(NodeState.UPCOMING, JourneyMapRenderer.nodeState(s.replay, 2))
        assertEquals(SegmentState.DONE, JourneyMapRenderer.segmentState(s.replay, 0))
        assertEquals(SegmentState.ACTIVE, JourneyMapRenderer.segmentState(s.replay, 1))
        assertEquals(SegmentState.UPCOMING, JourneyMapRenderer.segmentState(s.replay, 3))
        val atLunch = replayAt(t(12, 30))
        assertEquals(NodeState.ACTIVE, JourneyMapRenderer.nodeState(atLunch.replay, 2))
        // Sem replay, tudo visitado.
        layout.nodes.forEach { assertEquals(NodeState.VISITED, JourneyMapRenderer.nodeState(idle.replay, it.index)) }
    }

    @Test
    fun `parada ativa ganha anel dourado na plataforma`() {
        val img = JourneyMapRenderer.render(replayAt(t(12, 30)))
        val pad = layout.nodes[2].pad
        // O Hoodie fica em pé na plataforma e cobre parte do topo: conta o anel inteiro.
        val gold = (pad.y - 2..pad.bottom + 1).sumOf { y -> (pad.x - 2..pad.right + 1).count { x -> img[x, y] == JourneyPalette.GOLD } }
        assertTrue("anel dourado ($gold px)", gold > pad.w + pad.h)
    }

    @Test
    fun `parada selecionada ganha contorno azul`() {
        val img = JourneyMapRenderer.render(idle.copy(selectedNodeId = layout.nodes[1].nodeId))
        val (x, y, w, _) = layout.nodes[1].building.pixels.toList()
        assertTrue((x - 3..x + w + 2).count { img[it, y - 3] == JourneyPalette.SELECTED } > w)
        val plain = JourneyMapRenderer.render(idle)
        assertFalse((x - 3..x + w + 2).any { plain[it, y - 3] == JourneyPalette.SELECTED })
    }

    @Test
    fun `Hoodie andando no trecho ativo e parado onde o dia terminou`() {
        val walking = JourneyMapRenderer.marker(replayAt(t(12, 12)))!!
        assertTrue(walking.moving)
        val atEnd = JourneyMapRenderer.marker(idle)!!
        assertEquals(layout.nodes.last().stop, atEnd.position)
        // Antes da primeira chegada: esperando na primeira parada.
        val early = JourneyMapRenderer.marker(replayAt(t(6) - 1))!!
        assertEquals(layout.nodes.first().stop, early.position)
    }

    @Test
    fun `noite escurece o chao, acende postes e muda o ceu`() {
        val day = JourneyMapRenderer.render(replayAt(t(13)))
        val night = JourneyMapRenderer.render(replayAt(t(22, 30)))
        fun lum(c: Int) = (c shr 16 and 255) + (c shr 8 and 255) + (c and 255)
        val ground = (JourneyLayoutEngine.HEADER until layout.height step 7).flatMap { y -> (0 until layout.width step 7).map { x -> x to y } }
        assertTrue(ground.sumOf { (x, y) -> lum(night[x, y]) } < ground.sumOf { (x, y) -> lum(day[x, y]) } * 0.8)
        assertNotEquals(day[120, 2], night[120, 2])
        assertTrue("halo de poste aceso", night.pixels.any { it == JourneyPalette.LAMP_LIGHT })
    }

    @Test
    fun `mundo vivo - ambiente anima com o tempo, chao e ruas nao`() {
        val a = JourneyMapRenderer.render(idle.copy(timeMs = 0))
        val b = JourneyMapRenderer.render(idle.copy(timeMs = 4_000))
        assertFalse(a.pixels.contentEquals(b.pixels))
        // A rua do primeiro trecho é a mesma nos dois quadros (só muda o que é animado).
        val p = layout.segments[0].path.pointAt(0.1f)
        assertEquals(a[p.x.toInt() + 3, p.y.toInt()], b[p.x.toInt() + 3, p.y.toInt()])
    }

    @Test
    fun `camada estatica vem do cache`() {
        val cache = JourneyRenderCache.create(layout)
        val builds = DiaryMapPerf.staticBuilds
        repeat(5) { JourneyMapRenderer.render(idle.copy(timeMs = it * 400L), cache) }
        assertEquals(builds, DiaryMapPerf.staticBuilds)
        assertTrue(cache.matches(layout))
        assertFalse(cache.matches(JourneyLayoutEngine.layout(JourneyTestFixtures.shortData)))
    }

    @Test
    fun `dia vazio ainda desenha um canteiro tranquilo`() {
        val empty = JourneyLayoutEngine.layout(JourneyMapData.EMPTY)
        val img = JourneyMapRenderer.render(JourneyScene(JourneyMapData.EMPTY, empty, JourneyReplayAssembler.stateAt(JourneyMapData.EMPTY, null, false)))
        assertTrue(img.pixels.all { it ushr 24 == 0xFF })
        assertEquals(null, JourneyMapRenderer.marker(JourneyScene(JourneyMapData.EMPTY, empty, JourneyReplayAssembler.stateAt(JourneyMapData.EMPTY, null, false))))
    }

    @Test
    fun `quadro do dia longo cabe no orcamento do replay`() {
        val long = JourneyTestFixtures.longData
        val l = JourneyLayoutEngine.layout(long)
        val cache = JourneyRenderCache.create(l)
        val out = com.hoodie.app.pixel.renderer.PixelBuffer(l.width, l.height)
        val scene = JourneyScene(long, l, JourneyReplayAssembler.stateAt(long, long.nodes[5].arrivalAt + 1, true), JourneyLightingRenderer.at(20 * 60))
        repeat(5) { JourneyMapRenderer.render(scene.copy(timeMs = it * 100L), cache, out) } // aquecimento do JIT
        val start = System.nanoTime()
        repeat(20) { JourneyMapRenderer.render(scene.copy(timeMs = it * 100L), cache, out) }
        val avgMs = (System.nanoTime() - start) / 20 / 1_000_000.0
        // Replay a 10 FPS = 100 ms por quadro; folga larga para a JVM de CI.
        assertTrue("média de $avgMs ms por quadro", avgMs < 60)
    }
}
