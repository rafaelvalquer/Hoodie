package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.pixel.diary.DiaryMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class DiaryMapNodeLabelTest {
    private val layout = DiaryMapLayoutEngine.layout(DiaryMapTestFixtures.typicalDay)
    private fun id(type: PlaceType) = layout.nodes.first { it.type == type }.id

    @Test
    fun `casa e trabalho sempre, o resto so tocado ou ativo`() {
        assertEquals(setOf(id(PlaceType.HOME), id(PlaceType.WORK)), DiaryMapLabelPolicy.visibleNodeIds(layout, null, null))
        assertEquals(
            setOf(id(PlaceType.HOME), id(PlaceType.WORK), id(PlaceType.GYM)),
            DiaryMapLabelPolicy.visibleNodeIds(layout, activeNodeId = id(PlaceType.GYM), selectedNodeId = null),
        )
        assertEquals(
            setOf(id(PlaceType.HOME), id(PlaceType.WORK), id(PlaceType.RESTAURANT)),
            DiaryMapLabelPolicy.visibleNodeIds(layout, activeNodeId = null, selectedNodeId = id(PlaceType.RESTAURANT)),
        )
    }

    @Test
    fun `etiqueta encosta no topo do predio e visita ativa vira o no`() {
        val gym = layout.nodes.first { it.type == PlaceType.GYM }
        val a = DiaryMapLabelPolicy.anchor(gym)
        val (x, y, w, _) = gym.footprint.pixels.toList()
        assertEquals(x + w / 2f, a.x); assertEquals(y.toFloat(), a.y)
        // Visita 4 (academia) → nó da academia; visita repetida (3, trabalho) → mesmo nó da visita 1.
        assertEquals(gym.id, DiaryMapLabelPolicy.activeNodeId(layout, "visit-4"))
        assertEquals(DiaryMapLabelPolicy.activeNodeId(layout, "visit-1"), DiaryMapLabelPolicy.activeNodeId(layout, "visit-3"))
        assertEquals(null, DiaryMapLabelPolicy.activeNodeId(layout, null))
    }
}
