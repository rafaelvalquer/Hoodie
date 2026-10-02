package com.hoodie.app.pixel.diary

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryHoodieMarkerTest {

    private val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)

    @Test
    fun `regra de direcao pelo eixo dominante`() {
        assertEquals(MarkerDirection.RIGHT, DiaryHoodieMarker.direction(3f, 1f))
        assertEquals(MarkerDirection.LEFT, DiaryHoodieMarker.direction(-3f, 2f))
        assertEquals(MarkerDirection.FRONT, DiaryHoodieMarker.direction(1f, 3f))
        assertEquals(MarkerDirection.BACK, DiaryHoodieMarker.direction(-1f, -3f))
        // Empate vai para a vertical.
        assertEquals(MarkerDirection.FRONT, DiaryHoodieMarker.direction(2f, 2f))
    }

    @Test
    fun `comeca e termina no no certo`() {
        layout.trips.forEach { trip ->
            val from = layout.node(trip.fromNodeId)!!; val to = layout.node(trip.toNodeId)!!
            val start = DiaryHoodieMarker.onTrip(trip, 0f); val end = DiaryHoodieMarker.onTrip(trip, 1f)
            assertEquals(from.entrance, start.position)
            assertEquals(to.entrance, end.position)
            assertFalse("parado na chegada", end.walking)
            assertTrue("andando no meio", DiaryHoodieMarker.onTrip(trip, 0.5f).walking)
        }
    }

    @Test
    fun `direita usa o espelho da esquerda`() {
        for (t in listOf(0L, 140L, 280L, 420L)) {
            val left = DiaryHoodieMarker.sprite(MarkerDirection.LEFT, walking = true, timeMs = t)
            val right = DiaryHoodieMarker.sprite(MarkerDirection.RIGHT, walking = true, timeMs = t)
            assertArrayEquals(DiaryHoodieMarker.mirror(left).pixels, right.pixels)
        }
    }

    @Test
    fun `sprites 16x24 com pes no chao e passos diferentes`() {
        for (d in MarkerDirection.entries) {
            val frames = (0 until DiaryHoodieMarker.WALK_FRAMES).map { DiaryHoodieMarker.sprite(d, true, it * 140L) }
            frames.forEach { f ->
                assertEquals(16, f.width); assertEquals(24, f.height)
                assertTrue("$d sem pé na última linha", (0 until 16).any { x -> f[x, DiaryHoodieMarker.FEET_Y] ushr 24 != 0 })
            }
            assertTrue("$d sem animação de passo", frames.map { it.pixels.toList() }.distinct().size > 1)
        }
        val idle = DiaryHoodieMarker.sprite(MarkerDirection.FRONT, false, 0)
        val walkFront = DiaryHoodieMarker.sprite(MarkerDirection.FRONT, true, 140)
        assertFalse(idle.pixels.contentEquals(walkFront.pixels))
    }

    @Test
    fun `direcao acompanha o passo do caminho`() {
        val trip = layout.trips.first()
        val n = trip.path.size - 1
        for (i in 0 until n) {
            val progress = (i + 0.5f) / n
            val (dx, dy) = trip.path[i + 1].let { it.col - trip.path[i].col to it.row - trip.path[i].row }
            assertEquals(DiaryHoodieMarker.direction(dx.toFloat(), dy.toFloat()), DiaryHoodieMarker.onTrip(trip, progress).direction)
        }
    }
}
