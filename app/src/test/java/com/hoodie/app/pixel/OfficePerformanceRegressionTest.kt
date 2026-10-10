package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficePerformanceCounters
import com.hoodie.app.pixel.scene.OfficeScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.renderer.SceneRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficePerformanceRegressionTest {
    private fun env(seed: Int, minute: Int = 9 * 60) = SceneEnv(
        DayPeriod.DAY, minute, variant = seed, daySeed = seed,
    )

    @Test
    fun `120 quadros do renderer reutilizam os tres NPCs e a coordenacao`() {
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()
        val attachesBefore = OfficePerformanceCounters.socialAttaches.get()
        val renderer = SceneRenderer()
        val scene = OfficeScene()
        repeat(120) { frame -> renderer.renderEmpty(scene, env(42), frame * 33L) }

        assertEquals(1L, OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
        assertEquals(3L, OfficePerformanceCounters.brainsCreated.get() - brainsBefore)
        assertEquals(3L, OfficePerformanceCounters.socialAttaches.get() - attachesBefore)
    }

    @Test
    fun `perfil do minuto nao recria brains nem limpa timelines e seeds ficam isoladas`() {
        val first = OfficeNpcDirector.createSession(env(7))
        val initialSlots = first.npcSlots(9 * 60)
        val initialBrains = initialSlots.map { it.officeBrain }
        initialBrains.filterNotNull().forEach { it.frameStateAt(30 * 60_000L) }
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val rebuildsBefore = OfficePerformanceCounters.timelineRebuilds.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()

        val changedMinuteSlots = first.npcSlots(10 * 60)
        assertSame(initialSlots, changedMinuteSlots)
        assertEquals(initialBrains, changedMinuteSlots.map { it.officeBrain })
        assertEquals(brainsBefore, OfficePerformanceCounters.brainsCreated.get())
        assertEquals(resetsBefore, OfficePerformanceCounters.timelineResets.get())
        assertTrue(OfficePerformanceCounters.timelineRebuilds.get() >= rebuildsBefore)

        val second = OfficeNpcDirector.createSession(env(8))
        assertNotSame(first.npcSlots(10 * 60).first().officeBrain, second.npcSlots(10 * 60).first().officeBrain)
        assertNotSame(first.social, second.social)
    }

    @Test
    fun `busca temporal permanece deterministica apos salto de trinta minutos`() {
        val brains = OfficeNpcDirector.createSession(env(91)).npcSlots(9 * 60).mapNotNull { it.officeBrain }
        val expected = brains.associate { it.npcId to it.frameStateAt(30 * 60_000L) }
        brains.forEach { brain ->
            brain.frameStateAt(30 * 60_000L - 1)
            assertEquals(expected.getValue(brain.npcId), brain.frameStateAt(30 * 60_000L))
        }
    }

    @Test
    fun `caches temporais permanecem limitados apos simulacao longa`() {
        val session = OfficeNpcDirector.createSession(env(37), 8_192, 512)
        val brains = session.npcSlots(9 * 60).mapNotNull { it.officeBrain }
        val oldRawActions = brains.associate { it.npcId to it.rawBaseActionAt(5 * 60_000L) }
        val expected = brains.associate { it.npcId to it.frameStateAt(60 * 60_000L) }
        brains.forEach { it.rawBaseActionAt(24 * 60 * 60_000L) }

        assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())
        brains.forEach { brain ->
            assertTrue(brain.rawTimelineCacheSize() <= brain.rawTimelineCacheCapacity())
            assertTrue(brain.rawTimelinePruneCount() > 0)
            assertEquals(oldRawActions.getValue(brain.npcId), brain.rawBaseActionAt(5 * 60_000L))
            brain.frameStateAt(5 * 60_000L)
            assertEquals(expected.getValue(brain.npcId), brain.frameStateAt(60 * 60_000L))
        }
        assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())
    }

    @Test
    fun `timeline coordenada tem limite e replay antigo preserva determinismo`() {
        val prunesBefore = OfficePerformanceCounters.timelinePrunes.get()
        val longTimeline = OfficeNpcDirector.createSession(env(52), coordinatedTimelineLimit = 1_024)
        val expectedBrain = longTimeline.npcSlots(9 * 60).first().officeBrain!!
        val expectedAtTwoMinutes = expectedBrain.frameStateAt(2 * 60_000L)
        val expectedAtTenMinutes = expectedBrain.frameStateAt(10 * 60_000L)

        val bounded = OfficeNpcDirector.createSession(env(52), coordinatedTimelineLimit = 16)
        val boundedBrain = bounded.npcSlots(9 * 60).first().officeBrain!!
        assertEquals(expectedAtTenMinutes, boundedBrain.frameStateAt(10 * 60_000L))
        assertTrue(boundedBrain.coordinatedTimelineCacheSize() <= boundedBrain.coordinatedTimelineCacheCapacity())
        assertTrue(boundedBrain.coordinatedTimelinePruneCount() > 0)
        assertTrue(OfficePerformanceCounters.timelinePrunes.get() > prunesBefore)
        assertEquals(expectedAtTwoMinutes, boundedBrain.frameStateAt(2 * 60_000L))
    }

    @Test
    fun `busca antiga apos uma hora de timeline podada preserva resultado`() {
        val reference = OfficeNpcDirector.createSession(env(53), 4_096, 24_576)
        val referenceBrain = reference.npcSlots(9 * 60).first().officeBrain!!
        val expected = referenceBrain.frameStateAt(10 * 60_000L)

        val bounded = OfficeNpcDirector.createSession(env(53), 16, 24_576)
        val brain = bounded.npcSlots(9 * 60).first().officeBrain!!
        brain.frameStateAt(60 * 60_000L)
        assertTrue(bounded.social.cachedReservationCount() > 512)
        assertTrue(bounded.social.cachedReservationCount() <= bounded.social.reservationCacheCapacity())
        assertEquals(expected, brain.frameStateAt(10 * 60_000L))
    }

    @Test
    fun `busca no comeco do dia apos caches atingirem os limites permanece deterministica`() {
        val expectedSession = OfficeNpcDirector.createSession(env(54))
        val expectedBrain = expectedSession.npcSlots(9 * 60).first().officeBrain!!
        val expected = expectedBrain.frameStateAt(60 * 60_000L)

        val session = OfficeNpcDirector.createSession(env(54))
        val brains = session.npcSlots(9 * 60).mapNotNull { it.officeBrain }
        val dayStates = brains.associate { it.npcId to it.frameStateAt(24 * 60 * 60_000L) }
        assertTrue(brains.any { it.coordinatedTimelinePruneCount() > 0 })
        assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())

        val replayStartedAt = System.nanoTime()
        val replayed = brains.first().frameStateAt(60 * 60_000L)
        val replayDurationMs = (System.nanoTime() - replayStartedAt) / 1_000_000.0
        assertEquals(expected, replayed)
        assertEquals(dayStates.keys, brains.map { it.npcId }.toSet())
        println(
            "OFFICE_DAY_BACKSEEK replay=${replayDurationMs}ms " +
                "reservationCache=${session.social.cachedReservationCount()}/" +
                session.social.reservationCacheCapacity()
        )
    }

    @Test
    fun `troca de cena libera a sessao e reentrada cria um ciclo novo`() {
        val before = OfficePerformanceCounters.sessionsCreated.get()
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val renderer = SceneRenderer()
        renderer.renderEmpty(OfficeScene(), env(42), 0L)
        renderer.renderEmpty(SceneRegistry[SceneId.HOME], env(42), 33L)
        renderer.renderEmpty(OfficeScene(), env(42), 66L)
        renderer.dispose()
        assertEquals(2L, OfficePerformanceCounters.sessionsCreated.get() - before)
        assertEquals(6L, OfficePerformanceCounters.timelineResets.get() - resetsBefore)
    }

    @Test
    fun `chaves compostas nao colidem quando o seed combinado coincide`() {
        val a = OfficeNpcDirector.createSession(SceneEnv(DayPeriod.DAY, 540, daySeed = 1, variant = 31))
        val b = OfficeNpcDirector.createSession(SceneEnv(DayPeriod.DAY, 540, daySeed = 2, variant = 0))
        assertNotEquals(a.key, b.key)
        assertNotSame(a.social, b.social)
        assertNotSame(a.npcSlots(540).first().officeBrain, b.npcSlots(540).first().officeBrain)
    }
}
