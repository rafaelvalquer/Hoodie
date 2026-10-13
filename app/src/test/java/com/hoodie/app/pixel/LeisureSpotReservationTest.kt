package com.hoodie.app.pixel

import com.hoodie.app.pixel.LeisureTestFixtures.MOUSE
import com.hoodie.app.pixel.LeisureTestFixtures.onBenchSeat
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSpot
import com.hoodie.app.pixel.npc.leisure.LeisureSpotReservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ocupação exclusiva do banco (P0). Nunca Hoodie e visitante no mesmo assento. */
class LeisureSpotReservationTest {

    @Test fun aSpotHasASingleOccupantAtATime() {
        val r = LeisureSpotReservation()
        r.reserve(LeisureNpcSpot.BENCH_SEAT, "mouse", 0, 10_000)
        assertFalse(r.isFree(LeisureNpcSpot.BENCH_SEAT, 5_000, 6_000, "rabbit"))
        assertTrue("o próprio dono continua podendo usar", r.isFree(LeisureNpcSpot.BENCH_SEAT, 5_000, 6_000, "mouse"))
        assertTrue(r.isFree(LeisureNpcSpot.BENCH_SEAT, 10_000, 12_000, "rabbit"))
        r.releaseAt(LeisureNpcSpot.BENCH_SEAT, "mouse", 4_000)
        assertTrue("saída antecipada libera o lugar", r.isFree(LeisureNpcSpot.BENCH_SEAT, 5_000, 6_000, "rabbit"))
    }

    @Test fun theHoodieClaimBlocksTheBenchForEveryone() {
        val r = LeisureSpotReservation()
        r.hoodieClaims += LeisureSpotReservation.HoodieClaim(3_000, null)
        assertFalse(r.isFree(LeisureNpcSpot.BENCH_SEAT, 2_000, 4_000, "mouse"))
        assertTrue(r.isFree(LeisureNpcSpot.BENCH_SEAT, 0, 2_900, "mouse"))
        r.hoodieClaims.last().end = 9_000
        assertTrue(r.isFree(LeisureNpcSpot.BENCH_SEAT, 9_000, 12_000, "mouse"))
        assertEquals(3_000L, r.firstHoodieClaimStartIn(0, 10_000))
    }

    private fun mouseAt(s: LeisureNpcSession, t: Long) = s.snapshotAt(t).npcs.single { it.npcId == MOUSE }

    /** Rato sentado → Hoodie escolhe o banco → rato termina, levanta, sai → só então o Hoodie chega. */
    @Test fun seatedVisitorYieldsTheBenchBeforeTheHoodieArrives() {
        val session = LeisureTestFixtures.session(daySeed = 21_000)
        session.observeHoodie(0, LeisureTestFixtures.HOODIE_ON_PATH)
        // Começa sentado no banco.
        assertTrue("o rato começa no banco", onBenchSeat(mouseAt(session, 1_000)))
        val claimAt = 2_000L
        (33L..claimAt step 33L).forEach { session.observeHoodie(it, LeisureTestFixtures.HOODIE_ON_PATH); session.snapshotAt(it) }
        session.observeHoodie(claimAt, LeisureTestFixtures.HOODIE_GOING_TO_BENCH)
        var sawStandUp = false
        for (t in claimAt..claimAt + 6_000 step 33L) {
            session.observeHoodie(t, if (t < claimAt + 2_600) LeisureTestFixtures.HOODIE_GOING_TO_BENCH else LeisureTestFixtures.HOODIE_ON_BENCH)
            val m = mouseAt(session, t)
            if (m.animation == NpcAnimation.STAND_UP) sawStandUp = true
            // O Hoodie leva ~2,2 s do caminho ao banco; a 2,6 s o assento já está livre.
            if (t >= claimAt + 2_600) assertFalse("rato ainda no assento em t=$t ($m)", onBenchSeat(m))
        }
        assertTrue("levantou com a animação, sem teletransporte", sawStandUp)
        assertEquals(LeisureNpcSession.HOODIE_ID, session.snapshotAt(claimAt + 3_000).benchHolder)
    }

    /** Entrada direta com o Hoodie já sentado: o rato nunca aparece sentado no banco. */
    @Test fun visitorNeverStartsOnTheBenchWhenTheHoodieIsAlreadyThere() {
        (0 until 8).forEach { seed ->
            val session = LeisureTestFixtures.session(daySeed = 22_000 + seed)
            session.observeHoodie(0, LeisureTestFixtures.HOODIE_ON_BENCH)
            for (t in 0L until 240_000L step 250L) {
                session.observeHoodie(t, LeisureTestFixtures.HOODIE_ON_BENCH)
                val m = mouseAt(session, t)
                assertFalse("seed $seed t=$t: rato no banco com o Hoodie sentado", onBenchSeat(m) || m.seated)
            }
        }
    }

    /** Hoodie sai do banco: o lugar volta a ficar disponível para o rato. */
    @Test fun theBenchBecomesAvailableAgainAfterTheHoodieLeaves() {
        val session = LeisureTestFixtures.session(daySeed = 23_000)
        session.observeHoodie(0, LeisureTestFixtures.HOODIE_ON_BENCH)
        (0L..20_000L step 100L).forEach { session.observeHoodie(it, LeisureTestFixtures.HOODIE_ON_BENCH); session.snapshotAt(it) }
        var satAgain = false
        for (t in 20_000L until 400_000L step 200L) {
            session.observeHoodie(t, LeisureTestFixtures.HOODIE_ON_PATH)
            if (mouseAt(session, t).seated) { satAgain = true; break }
        }
        assertTrue("o rato voltou a usar o banco depois que o Hoodie saiu", satAgain)
        assertNull("sem reserva do Hoodie no fim", session.snapshotAt(400_000).benchHolder?.takeIf { it == LeisureNpcSession.HOODIE_ID })
    }

    /** Replay depois da troca: voltar no tempo mostra o mesmo banco ocupado/liberado. */
    @Test fun replayAfterTheClaimShowsTheSameBench() {
        val session = LeisureTestFixtures.session(daySeed = 24_000)
        session.observeHoodie(0, LeisureTestFixtures.HOODIE_ON_PATH)
        (0L..3_000L step 50L).forEach { session.observeHoodie(it, LeisureTestFixtures.HOODIE_ON_PATH); session.snapshotAt(it) }
        session.observeHoodie(3_050, LeisureTestFixtures.HOODIE_GOING_TO_BENCH)
        val forward = (3_050L..9_000L step 50L).map { session.snapshotAt(it).npcs }
        val replay = (3_050L..9_000L step 50L).map { session.snapshotAt(it).npcs }
        assertEquals(forward, replay)
        // O passado anterior à reserva não muda: igual ao de um dia em que o Hoodie nunca foi ao banco.
        val control = LeisureTestFixtures.session(daySeed = 24_000)
        control.observeHoodie(0, LeisureTestFixtures.HOODIE_ON_PATH)
        val before = (0L..3_000L step 50L).map { session.snapshotAt(it).npcs.single { n -> n.npcId == MOUSE }.movement }
        val untouched = (0L..3_000L step 50L).map { control.snapshotAt(it).npcs.single { n -> n.npcId == MOUSE }.movement }
        assertEquals("antes da reserva o rato fazia o mesmo", untouched, before)
    }
}
