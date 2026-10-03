package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.pixel.diary.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class JourneyPathBuilderTest {
    private val from = MapPoint(48f, 62f)
    private val to = MapPoint(192f, 158f)
    private val path = JourneyPathBuilder.build("s", from, to, crossY = 96)

    @Test
    fun `desce, faz curva, atravessa e desce ate a proxima plataforma`() {
        assertEquals(from, path.points.first())
        assertEquals(to, path.points.last())
        // Só desce ou anda reto: nunca volta para cima.
        assertTrue(path.points.zipWithNext().all { (a, b) -> b.y >= a.y })
        // Trechos retos ou esquina chanfrada a 45°.
        path.points.zipWithNext().forEach { (a, b) ->
            val dx = abs(b.x - a.x); val dy = abs(b.y - a.y)
            assertTrue("trecho $a→$b", dx == 0f || dy == 0f || dx == dy)
        }
        // A travessia acontece na altura combinada.
        assertTrue(path.points.any { it.y == 96f && it.x > from.x && it.x < to.x })
    }

    @Test
    fun `ponto por progresso anda com velocidade constante`() {
        assertEquals(from, path.pointAt(0f))
        assertEquals(to, path.pointAt(1f))
        // Passos iguais de progresso → distâncias iguais (sem "teleporte" nas esquinas).
        val steps = (0..100).map { path.pointAt(it / 100f) }
        val dists = steps.zipWithNext().map { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
        val expected = path.length / 100f
        dists.forEach { assertEquals(expected, it, 0.75f) }
    }

    @Test
    fun `direcao do trecho - desce no comeco, atravessa no meio`() {
        val (dx0, dy0) = path.directionAt(0.01f)
        assertEquals(0f, dx0, 0.001f); assertTrue(dy0 > 0)
        val crossing = path.points.indexOfFirst { it.y == 96f }
        val mid = (crossing + 0.5f) / (path.points.size - 1)
        val (dx, dy) = path.directionAt(0.5f)
        assertTrue("meio do caminho anda na horizontal (mid ~$mid)", dx > 0 && dy == 0f)
        assertEquals(1f, path.horizontalSign)
        assertEquals(-1f, JourneyPathBuilder.build("r", to, MapPoint(48f, 254f), 192).horizontalSign)
    }

    @Test
    fun `mesma coluna vira linha reta`() {
        val straight = JourneyPathBuilder.build("x", MapPoint(10f, 10f), MapPoint(12f, 50f), 30)
        assertEquals(2, straight.points.size)
    }
}
