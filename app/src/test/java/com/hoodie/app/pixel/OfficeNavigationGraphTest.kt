package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.office.*
import org.junit.Assert.*
import org.junit.Test

class OfficeNavigationGraphTest {
    @Test fun routesConnectEveryPairInsideSceneBounds() {
        OfficeNpcSpot.entries.forEach { from -> OfficeNpcSpot.entries.forEach { to ->
            val path = OfficeNavigationGraph.route(from, to)
            assertEquals(OfficeNavigationGraph.spots.getValue(from), path.first())
            assertEquals(OfficeNavigationGraph.spots.getValue(to), path.last())
            assertTrue(path.all { it.x in 0..239 && it.floorY in 0..319 })
        } }
    }
}
