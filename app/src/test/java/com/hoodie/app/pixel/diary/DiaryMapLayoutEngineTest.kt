package com.hoodie.app.pixel.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.DiaryMapTestFixtures.typicalDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryMapLayoutEngineTest {

    private val layout = DiaryMapLayoutEngine.layout(typicalDay)
    private fun nodeOf(type: PlaceType) = layout.nodes.first { it.type == type }

    @Test
    fun `mesma entrada gera o mesmo layout`() {
        assertEquals(layout, DiaryMapLayoutEngine.layout(typicalDay.map { it.copy() }))
    }

    @Test
    fun `visitas repetidas reutilizam o mesmo no`() {
        assertEquals(4, layout.nodes.size) // casa, trabalho, café, academia
        assertEquals(6, layout.visits.size)
        assertEquals(listOf(0, 5), nodeOf(PlaceType.HOME).visitIndices)
        assertEquals(listOf(1, 3), nodeOf(PlaceType.WORK).visitIndices)
        assertEquals(layout.visits[1].nodeId, layout.visits[3].nodeId)
        // Sem id cadastrado, o mesmo nome e tipo também são o mesmo lugar.
        val noIds = DiaryMapLayoutEngine.layout(typicalDay.map { it.copy(placeId = null) })
        assertEquals(4, noIds.nodes.size)
    }

    @Test
    fun `ids seguem o replay`() {
        assertEquals((0..5).map { "visit-$it" }, layout.visits.map { it.id })
        assertEquals((0..4).map { "edge-$it" }, layout.trips.map { it.id })
    }

    @Test
    fun `posicoes preferidas - casa embaixo a esquerda, trabalho em cima a direita, restaurante no centro, academia embaixo a direita`() {
        val midX = DiaryMapTiles.COLS / 2; val midY = DiaryMapTiles.ROWS / 2
        val home = nodeOf(PlaceType.HOME).footprint; val work = nodeOf(PlaceType.WORK).footprint
        val food = nodeOf(PlaceType.RESTAURANT).footprint; val gym = nodeOf(PlaceType.GYM).footprint
        assertTrue(home.col < midX && home.row > midY)
        assertTrue(work.col > midX && work.row < midY)
        assertTrue(food.col <= midX && food.lastCol >= midX - 1 && food.row in 5..12)
        assertTrue(gym.col > midX && gym.row > midY)
    }

    @Test
    fun `predios nao se sobrepoem nem encostam`() {
        for (l in listOf(layout, DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.crowdedDay))) {
            l.nodes.forEachIndexed { i, a ->
                l.nodes.drop(i + 1).forEach { b -> assertFalse("${a.id} x ${b.id}", a.footprint.grow(1).intersects(b.footprint)) }
                assertTrue(a.footprint.col >= 0 && a.footprint.lastCol < DiaryMapTiles.COLS && a.footprint.lastRow < DiaryMapTiles.ROWS)
            }
        }
    }

    @Test
    fun `dois lugares do mesmo tipo ganham predios diferentes`() {
        val l = DiaryMapLayoutEngine.layout(listOf(
            DiaryMapTestFixtures.visit(1, "Casa", PlaceType.HOME, 6, 8),
            DiaryMapTestFixtures.visit(9, "Casa da praia", PlaceType.HOME, 10, 12),
        ))
        assertEquals(2, l.nodes.size)
        assertFalse(l.nodes[0].footprint == l.nodes[1].footprint)
    }

    @Test
    fun `mais lugares que terrenos dividem o predio Outros lugares`() {
        val l = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.crowdedDay)
        assertEquals(DiaryMapTiles.LOTS.size, l.nodes.size)
        val overflow = l.node(DiaryMapLayoutEngine.OVERFLOW_ID)!!
        assertEquals(20 - (DiaryMapTiles.LOTS.size - 1), overflow.visitIndices.size)
        assertEquals(20, l.nodes.sumOf { it.visitIndices.size })
    }

    @Test
    fun `dia sem visitas gera mapa vazio`() {
        assertTrue(DiaryMapLayoutEngine.layout(emptyList()).isEmpty)
    }
}
