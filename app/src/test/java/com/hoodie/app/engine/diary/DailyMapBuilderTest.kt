package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.PlaceVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMapBuilderTest {
    @Test fun mapsVisitsInOrderAndConnectsEveryConsecutivePair() {
        val visits = listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.HOME).mapIndexed { i, type ->
            PlaceVisit(i.toLong(), type.name, type, i * 100L, i * 100L + 50, 50, 1)
        }
        val map = DailyMapBuilder.build(visits)
        assertEquals(listOf(1, 2, 3), map.nodes.map { it.visitIndex })
        assertEquals(listOf("visit-0" to "visit-1", "visit-1" to "visit-2"), map.edges.map { it.fromNodeId to it.toNodeId })
        assertTrue(map.nodes.all { it.x >= 0 && it.y >= 0 })
    }
}
