package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.leisure.LeisureAmbientState
import com.hoodie.app.pixel.npc.leisure.LeisureNpcIntent
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Os elementos do cenário reagem ao que os visitantes fazem (P1 — ambiente vivo). */
class LeisureAmbientStateTest {

    /** Instantes em que alguém está parado diante da atração da variante. */
    private fun watchedMoments(variant: Int, seed: Int, intent: LeisureNpcIntent? = null) =
        LeisureTestFixtures.session(variant, daySeed = seed).let { s ->
            (0L until 900_000L step 250L).map { t -> t to s.snapshotAt(t) }.filter { (_, snap) ->
                snap.npcs.any { it.spot == LeisureNpcSpot.NATURE_VIEW && it.animation.name != "WALK" && (intent == null || it.intent == intent) &&
                    (it.intent == LeisureNpcIntent.OBSERVING || it.intent == LeisureNpcIntent.PHOTOGRAPHING) }
            }
        }

    @Test fun ducksComeToTheShoreWhileSomeoneWatchesTheLake() {
        val watched = (0 until 6).flatMap { watchedMoments(0, 60_000 + it) }
        assertTrue("alguém precisa observar o lago em 15 min", watched.isNotEmpty())
        // No auge da atração os patos se deslocam para a margem do visitante e levantam a cabeça.
        val peak = watched.map { it.second.ambient }.filter { it.attraction >= 0.99f }
        assertTrue(peak.isNotEmpty())
        peak.forEach { a ->
            assertTrue("pato deveria ir à margem (offset ${a.duckOffsetX})", a.watcherId != null && a.duckAlert)
        }
        val calm = LeisureAmbientState.idle(0, 1_000)
        assertEquals(0, calm.duckOffsetX)
        assertTrue(!calm.duckAlert)
    }

    @Test fun fountainRisesAndFlashesWhenPhotographed() {
        val moments = (0 until 10).flatMap { watchedMoments(1, 61_000 + it, LeisureNpcIntent.PHOTOGRAPHING) }
        assertTrue("alguém fotografa o chafariz", moments.isNotEmpty())
        assertTrue("o jato sobe durante a foto", moments.all { it.second.ambient.fountainBoost >= 2 })
        val flashes = (0 until 10).sumOf { seed ->
            val s = LeisureTestFixtures.session(1, daySeed = 61_000 + seed)
            (0L until 900_000L step 50L).count { s.snapshotAt(it).ambient.photoFlash }
        }
        assertTrue("o clique da foto aparece no chafariz", flashes > 0)
    }

    @Test fun butterflyCirclesWhoStopsAmongTheFlowers() {
        val moments = (0 until 6).flatMap { watchedMoments(2, 62_000 + it) }.filter { it.second.ambient.attraction >= 0.99f }
        assertTrue(moments.isNotEmpty())
        moments.forEach { (_, snap) ->
            val a = snap.ambient
            val (bx, by) = a.butterfly
            assertTrue("borboleta perto de quem observa ($bx,$by) × (${a.watcherX},${a.watcherFloorY})",
                abs(bx - a.watcherX) <= 14 && abs(by - (a.watcherFloorY - LeisureAmbientState.HEAD_ABOVE_FEET)) <= 8)
        }
    }

    @Test fun nothingReactsWhenNobodyIsThere() {
        val s = LeisureTestFixtures.session(0, daySeed = 63_000)
        (0L until 300_000L step 500L).forEach { t ->
            val snap = s.snapshotAt(t)
            if (snap.npcs.none { it.spot == LeisureNpcSpot.NATURE_VIEW } && snap.ambient.watcherId == null) {
                assertEquals(0f, snap.ambient.attraction)
                assertNull(snap.ambient.watcherId)
            }
        }
    }
}
