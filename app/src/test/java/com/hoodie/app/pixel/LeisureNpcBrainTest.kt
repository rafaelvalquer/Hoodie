package com.hoodie.app.pixel

import com.hoodie.app.pixel.LeisureTestFixtures.CAT
import com.hoodie.app.pixel.LeisureTestFixtures.MOUSE
import com.hoodie.app.pixel.LeisureTestFixtures.RABBIT
import com.hoodie.app.pixel.npc.leisure.LeisureActivity
import com.hoodie.app.pixel.npc.leisure.LeisureDecisionContext
import com.hoodie.app.pixel.npc.leisure.LeisureNpcBrain
import com.hoodie.app.pixel.npc.leisure.LeisureNpcIntent
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSpot
import com.hoodie.app.pixel.npc.leisure.LeisurePersonality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diversidade e sequência das atividades (P0 — inteligência individual). */
class LeisureNpcBrainTest {
    private fun ctx(
        seated: Boolean = false, night: Boolean = false, bench: Boolean = true, observe: Boolean = true,
        social: Boolean = true, recent: List<LeisureActivity> = emptyList(), now: Long = 100_000,
    ) = LeisureDecisionContext(now, seated, night, bench, observe, photoReadyAt = 0, socialReadyAt = 0, socialPossible = social, recent = recent, visitDone = false)

    @Test fun personalityWeightsMatchThePlan() {
        assertEquals(mapOf(LeisureActivity.EXPLORE to 35, LeisureActivity.OBSERVE to 25, LeisureActivity.PHOTO to 20, LeisureActivity.STRETCH to 10, LeisureActivity.SOCIAL to 10), LeisurePersonality.EXPLORER.weights)
        assertEquals(mapOf(LeisureActivity.PHOTO to 30, LeisureActivity.EXPLORE to 25, LeisureActivity.OBSERVE to 20, LeisureActivity.SOCIAL to 15, LeisureActivity.STRETCH to 10), LeisurePersonality.TOURIST.weights)
        assertEquals(mapOf(LeisureActivity.READ to 35, LeisureActivity.BENCH_REST to 25, LeisureActivity.PHONE to 20, LeisureActivity.OBSERVE to 10, LeisureActivity.STROLL to 10), LeisurePersonality.CALM.weights)
    }

    @Test fun onlyValidActivitiesTakePartInTheDraw() {
        val night = LeisureNpcBrain.validWeights(LeisurePersonality.TOURIST, ctx(night = true))
        assertFalse("sem foto à noite", LeisureActivity.PHOTO in night)
        val noBench = LeisureNpcBrain.validWeights(LeisurePersonality.CALM, ctx(bench = false))
        assertFalse(LeisureActivity.READ in noBench); assertFalse(LeisureActivity.BENCH_REST in noBench)
        val seated = LeisureNpcBrain.validWeights(LeisurePersonality.CALM, ctx(seated = true, bench = false))
        assertTrue("sentado continua podendo ler", LeisureActivity.READ in seated)
        val alone = LeisureNpcBrain.validWeights(LeisurePersonality.EXPLORER, ctx(social = false))
        assertFalse(LeisureActivity.SOCIAL in alone)
        val repeated = LeisureNpcBrain.validWeights(LeisurePersonality.EXPLORER, ctx(recent = listOf(LeisureActivity.EXPLORE, LeisureActivity.EXPLORE)))
        assertFalse("nunca a mesma atividade 3 vezes seguidas", LeisureActivity.EXPLORE in repeated)
    }

    @Test fun drawsFollowTheWeightsApproximately() {
        val counts = (0L until 4_000L).groupingBy { i -> LeisureNpcBrain.choose(RABBIT, 7, i, LeisurePersonality.EXPLORER, ctx()) }.eachCount()
        val share = counts.mapValues { it.value / 4_000.0 }
        assertEquals(0.35, share.getValue(LeisureActivity.EXPLORE), 0.04)
        assertEquals(0.10, share.getValue(LeisureActivity.SOCIAL), 0.03)
    }

    @Test fun durationsRespectThePlanRanges() {
        (0L until 200L).forEach { i ->
            assertTrue(LeisureNpcBrain.holdMs(LeisureActivity.EXPLORE, false, RABBIT, 3, i) in 2_000L..4_000L)
            assertTrue(LeisureNpcBrain.holdMs(LeisureActivity.READ, true, MOUSE, 3, i) in 5_000L..12_000L)
            assertTrue(LeisureNpcBrain.holdMs(LeisureActivity.BENCH_REST, true, MOUSE, 3, i) in 5_000L..12_000L)
        }
        assertTrue(LeisureNpcBrain.STRETCH_MS in 5_000L..12_000L)
    }

    /** Três minutos de cena: atividades variadas, sem a mesma 3 vezes seguidas e sem vai-e-vem entre dois pontos. */
    @Test fun threeMinutesWithoutPendulumOrExcessiveRepetition() {
        (0 until 6).forEach { seed ->
            val session = LeisureTestFixtures.session(variant = seed % 3, daySeed = 30_000 + seed)
            listOf(RABBIT, CAT, MOUSE).forEach { id ->
                val index = session.npcSlots.indexOfFirst { it.definition.id == id }
                val segments = session.segmentsBetween(index, 0, 180_000)
                val activities = segments.mapNotNull { it.activity }.fold(mutableListOf<LeisureActivity>()) { acc, a -> if (acc.lastOrNull() != a) acc += a; acc }
                assertTrue("$id seed $seed: atividades pouco variadas $activities", activities.toSet().size >= 3)
                // Paradas no caminho: nunca A → B → A → B seguidas.
                val stops = segments.filter { it.kind.name == "STAY" && it.spot in setOf(LeisureNpcSpot.PATH_LEFT, LeisureNpcSpot.PATH_CENTER, LeisureNpcSpot.PATH_RIGHT) }
                    .mapNotNull { it.spot }.fold(mutableListOf<LeisureNpcSpot>()) { acc, s -> if (acc.lastOrNull() != s) acc += s; acc }
                stops.windowed(4).forEach { w ->
                    assertFalse("$id seed $seed: vai-e-vem $w", w[0] == w[2] && w[1] == w[3])
                }
            }
        }
    }
}
