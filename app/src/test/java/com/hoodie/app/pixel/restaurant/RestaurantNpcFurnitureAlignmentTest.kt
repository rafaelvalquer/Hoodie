package com.hoodie.app.pixel.restaurant

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
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
        assertEquals(com.hoodie.app.pixel.sprite.Facing.FRONT, seat.interactionFacing)
        assertEquals(seat.x, slot.x)
        assertEquals(seat.floorY, slot.floorY)
        assertEquals("table_a", seat.tableId)

        val baselines = RestaurantScene().sortedProps.map { it.baseline }
        assertTrue("cadeira traseira deve estar atrás do gato", baselines.any { it < slot.baseline && it >= 216 })
        assertTrue("tampo/prato devem passar à frente da parte inferior do torso", baselines.any { it == 224 })
        assertTrue("borda frontal deve passar à frente do gato", baselines.any { it == 260 })
        assertTrue("NPC deve estar centrado na cadeira", kotlin.math.abs(seat.x - 205) <= 1)
        assertTrue("prato deve ficar diretamente à frente do assento", kotlin.math.abs(seat.x - 203) <= 3)
        assertTrue("mesa e cadeira devem pertencer à área do assento", seat.x in 194..217 && seat.floorY in 240..255)

        val eatPose = NpcPoseLibrary.frame(
            NpcAnimation.SIT_EAT, 700, 0, SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.CAT_GUEST),
            seated = true, facing = seat.interactionFacing,
        )
        val frame = CharacterPainter.paint(NpcCharacterRegistry.CAT_GUEST, eatPose.pose, eatPose.motion)
        val handX = seat.x - frame.anchors.feet.x + frame.anchors.rightHand.x
        val handY = seat.floorY - frame.anchors.feet.y + frame.anchors.rightHand.y
        assertTrue("garfo deve alcançar o prato: hand=($handX,$handY)", kotlin.math.abs(handX - 208) <= 9 && kotlin.math.abs(handY - 228) <= 8)
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
