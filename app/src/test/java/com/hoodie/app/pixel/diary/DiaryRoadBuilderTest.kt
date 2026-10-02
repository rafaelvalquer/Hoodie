package com.hoodie.app.pixel.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DiaryRoadBuilderTest {

    private val allDoors = DiaryMapTiles.LOTS.map(DiaryMapTiles::door)

    @Test
    fun `caminho liga porta a porta em passos ortogonais`() {
        for (a in allDoors) for (b in allDoors) {
            val path = DiaryRoadBuilder.route(a, b)
            assertEquals(a, path.first()); assertEquals(b, path.last())
            path.zipWithNext().forEach { (p, q) -> assertEquals("passo único em $p → $q", 1, abs(p.col - q.col) + abs(p.row - q.row)) }
        }
    }

    @Test
    fun `ruas nunca atravessam predios`() {
        for (a in allDoors) for (b in allDoors) {
            DiaryRoadBuilder.route(a, b).forEach { t ->
                assertTrue("$t dentro de um terreno", DiaryMapTiles.LOTS.none { t in it })
                assertTrue("$t fora da rua/calçada", DiaryMapTiles.walkable(t.col, t.row))
            }
        }
    }

    @Test
    fun `desvio pequeno - no maximo 2 tiles alem da distancia manhattan pela grade`() {
        for (a in allDoors) for (b in allDoors) {
            val path = DiaryRoadBuilder.route(a, b)
            val manhattan = abs(a.col - b.col) + abs(a.row - b.row)
            val sameRow = a.row == b.row
            // Sair para a rua e voltar custa 2; mudar de fileira pode exigir ir até a rua vertical.
            val extra = path.size - 1 - manhattan
            if (sameRow) assertTrue("$a→$b extra=$extra", extra <= 2 || a == b)
            else assertTrue("$a→$b extra=$extra", extra <= 2 + 2 * 3)
        }
    }

    @Test
    fun `deterministico e ponto ao longo do caminho`() {
        val a = allDoors[10]; val b = allDoors[4]
        assertEquals(DiaryRoadBuilder.route(a, b), DiaryRoadBuilder.route(a, b))
        val path = DiaryRoadBuilder.route(a, b)
        assertEquals(a.center, DiaryRoadBuilder.pointAt(path, 0f))
        assertEquals(b.center, DiaryRoadBuilder.pointAt(path, 1f))
        // Primeiro passo é descer da calçada para a rua.
        assertEquals(0 to 1, DiaryRoadBuilder.stepAt(path, 0f))
    }
}
