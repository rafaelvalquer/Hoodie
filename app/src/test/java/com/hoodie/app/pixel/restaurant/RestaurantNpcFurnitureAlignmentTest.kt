package com.hoodie.app.pixel.restaurant

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.restaurant.RestaurantNavigationGraph
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcSpot
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.RestaurantScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantNpcFurnitureAlignmentTest {
    @Test fun `grafo liga todos os lugares sem cruzar a mesa principal`() {
        for (from in RestaurantNpcSpot.entries) for (to in RestaurantNpcSpot.entries) {
            val route = RestaurantNavigationGraph.route(from, to)
            assertEquals(RestaurantNavigationGraph.spots.getValue(from).x, route.first().x)
            assertEquals(RestaurantNavigationGraph.spots.getValue(to).x, route.last().x)
            assertTrue("rota $from -> $to cruza a mesa principal: $route", route.none {
                it.x in 60..180 && it.floorY in 246..286
            })
        }
    }

    @Test fun `cliente ocupa o assento definido e cadeira e borda ficam nas camadas corretas`() {
        val seatId = RestaurantNpcDirector.guestSeat
        val seat = RestaurantNavigationGraph.spots.getValue(seatId)
        val slot = RestaurantNpcDirector.plan(SceneEnv(DayPeriod.DAY, 12 * 60)).single()
        assertEquals(RestaurantNpcSpot.TABLE_A_SEAT_RIGHT, seatId)
        assertEquals(seat.x, slot.x)
        assertEquals(seat.floorY, slot.floorY)
        assertEquals("table_a", seat.tableId)

        val baselines = RestaurantScene().sortedProps.map { it.baseline }
        assertTrue("cadeira traseira deve estar atrás do gato", baselines.any { it < slot.baseline && it >= 216 })
        assertTrue("borda frontal deve passar à frente do gato", baselines.any { it > slot.baseline && it <= 270 })
        assertTrue("mesa e cadeira devem pertencer à área do assento", seat.x in 194..217 && seat.floorY in 240..255)
    }

    @Test fun `cena completa continua renderizavel em dia e noite durante a refeicao`() {
        val scene = SceneRegistry[SceneId.RESTAURANT]
        val renderer = SceneRenderer()
        listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { period ->
            listOf(0L, 25_000L, 95_000L, 180_000L).forEach { time ->
                val image = renderer.renderEmpty(scene, SceneEnv(period, 12 * 60, daySeed = 99), time)
                assertEquals(240, image.width)
                assertEquals(320, image.height)
                assertTrue(image.pixels.any { it != 0 })
            }
        }
    }
}
