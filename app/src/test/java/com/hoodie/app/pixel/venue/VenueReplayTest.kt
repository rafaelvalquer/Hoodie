package com.hoodie.app.pixel.venue

import org.junit.Assert.assertEquals
import org.junit.Test

/** Avançar e retroceder o Diário preserva o estado (mesmo além da janela guardada). */
class VenueReplayTest {
    @Test fun goingBackInTimeReplaysTheSameScene() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 2, 52_000)
            val early = (0L until 90_000L step 500L).map { s.snapshotAt(it) }
            s.snapshotAt(6 * 60 * 60 * 1_000L) // seis horas depois: a janela guardada já descartou o começo
            val replay = (0L until 90_000L step 500L).map { s.snapshotAt(it) }
            assertEquals("$scene", early, replay)
        }
    }

    @Test fun scrubbingBackAndForthIsStable() {
        VenueTestFixtures.scenes.forEach { scene ->
            val reference = VenueTestFixtures.session(scene, 1, 53_000)
            val scrub = VenueTestFixtures.session(scene, 1, 53_000)
            val times = listOf(120_000L, 3_000L, 60_000L, 59_000L, 240_000L, 1_000L, 180_000L)
            times.forEach { t -> assertEquals("$scene t=$t", reference.snapshotAt(t), scrub.snapshotAt(t)) }
        }
    }
}
