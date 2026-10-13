package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.leisure.LeisureNpcIntent
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSpot
import com.hoodie.app.pixel.npc.leisure.LeisureSocialCoordinator
import com.hoodie.app.pixel.npc.leisure.LeisureSpeechLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Encontros e cooldown (P1 — vida social). */
class LeisureSocialCoordinatorTest {

    @Test fun linesFitTheBubbleAndAvoidUndrawnPunctuation() {
        LeisureSpeechLibrary.all().forEach { line ->
            assertTrue(line, line.length <= 22)
            assertTrue(line, line.none { it == '!' || it == '?' })
        }
    }

    @Test fun encountersHappenAndFollowTheWholeSequence() {
        var total = 0
        (0 until 10).forEach { seed ->
            val session = LeisureTestFixtures.session(variant = seed % 3, daySeed = 40_000 + seed)
            session.snapshotAt(900_000)
            val encounters = session.encounters()
            total += encounters.size
            encounters.forEach { e ->
                val mid = (e.meetAt + e.endAt) / 2
                val a = session.snapshotAt(mid).npcs.single { it.npcId == e.initiator }
                val b = session.snapshotAt(mid).npcs.single { it.npcId == e.partner }
                assertEquals(LeisureNpcIntent.SOCIALIZING, a.intent); assertEquals(LeisureNpcIntent.SOCIALIZING, b.intent)
                assertTrue("posições de conversa", a.spot in setOf(LeisureNpcSpot.SOCIAL_LEFT, LeisureNpcSpot.SOCIAL_RIGHT))
                // Viram-se um para o outro e ficam a um passo de distância; na vista compartilhada
                // (microevento) ficam lado a lado, virados para a atração.
                val view = session.storiesUntil(e.endAt).firstOrNull { mid in it.event.interactAt until it.event.resolveAt && e.initiator in it.event.participants }
                if (view != null) {
                    assertEquals("lado a lado diante da vista", a.movement.facingRight, b.movement.facingRight)
                } else {
                    assertEquals("${e.initiator} olha para o outro", b.x > a.x, a.movement.facingRight)
                    assertEquals("${e.partner} olha para o outro", a.x > b.x, b.movement.facingRight)
                }
                assertTrue("distância de conversa ${abs(a.x - b.x)}", abs(a.x - b.x) in 20..30)
                // Ninguém anda durante a conversa.
                for (t in e.meetAt until e.endAt step 50) {
                    session.snapshotAt(t).npcs.filter { it.npcId == e.initiator || it.npcId == e.partner }.forEach {
                        assertTrue("andando em plena conversa: $it", it.animation != NpcAnimation.WALK)
                    }
                }
                // Cumprimento → resposta → assunto → despedida: quatro falas, alternando quem fala.
                val speakers = (e.meetAt until e.endAt step 50).mapNotNull { t ->
                    session.snapshotAt(t).npcs.singleOrNull { it.speech != null }?.npcId
                }.fold(mutableListOf<String>()) { acc, id -> if (acc.lastOrNull() != id) acc += id; acc }
                assertEquals(listOf(e.initiator, e.partner, e.initiator, e.partner), speakers)
            }
        }
        assertTrue("encontros precisam acontecer em 15 min de cena ($total em 10 dias)", total >= 8)
    }

    @Test fun oneEncounterAtATimeAndCooldownBetweenThem() {
        (0 until 6).forEach { seed ->
            val session = LeisureTestFixtures.session(daySeed = 41_000 + seed)
            session.snapshotAt(1_800_000)
            val encounters = session.encounters().sortedBy { it.meetAt }
            encounters.zipWithNext().forEach { (a, b) ->
                assertTrue("dois encontros ao mesmo tempo", b.meetAt >= a.endAt)
                if (setOf(a.initiator, a.partner).intersect(setOf(b.initiator, b.partner)).isNotEmpty()) {
                    assertTrue("cooldown social de ${b.meetAt - a.endAt} ms", b.meetAt - a.endAt >= LeisureSocialCoordinator.COOLDOWN_MIN_MS - 10_000)
                }
            }
        }
    }

    /** Só um balão por vez na cena (conversa e comentários sobre o lago/chafariz/flores). */
    @Test fun neverTwoBubblesAtOnce() {
        (0 until 4).forEach { seed ->
            val session = LeisureTestFixtures.session(variant = seed % 3, daySeed = 42_000 + seed)
            for (t in 0L until 600_000L step 100L) {
                val speaking = session.snapshotAt(t).npcs.count { it.speech != null }
                assertTrue("t=$t: $speaking balões", speaking <= 1)
            }
        }
    }
}
