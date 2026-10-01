package com.hoodie.app.engine

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.engine.hoodie.EventContextTimeline
import com.hoodie.app.engine.hoodie.HoodieSimulator
import com.hoodie.app.engine.hoodie.SimulationEnv
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O princípio mais importante: o app nunca depende de ficar aberto. O estado é
 * reconstruído a partir de timestamps, independentemente de quando reabrimos.
 */
class SimulatorPersistenceTest {

    private val env = SimulationEnv(ZONE, officeRoutine, SleepSchedule(), emptySet())

    /** O dia de referência do MVP (Definition of Done). */
    private val day = listOf(
        ContextSpan(UserContextType.HOME, at(MONDAY - 1, 20).ms(), at(MONDAY, 8, 3).ms()),
        ContextSpan(UserContextType.COMMUTING, at(MONDAY, 8, 3).ms(), at(MONDAY, 8, 41).ms()),
        ContextSpan(UserContextType.WORK, at(MONDAY, 8, 41).ms(), at(MONDAY, 12, 6).ms()),
        ContextSpan(UserContextType.LUNCH, at(MONDAY, 12, 6).ms(), at(MONDAY, 12, 53).ms()),
        ContextSpan(UserContextType.WORK, at(MONDAY, 12, 53).ms(), at(MONDAY, 17, 44).ms()),
        ContextSpan(UserContextType.COMMUTING, at(MONDAY, 17, 44).ms(), at(MONDAY, 18, 20).ms()),
        ContextSpan(UserContextType.HOME, at(MONDAY, 18, 20).ms(), null),
    )
    private val timeline = EventContextTimeline(day) { UserContextType.HOME }

    @Test
    fun `CT-PERSIST - fechar e reabrir em qualquer momento produz o mesmo estado`() {
        val start = at(MONDAY - 1, 21).ms()
        val initial = HoodieSimulator.initial(start, timeline, env)
        val target = at(MONDAY, 10, 30).ms()

        // A: app ficou fechado o tempo todo e foi aberto às 10:30.
        val oneShot = HoodieSimulator.advance(initial, target, timeline, env).state
        // B: worker rodou a cada 15 minutos (como no aparelho).
        var s = initial
        var t = start
        while (t < target) { t = minOf(t + 15 * MINUTE_MS, target); s = HoodieSimulator.advance(s, t, timeline, env).state }

        assertEquals(oneShot, s)
        assertEquals(UserContextType.WORK, oneShot.userContext)
    }

    @Test
    fun `dia completo - sai junto, trabalha, almoca, volta e dorme`() {
        val start = at(MONDAY - 1, 21).ms()
        var state = HoodieSimulator.initial(start, timeline, env)
        val seen = mutableMapOf<Long, HoodieActivity>()
        var t = start
        val end = at(MONDAY + 1, 1).ms()
        while (t < end) {
            t += 10 * MINUTE_MS
            state = HoodieSimulator.advance(state, t, timeline, env).state
            seen[t] = state.activity
        }
        fun activityAt(h: Int, m: Int) = seen.entries.last { it.key <= at(MONDAY, h, m).ms() }.value

        assertEquals(HoodieActivity.SLEEPING, seen.entries.first { it.key >= at(MONDAY, 3).ms() }.value)
        assertEquals(HoodieActivity.COMMUTING, activityAt(8, 20))
        assertTrue(activityAt(10, 30) in setOf(HoodieActivity.WORKING, HoodieActivity.COFFEE, HoodieActivity.RESTING, HoodieActivity.PHONE, HoodieActivity.IDLE))
        assertTrue(activityAt(12, 40) in setOf(HoodieActivity.EATING, HoodieActivity.COFFEE, HoodieActivity.PHONE))
        assertEquals(HoodieActivity.COMMUTING, activityAt(18, 0))
        assertEquals(HoodieActivity.SLEEPING, seen.entries.last().value)
    }

    @Test
    fun `atividade continua mantem o desde original`() {
        val start = at(MONDAY, 9).ms()
        val tl = EventContextTimeline(listOf(ContextSpan(UserContextType.COMMUTING, start - HOUR_MS, null))) { UserContextType.HOME }
        val s0 = HoodieSimulator.initial(start, tl, env)
        val s1 = HoodieSimulator.advance(s0, start + 2 * HOUR_MS, tl, env).state
        assertEquals(HoodieActivity.COMMUTING, s1.activity)
        assertEquals(start, s1.startedAt)
    }

    @Test
    fun `mais de 3 dias fechado reinicia o dia sem simular minuto a minuto`() {
        val start = at(MONDAY, 9).ms()
        val s0 = HoodieSimulator.initial(start, timeline, env)
        val r = HoodieSimulator.advance(s0, start + 5 * 24 * HOUR_MS, timeline, env)
        assertTrue(r.completed.isEmpty())
        assertEquals(start + 5 * 24 * HOUR_MS, r.state.startedAt)
    }

    @Test
    fun `horario de verao e fuso nao quebram a reconstrucao`() {
        val ny = java.time.ZoneId.of("America/New_York")
        // 2026-11-01 02:00 → 01:00 (fim do horário de verão nos EUA).
        val start = java.time.LocalDateTime.of(2026, 10, 31, 22, 0).atZone(ny).toInstant().toEpochMilli()
        val e = SimulationEnv(ny, officeRoutine, SleepSchedule(), emptySet())
        val tl = EventContextTimeline(emptyList()) { UserContextType.HOME }
        val s = HoodieSimulator.advance(HoodieSimulator.initial(start, tl, e), start + 14 * HOUR_MS, tl, e).state
        assertTrue(s.startedAt <= start + 14 * HOUR_MS)
    }
}
