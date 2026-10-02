package com.hoodie.app.pixel.diary

import com.hoodie.app.core.time.DayPeriod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryMapRenderCacheTest {
    private val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)

    @Test
    fun `camada estatica e calculada uma vez e reaproveitada`() {
        val cache = DiaryMapRenderCache.create(layout)
        val builds = DiaryMapPerf.staticBuilds
        repeat(5) { t -> DiaryMapRenderer.render(DiaryMapScene(layout, timeMs = t * 400L), cache) }
        assertTrue("nenhum rebuild com o mesmo layout", DiaryMapPerf.staticBuilds == builds)
        assertTrue(DiaryMapPerf.lastCacheHit)
        assertTrue(cache.matches(DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)))
    }

    @Test
    fun `com cache o quadro e identico ao render completo`() {
        val cache = DiaryMapRenderCache.create(layout)
        for (period in DayPeriod.entries) for (t in listOf(0L, 950L, 2_700L)) {
            val scene = DiaryMapScene(layout, replaying = true, activeTripIndex = 1, tripProgress = 0.3f, period = period, timeMs = t)
            assertArrayEquals("$period/$t", DiaryMapRenderer.render(scene).pixels, DiaryMapRenderer.render(scene, cache).pixels)
        }
    }

    @Test
    fun `estatica nao muda com o tempo nem com o horario e nao tem predios`() {
        val a = DiaryMapRenderCache.create(layout).staticLayer
        val b = DiaryMapRenderCache.create(layout).staticLayer
        assertNotSame(a, b)
        assertArrayEquals(a.pixels, b.pixels)
        // Terreno dos prédios fica só grama na estática (o prédio é dinâmico, com estado).
        val home = layout.nodes.first()
        val (x, y, w, h) = home.footprint.pixels.toList()
        assertFalse((x until x + w).any { px -> (y until y + h).any { py -> a[px, py] == DiaryMapPalette.OUTLINE } })
        // Água e postes ficam na camada dinâmica.
        assertTrue(DiaryMapRenderCache.create(layout).animatedTiles.all { it.second in DiaryMapStaticLayer.ANIMATED })
    }

    @Test
    fun `layout diferente nao usa o cache errado`() {
        val other = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay.take(2))
        val cache = DiaryMapRenderCache.create(layout)
        assertFalse(cache.matches(other))
        assertArrayEquals(DiaryMapRenderer.render(DiaryMapScene(other)).pixels, DiaryMapRenderer.render(DiaryMapScene(other), cache).pixels)
    }
}
