package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.venue.VenueNavigationGraph
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import com.hoodie.app.pixel.sprite.Facing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ninguém atravessa objetos sólidos, teleporta, vira de um quadro para o outro ou anda em ciclo. */
class VenueNpcPathTest {
    private val turns = setOf(NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT)

    private fun graphOf(scene: com.hoodie.app.pixel.scene.SceneId, variant: Int = 0) = VenueNpcDirector.createSession(scene, VenueTestFixtures.env(variant)).graph

    @Test fun everyRouteExistsAndNoEdgeCrossesAnObstacle() {
        VenueTestFixtures.scenes.forEach { scene ->
            val g = graphOf(scene)
            g.edges.forEach { (a, b) ->
                val p = g.spot(a); val q = g.spot(b)
                g.obstacles.forEach { o -> assertFalse("$scene $a–$b atravessa ${o.name}", VenueNavigationGraph.crosses(p.x, p.floorY, q.x, q.floorY, o)) }
            }
        }
    }

    @Test fun movementIsContinuousAndNeverInsideObstacles() {
        VenueTestFixtures.scenes.forEach { scene ->
            listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { period ->
                (0 until VenueNpcDirector.VARIANTS).forEach { variant ->
                    val s = VenueTestFixtures.session(scene, variant, 43_000 + variant, period)
                    val obstacles = s.graph.obstacles
                    s.npcSlots.forEach { slot ->
                        val id = slot.definition.id
                        val maxStep = (33 / SpeciesMotionProfiles.forCharacter(slot.definition.characterStyle).msPerPixel + 2).toInt()
                        val track = VenueTestFixtures.track(s, id, 240_000)
                        track.forEach { (t, st) ->
                            if (st.hidden) return@forEach
                            obstacles.forEach { o -> assertFalse("$scene v$variant $period $id t=$t dentro de ${o.name} (${st.x},${st.floorY})", o.contains(st.x, st.floorY)) }
                        }
                        track.zipWithNext().forEach { (p, q) ->
                            val a = p.second; val b = q.second
                            if (a.hidden || b.hidden) return@forEach
                            val jump = maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY))
                            assertTrue("$scene v$variant $id t=${q.first}: salto de $jump px", jump <= maxStep)
                        }
                    }
                }
            }
        }
    }

    @Test fun directionAndPostureChangesAreAnimated() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 0, 44_000)
            s.npcSlots.forEach { slot ->
                VenueTestFixtures.track(s, slot.definition.id, 240_000).zipWithNext().forEach { (p, q) ->
                    val a = p.second.movement; val b = q.second.movement
                    if (p.second.hidden || q.second.hidden) return@forEach
                    val id = "$scene ${slot.definition.id} t=${q.first}"
                    if (a.facingRight != b.facingRight && a.facing != Facing.FRONT && b.facing != Facing.FRONT) {
                        assertTrue("$id: virou sem TURN (${a.animation} → ${b.animation})", a.animation in turns || b.animation in turns)
                    }
                    val aFront = a.facing == Facing.FRONT; val bFront = b.facing == Facing.FRONT
                    if (aFront != bFront && a.animation != NpcAnimation.WALK && b.animation != NpcAnimation.WALK) {
                        assertTrue("$id: frente/perfil sem virar (${a.animation} → ${b.animation})", a.animation in turns || b.animation in turns)
                    }
                    if (!a.seated && b.seated) assertEquals("$id: sentou sem SIT", NpcAnimation.SIT, a.animation)
                    if (a.seated && !b.seated) assertEquals("$id: levantou sem STAND_UP", NpcAnimation.STAND_UP, b.animation)
                }
            }
        }
    }

    /** Ninguém aparece ou some no meio do salão: só pela porta/borda. */
    @Test fun visitorsAppearOnlyAtDoors() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 0, 45_000)
            val doors = s.graph.spots.values.filter { it.offstage }
            s.npcSlots.forEach { slot ->
                VenueTestFixtures.track(s, slot.definition.id, 600_000, 50).zipWithNext().forEach { (p, q) ->
                    val a = p.second; val b = q.second
                    if (a.hidden == b.hidden) return@forEach
                    val visible = if (a.hidden) b else a
                    val nearDoor = doors.any { maxOf(abs(it.x - visible.x), abs(it.floorY - visible.floorY)) <= 4 } ||
                        visible.state.name in setOf("PERFORMING_ACTION", "INTERACTING") // entrar na loja / no elevador
                    assertTrue("$scene ${slot.definition.id} t=${q.first}: apareceu/sumiu em (${visible.x},${visible.floorY})", nearDoor)
                }
            }
        }
    }

    /**
     * Cinco minutos sem ciclo artificial: as posições de um cliente nunca se repetem com período
     * fixo (A → B → A → B no mesmo ritmo).
     */
    @Test fun fiveMinutesWithoutArtificialCycles() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 0, 46_000)
            s.npcSlots.forEach { slot ->
                val pos = VenueTestFixtures.track(s, slot.definition.id, 300_000, 500).map { (_, st) -> Triple(st.x, st.floorY, st.movement.animation) }
                // Quem trabalha parado no posto (sorveteiro) não tem trajeto para repetir.
                if (pos.map { it.first to it.second }.toSet().size < 3) return@forEach
                for (period in 10..300) { // 5 s .. 150 s
                    val window = pos.size - period
                    if (window < 300) break
                    val same = (0 until window).count { pos[it] == pos[it + period] }
                    assertTrue("$scene ${slot.definition.id}: ciclo de ${period / 2} s", same < window * 0.9)
                }
            }
        }
    }
}
