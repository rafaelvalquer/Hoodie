package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.pixel.diary.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyLayoutEngineTest {
    private val layout = JourneyTestFixtures.layout

    @Test
    fun `zigue-zague - par a esquerda, impar a direita, descendo`() {
        assertEquals(listOf(JourneySide.LEFT, JourneySide.RIGHT, JourneySide.LEFT, JourneySide.RIGHT, JourneySide.LEFT, JourneySide.RIGHT), layout.nodes.map { it.side })
        assertTrue(layout.nodes.zipWithNext().all { (a, b) -> b.rowTop - a.rowTop == JourneyLayoutEngine.ROW })
        assertTrue(layout.nodes.filter { it.side == JourneySide.LEFT }.all { it.stop.x < JourneyLayoutEngine.WIDTH / 2 })
        assertTrue(layout.nodes.filter { it.side == JourneySide.RIGHT }.all { it.stop.x > JourneyLayoutEngine.WIDTH / 2 })
    }

    @Test
    fun `lugar repetido ganha um no proprio no layout`() {
        assertEquals(6, layout.nodes.size)
        assertEquals(layout.nodes.size, layout.nodes.map { it.nodeId }.toSet().size)
        assertEquals(JourneyTestFixtures.data.nodes.map { it.placeType }, layout.nodes.map { it.type })
    }

    @Test
    fun `altura acompanha o dia e tudo cabe no canvas`() {
        assertEquals(JourneyLayoutEngine.heightFor(6), layout.height)
        layout.nodes.forEach { n ->
            val (x, y, w, h) = n.building.pixels.toList()
            assertTrue(x >= 0 && x + w <= layout.width && y >= JourneyLayoutEngine.HEADER && y + h <= layout.height)
            assertTrue(n.card.x >= 0 && n.card.right <= layout.width)
            // Prédios alinhados à grade de 8 px (reaproveitam os prédios do mapa clássico).
            assertEquals(0, x % 8); assertEquals(0, y % 8)
        }
        val long = JourneyLayoutEngine.layout(JourneyTestFixtures.longData)
        assertEquals(14, long.nodes.size)
        assertTrue(long.nodes.last().card.bottom < long.height - JourneyLayoutEngine.FOOTER)
    }

    @Test
    fun `ruas nunca passam por cima de cartoes, predios ou plataformas de outras paradas`() {
        layout.segments.forEach { seg ->
            var d = 0f
            while (d <= seg.path.length) {
                val p = seg.path.pointAt(d / seg.path.length)
                layout.nodes.forEach { n ->
                    assertFalse("rua ${seg.index} no cartão ${n.index} em $p", p in n.card)
                    val (bx, by, bw, bh) = n.building.pixels.toList()
                    assertFalse("rua ${seg.index} no prédio ${n.index}", p in JourneyRect(bx, by, bw, bh))
                }
                d += 1f
            }
        }
    }

    @Test
    fun `cada trecho liga a plataforma de origem a de destino`() {
        layout.segments.forEach { seg ->
            assertEquals(layout.nodes[seg.index].stop, seg.path.points.first())
            assertEquals(layout.nodes[seg.index + 1].stop, seg.path.points.last())
        }
    }

    @Test
    fun `toque encontra a parada pelo predio, plataforma ou cartao`() {
        val n = layout.nodes[3]
        assertEquals(n, layout.nodeAt(n.stop))
        assertEquals(n, layout.nodeAt(n.card.center))
        assertNull(layout.nodeAt(MapPoint(120f, 2f)))
    }

    @Test
    fun `deterministico e vazio`() {
        assertEquals(layout, JourneyLayoutEngine.layout(JourneyTestFixtures.data))
        val empty = JourneyLayoutEngine.layout(JourneyMapData.EMPTY)
        assertTrue(empty.isEmpty)
        assertEquals(JourneyLayoutEngine.emptyHeight, empty.height)
    }
}
