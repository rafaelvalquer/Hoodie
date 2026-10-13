package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.venue.VenueSpotReservation
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Mesas, assentos, filas e balcões nunca têm dois ocupantes; os lugares do Hoodie ficam livres para ele. */
class VenueNpcReservationTest {
    private val moving = setOf(NpcAnimation.WALK, NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT)

    @Test fun standingOrSeatedNpcsNeverStack() {
        VenueTestFixtures.scenes.forEach { scene ->
            listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { period ->
                (0 until 3).forEach { variant ->
                    val s = VenueTestFixtures.session(scene, variant, 47_000 + variant, period)
                    (0L until 600_000L step 200L).forEach { t ->
                        val still = s.snapshotAt(t).npcs.filter { !it.hidden && it.movement.animation !in moving }
                        still.forEachIndexed { i, a ->
                            still.drop(i + 1).forEach { b ->
                                if (abs(a.floorY - b.floorY) < 10) {
                                    assertTrue("$scene v$variant t=$t: ${a.npcId}(${a.x},${a.floorY}) e ${b.npcId}(${b.x},${b.floorY}) empilhados", abs(a.x - b.x) >= 18)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Onde o Hoodie fica (mesa dele, balcão, poltrona, sofá...), nenhum NPC para. */
    @Test fun npcsNeverStopOnTheHoodieSpots() {
        VenueTestFixtures.scenes.forEach { scene ->
            val hoodie = SceneRegistry[scene].spots.filterKeys { it != SpotId.DOOR }.values
            val s = VenueTestFixtures.session(scene, 0, 48_000)
            (0L until 600_000L step 250L).forEach { t ->
                s.snapshotAt(t).npcs.filter { !it.hidden && it.movement.animation !in moving }.forEach { n ->
                    hoodie.forEach { h ->
                        assertFalse("$scene t=$t: ${n.npcId} parado no lugar do Hoodie (${h.x},${h.y})", abs(n.x - h.x) < 14 && abs(n.floorY - h.y) < 10)
                    }
                }
            }
        }
    }

    @Test fun reservationRespectsCapacityAndHoodieSpots() {
        val r = VenueSpotReservation(setOf("hoodie_seat"))
        assertFalse(r.isFree("hoodie_seat", 0, 10, "a"))
        r.reserve("seat", "a", 0, 1_000)
        assertFalse(r.isFree("seat", 500, 600, "b"))
        assertTrue(r.isFree("seat", 500, 600, "a"))
        assertTrue(r.isFree("seat", 1_000, 2_000, "b"))
        assertTrue(r.isFree("seat", 500, 600, "b", capacity = 2))
        r.releaseAt("seat", "a", 300)
        assertTrue(r.isFree("seat", 400, 600, "b"))
        r.prune(10_000)
        assertTrue(r.size == 0)
    }
}
