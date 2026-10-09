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
    fun `cache de reservas sociais permanece limitado apos simulacao longa`() {
        val session = OfficeNpcDirector.createSession(env(37))
        val brains = session.npcSlots(9 * 60).mapNotNull { it.officeBrain }
        val expected = brains.associate { it.npcId to it.frameStateAt(60 * 60_000L) }

        assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())
        brains.forEach { brain ->
            brain.frameStateAt(5 * 60_000L)
            assertEquals(expected.getValue(brain.npcId), brain.frameStateAt(60 * 60_000L))
        }
        assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())
    }

    @Test
    fun `troca de cena libera a sessao e reentrada cria um ciclo novo`() {
        val before = OfficePerformanceCounters.sessionsCreated.get()
        val renderer = SceneRenderer()
        renderer.renderEmpty(OfficeScene(), env(42), 0L)
        renderer.renderEmpty(SceneRegistry[SceneId.HOME], env(42), 33L)
        renderer.renderEmpty(OfficeScene(), env(42), 66L)
        assertEquals(2L, OfficePerformanceCounters.sessionsCreated.get() - before)
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
