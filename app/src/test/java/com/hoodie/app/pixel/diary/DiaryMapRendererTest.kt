package com.hoodie.app.pixel.diary

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryMapRendererTest {

    private val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)
    private val idle = DiaryMapScene(layout, timeMs = 1_000)

    @Test
    fun `mesma cena desenha os mesmos pixels`() {
        val a = DiaryMapRenderer.render(idle); val b = DiaryMapRenderer.render(idle.copy())
        assertEquals(DiaryMapTiles.WIDTH, a.width); assertEquals(DiaryMapTiles.HEIGHT, a.height)
        assertArrayEquals(a.pixels, b.pixels)
        // Tudo opaco: o mapa não tem buracos.
        assertTrue(a.pixels.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `predio desenhado onde o layout diz e com contorno dourado quando ativo`() {
        val home = layout.nodes.first()
        val (x, y, w, h) = home.footprint.pixels.toList()
        val replay = DiaryMapScene(layout, replaying = true, activeVisitIndex = 0, timeMs = 1_000)
        assertEquals(BuildingState.ACTIVE, DiaryMapRenderer.buildingState(replay, home))
        val img = DiaryMapRenderer.render(replay)
        val goldOnFrame = (x until x + w).count { img[it, y - 1] == DiaryMapPalette.GOLD }
        assertTrue("contorno dourado no topo do prédio ativo", goldOnFrame > w / 2)
        // O prédio está dentro do próprio terreno: há contorno escuro dentro do footprint.
        assertTrue((x until x + w).any { px -> (y until y + h).any { py -> img[px, py] == DiaryMapPalette.OUTLINE } })
    }

    @Test
    fun `estados do replay - visitado, atual e ainda nao visitado`() {
        val scene = DiaryMapScene(layout, replaying = true, activeTripIndex = 1, tripProgress = 0.5f)
        val home = layout.nodes.first { 0 in it.visitIndices }
        val work = layout.nodes.first { 1 in it.visitIndices }
        val gym = layout.nodes.first { 4 in it.visitIndices }
        assertEquals(BuildingState.VISITED, DiaryMapRenderer.buildingState(scene, home))
        assertEquals(BuildingState.VISITED, DiaryMapRenderer.buildingState(scene, work))
        assertEquals(BuildingState.UPCOMING, DiaryMapRenderer.buildingState(scene, gym))
        assertEquals(DiaryMapRenderer.TripStyle.DONE, DiaryMapRenderer.tripStyle(scene, 0))
        assertEquals(DiaryMapRenderer.TripStyle.ACTIVE, DiaryMapRenderer.tripStyle(scene, 1))
        assertEquals(DiaryMapRenderer.TripStyle.HIDDEN, DiaryMapRenderer.tripStyle(scene, 3))
        // Sem replay, o dia inteiro aparece visitado.
        layout.nodes.forEach { assertEquals(BuildingState.VISITED, DiaryMapRenderer.buildingState(idle, it)) }
    }

    @Test
    fun `marcador no replay e no fim do dia`() {
        val onTrip = DiaryMapRenderer.marker(DiaryMapScene(layout, replaying = true, activeTripIndex = 0, tripProgress = 0f))!!
        assertEquals(layout.nodeOfVisit(0)!!.entrance, onTrip.position)
        val atWork = DiaryMapRenderer.marker(DiaryMapScene(layout, replaying = true, activeVisitIndex = 3))!!
        assertEquals(layout.nodeOfVisit(3)!!.entrance, atWork.position)
        assertFalse(atWork.walking)
        // Sem replay: onde o dia terminou.
        assertEquals(layout.nodeOfVisit(5)!!.entrance, DiaryMapRenderer.marker(idle)!!.position)
        assertNull(DiaryMapRenderer.marker(DiaryMapScene(DiaryMapLayout.EMPTY)))
    }

    @Test
    fun `horario do replay muda a luz e a cidade anima`() {
        val day = DiaryMapRenderer.render(idle.copy(period = DayPeriod.DAY))
        val night = DiaryMapRenderer.render(idle.copy(period = DayPeriod.NIGHT))
        assertFalse(day.pixels.contentEquals(night.pixels))
        val t0 = DiaryMapRenderer.render(idle.copy(timeMs = 0)); val t1 = DiaryMapRenderer.render(idle.copy(timeMs = 2_500))
        assertFalse("fumaça/carro/água devem mexer", t0.pixels.contentEquals(t1.pixels))
    }

    @Test
    fun `mapa vazio renderiza so a cidade`() {
        val img = DiaryMapRenderer.render(DiaryMapScene(DiaryMapLayout.EMPTY))
        assertEquals(DiaryMapTiles.WIDTH * DiaryMapTiles.HEIGHT, img.pixels.size)
    }

    @Test
    fun `preview do mapa`() {
        val frames = listOf(
            idle,
            DiaryMapScene(layout, replaying = true, activeTripIndex = 1, tripProgress = 0.4f, period = DayPeriod.MORNING, timeMs = 900),
            DiaryMapScene(layout, replaying = true, activeVisitIndex = 4, period = DayPeriod.EVENING, timeMs = 1_800),
            DiaryMapScene(layout, replaying = true, activeTripIndex = 4, tripProgress = 0.7f, period = DayPeriod.NIGHT, timeMs = 2_700),
        )
        PreviewExport.sheet("diary_map", frames.map { DiaryMapRenderer.render(it) }, columns = 2, scale = 3)
        PreviewExport.save("diary_marker", PixelBuffer(16 * 8, 24).also { b ->
            listOf(MarkerDirection.FRONT, MarkerDirection.BACK, MarkerDirection.LEFT, MarkerDirection.RIGHT).forEachIndexed { i, d ->
                b.blit(DiaryHoodieMarker.sprite(d, true, 0), i * 32, 0); b.blit(DiaryHoodieMarker.sprite(d, true, 140), i * 32 + 16, 0)
            }
        }, scale = 6)
    }
}
