package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.leisure.LeisureNpcDirector
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Mesmo dia, variante, condições e instante → mesma cena (P1 — tempo e replay). */
class LeisureDeterminismTest {

    @Test fun sameKeyAndTimeGiveTheSameSnapshot() {
        val a = LeisureTestFixtures.session(1, daySeed = 70_000)
        val b = LeisureTestFixtures.session(1, daySeed = 70_000)
        (0L until 300_000L step 1_234L).forEach { t -> assertEquals(a.snapshotAt(t), b.snapshotAt(t)) }
    }

    /** A cena não depende de quantos quadros foram desenhados antes. */
    @Test fun updateRateDoesNotChangePositions() {
        val fast = LeisureTestFixtures.session(0, daySeed = 71_000)
        val slow = LeisureTestFixtures.session(0, daySeed = 71_000)
        val jump = LeisureTestFixtures.session(0, daySeed = 71_000)
        val fastStates = (0L..180_000L step 16L).associateWith { fast.snapshotAt(it).npcs }
        val slowStates = (0L..180_000L step 47L).associateWith { slow.snapshotAt(it).npcs }
        slowStates.keys.filter { it in fastStates }.forEach { t -> assertEquals("t=$t", fastStates.getValue(t), slowStates.getValue(t)) }
        // Saltar direto para o fim e voltar dá o mesmo resultado.
        assertEquals(fastStates.getValue(180_000L), jump.snapshotAt(180_000).npcs)
        assertEquals(fastStates.getValue(752L), jump.snapshotAt(752).npcs)
    }

    /** Replay: voltar para um instante anterior (mesmo além da janela guardada) mostra a mesma cena. */
    @Test fun goingBackInTimeReplaysTheSameScene() {
        val s = LeisureTestFixtures.session(2, daySeed = 72_000)
        val early = (0L until 60_000L step 500L).map { s.snapshotAt(it).npcs }
        s.snapshotAt(6 * 60 * 60 * 1_000L) // seis horas depois: a janela guardada já descartou o começo
        val replay = (0L until 60_000L step 500L).map { s.snapshotAt(it).npcs }
        assertEquals(early, replay)
    }

    @Test fun differentSeedsGiveDifferentDays() {
        val a = LeisureTestFixtures.session(0, daySeed = 73_000)
        val b = LeisureTestFixtures.session(0, daySeed = 73_001)
        val differs = (0L until 300_000L step 2_000L).count { a.snapshotAt(it).npcs.map { n -> n.x to n.animation } != b.snapshotAt(it).npcs.map { n -> n.x to n.animation } }
        assertTrue("sementes diferentes deveriam gerar sequências diferentes ($differs)", differs > 20)
    }

    @Test fun variantPicksTheSceneAndDaySeedPicksTheStory() {
        val key = LeisureNpcDirector.sessionKey(LeisureTestFixtures.env(variant = 4, daySeed = 9))
        assertEquals(1, key.variant)
        assertEquals(9, key.daySeed)
        assertNotEquals(LeisureNpcDirector.sessionKey(LeisureTestFixtures.env(1, 9)), LeisureNpcDirector.sessionKey(LeisureTestFixtures.env(1, 10)))
    }

    /** A semente do dia vem da jornada (VisualState.daySeed), separada da variante, só no Passeio. */
    @Test fun stateMachineSeparatesDaySeedFromVariantOnlyInLeisure() {
        val sm = AnimationStateMachine(Random(1))
        val leisure = VisualDirector.resolve(HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, variant = 5).copy(daySeed = 20_123)
        sm.setVisual(leisure, 1)
        val frame = sm.frame(1, 600, DayPeriod.DAY)!!
        assertEquals(5, frame.env.variant)
        assertEquals(20_123, frame.env.daySeed)
        val office = AnimationStateMachine(Random(1))
        office.setVisual(VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, variant = 5).copy(daySeed = 20_123), 1)
        val officeFrame = office.frame(1, 600, DayPeriod.DAY)!!
        assertEquals("o escritório mantém a semente histórica", 5, officeFrame.env.daySeed)
    }

    @Test fun nightHasTwoVisitorsAndTheDayThree() {
        assertEquals(3, LeisureTestFixtures.session(period = DayPeriod.DAY).npcSlots.size)
        assertEquals(2, LeisureTestFixtures.session(period = DayPeriod.NIGHT).npcSlots.size)
        assertEquals(3, NpcDirector.plan(SceneId.LEISURE, LeisureTestFixtures.env()).size)
    }
}
