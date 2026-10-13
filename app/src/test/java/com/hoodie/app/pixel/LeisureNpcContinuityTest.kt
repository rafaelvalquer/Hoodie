package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.sprite.Facing
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Sem saltos, sem viradas instantâneas e sem sentar/levantar de um quadro para o outro. */
class LeisureNpcContinuityTest {
    private val turns = setOf(NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT)

    private fun visible(x: Int) = x in -30..270

    @Test fun movementIsContinuousAtThirtyFps() {
        listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { period ->
            (0 until 3).forEach { variant ->
                val session = LeisureTestFixtures.session(variant, daySeed = 50_000 + variant, period = period)
                session.npcSlots.forEach { slot ->
                    val id = slot.definition.id
                    val ms = SpeciesMotionProfiles.forCharacter(slot.definition.characterStyle).msPerPixel
                    val maxStep = (33 / ms + 2).toInt()
                    LeisureTestFixtures.track(session, id, 240_000).zipWithNext().forEach { (p, q) ->
                        val a = p.second; val b = q.second
                        if (!visible(a.x) || !visible(b.x) || a.offstage || b.offstage) return@forEach
                        val jump = maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY))
                        assertTrue("$period v$variant $id t=${q.first}: salto de $jump px ($a → $b)", jump <= maxStep)
                    }
                }
            }
        }
    }

    @Test fun directionChangesPassThroughATurn() {
        (0 until 3).forEach { variant ->
            val session = LeisureTestFixtures.session(variant, daySeed = 51_000 + variant)
            session.npcSlots.forEach { slot ->
                LeisureTestFixtures.track(session, slot.definition.id, 240_000).zipWithNext().forEach { (p, q) ->
                    val a = p.second.movement; val b = q.second.movement
                    if (p.second.offstage || q.second.offstage) return@forEach
                    val reacting = a.animation == NpcAnimation.REACTION || b.animation == NpcAnimation.REACTION
                    // Perfil: virar de lado só passando por TURN.
                    if (a.facingRight != b.facingRight && a.facing != Facing.FRONT && b.facing != Facing.FRONT && !reacting) {
                        assertTrue("v$variant ${slot.definition.id} t=${q.first}: virou sem TURN (${a.animation} → ${b.animation})",
                            a.animation in turns || b.animation in turns)
                    }
                    // Perfil ↔ frente (foto, banco) também só com a virada para a frente/lado.
                    val aFront = a.facing == Facing.FRONT; val bFront = b.facing == Facing.FRONT
                    if (aFront != bFront && !reacting && a.animation != NpcAnimation.WALK && b.animation != NpcAnimation.WALK) {
                        assertTrue("v$variant ${slot.definition.id} t=${q.first}: frente/perfil sem virar (${a.animation} → ${b.animation})",
                            a.animation in turns || b.animation in turns)
                    }
                }
            }
        }
    }

    @Test fun sittingAndStandingAreAnimated() {
        val session = LeisureTestFixtures.session(daySeed = 52_000)
        var sits = 0; var stands = 0
        LeisureTestFixtures.track(session, NpcCharacterRegistry.MOUSE_COMMUTER.id, 600_000).zipWithNext().forEach { (p, q) ->
            val a = p.second.movement; val b = q.second.movement
            if (!a.seated && b.seated) { sits++; assertTrue("sentou sem SIT em ${q.first}", a.animation == NpcAnimation.SIT) }
            if (a.seated && !b.seated) { stands++; assertTrue("levantou sem STAND_UP em ${q.first}", b.animation == NpcAnimation.STAND_UP) }
        }
        assertTrue("o rato levanta do banco em 10 min ($stands)", stands >= 1)
    }

    /** Parados, dois visitantes nunca ficam um em cima do outro (cruzar andando é natural). */
    @Test fun standingVisitorsDoNotStack() {
        val moving = setOf(NpcAnimation.WALK, NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT)
        (0 until 3).forEach { variant ->
            val session = LeisureTestFixtures.session(variant, daySeed = 54_000 + variant)
            (0L until 600_000L step 100L).forEach { t ->
                val still = session.snapshotAt(t).npcs.filter { !it.offstage && it.movement.animation !in moving && visible(it.x) }
                still.forEachIndexed { i, a ->
                    still.drop(i + 1).forEach { b ->
                        if (abs(a.floorY - b.floorY) < 12) {
                            assertTrue("v$variant t=$t: ${a.npcId} (${a.x},${a.floorY}) e ${b.npcId} (${b.x},${b.floorY}) empilhados",
                                abs(a.x - b.x) >= 24)
                        }
                    }
                }
            }
        }
    }

    /** Entradas e saídas acontecem pelas bordas: ninguém some ou aparece no meio da cena. */
    @Test fun visitorsEnterAndLeaveThroughTheEdges() {
        val session = LeisureTestFixtures.session(daySeed = 53_000)
        session.npcSlots.forEach { slot ->
            LeisureTestFixtures.track(session, slot.definition.id, 900_000, 50).zipWithNext().forEach { (p, q) ->
                val a = p.second; val b = q.second
                if (a.offstage != b.offstage) {
                    val edgeX = if (a.offstage) b.x else a.x
                    assertTrue("${slot.definition.id} t=${q.first}: entrou/saiu em x=$edgeX", edgeX < 0 || edgeX >= 240)
                }
            }
        }
    }
}
