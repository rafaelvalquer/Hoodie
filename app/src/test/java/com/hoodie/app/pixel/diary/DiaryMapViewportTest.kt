package com.hoodie.app.pixel.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryMapViewportTest {

    @Test
    fun `escala inteira e mapa centralizado`() {
        val vp = DiaryMapViewport(1000f, 700f)
        assertEquals(4f, vp.scale) // min(1000/240, 700/160) = 4.16 → 4
        assertEquals((1000f - 960f) / 2f, vp.offsetX)
        assertEquals((700f - 640f) / 2f, vp.offsetY)
    }

    @Test
    fun `toScreen e toLogical sao inversos`() {
        val vp = DiaryMapViewport(1080f, 720f)
        listOf(MapPoint(0f, 0f), MapPoint(12.5f, 99f), MapPoint(239f, 159f)).forEach { p ->
            val back = vp.toLogical(vp.toScreen(p))
            assertEquals(p.x, back.x, 1e-3f); assertEquals(p.y, back.y, 1e-3f)
        }
    }

    @Test
    fun `toque fora do mapa nao vira coordenada`() {
        val vp = DiaryMapViewport(1000f, 700f)
        assertNull(vp.toLogicalOrNull(ScreenPoint(5f, 5f)))
        assertTrue(vp.toLogicalOrNull(ScreenPoint(500f, 350f)) != null)
    }

    @Test
    fun `tela menor que o mapa reduz sem quebrar`() {
        val vp = DiaryMapViewport(120f, 80f)
        assertEquals(0.5f, vp.scale)
        val p = vp.toLogical(vp.toScreen(MapPoint(100f, 50f)))
        assertEquals(100f, p.x, 1e-3f)
    }

    @Test
    fun `canvas e hitbox usam as mesmas coordenadas`() {
        val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)
        val vp = DiaryMapViewport(1080f, 720f)
        layout.nodes.forEach { node ->
            val (x, y, w, h) = node.footprint.pixels.toList()
            // Onde o Canvas desenha o centro do prédio…
            val screen = vp.toScreen(MapPoint(x + w / 2f, y + h / 2f))
            // …é onde o toque encontra o mesmo nó.
            assertSame(node, layout.nodeAt(vp.toLogicalOrNull(screen)!!))
            // E a porta (onde o marcador para) também é do nó.
            assertSame(node, layout.nodeAt(vp.toLogical(vp.toScreen(node.entrance))))
        }
    }
}
