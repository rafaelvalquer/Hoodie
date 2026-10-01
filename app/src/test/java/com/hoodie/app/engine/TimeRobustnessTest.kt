package com.hoodie.app.engine

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.currentDateFlow
import com.hoodie.app.engine.hoodie.EventContextTimeline
import com.hoodie.app.engine.hoodie.HoodieSimulator
import com.hoodie.app.engine.hoodie.SimulationEnv
import com.hoodie.app.engine.hoodie.SimulationRecovery
import com.hoodie.app.engine.hoodie.SimulationRecoveryPolicy
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.engine.timeline.ContextSpan
import com.hoodie.app.receiver.BootReceiver
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Relógio de teste em que o fuso também pode mudar. */
private class MutableClock(var millis: Long, var zoneId: ZoneId = ZONE) : ClockProvider {
    override fun nowMillis() = millis
    override fun zone() = zoneId
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimeRobustnessTest {

    private val env = SimulationEnv(ZONE, officeRoutine, SleepSchedule(), emptySet())

    private fun collectDates(clock: MutableClock, change: () -> Unit): List<LocalDate> {
        val seen = mutableListOf<LocalDate>()
        runTest {
            val job = launch { currentDateFlow(clock, tickMs = 1_000).toList(seen) }
            runCurrent()
            change()
            advanceTimeBy(1_001)
            runCurrent()
            job.cancel()
        }
        return seen
    }

    @Test
    fun `CT-DATE-001 23h59 para 00h00 emite o novo dia`() {
        val clock = MutableClock(at(MONDAY, 23, 59).ms())
        val seen = collectDates(clock) { clock.millis = at(MONDAY + 1, 0, 0).ms() }
        assertEquals(listOf(at(MONDAY, 0).toLocalDate(), at(MONDAY + 1, 0).toLocalDate()), seen)
    }

    @Test
    fun `CT-DATE-002 domingo para segunda recalcula dia de trabalho`() {
        val clock = MutableClock(at(SUNDAY, 23, 59).ms())
        val seen = collectDates(clock) { clock.millis = at(SUNDAY + 1, 0, 1).ms() }
        assertFalse(RoutineEngine.isWorkDay(seen.first(), officeRoutine, false))
        assertTrue(RoutineEngine.isWorkDay(seen.last(), officeRoutine, false))
    }

    @Test
    fun `CT-DATE-003 mudanca de fuso muda o dia e a rotina provavel`() {
        // 22:30 em São Paulo = 01:30 do dia seguinte em Lisboa.
        val clock = MutableClock(at(MONDAY, 22, 30).ms())
        val seen = collectDates(clock) { clock.zoneId = ZoneId.of("Europe/Lisbon") }
        assertEquals(2, seen.size)
        val lisbon = clock.now()
        assertEquals(at(MONDAY + 1, 0).toLocalDate(), seen.last())
        assertEquals(UserContextType.HOME, RoutineEngine.probableContext(lisbon, officeRoutine, false))
        assertTrue("madrugada em Lisboa", lisbon.hour in 1..2)
    }

    @Test
    fun `CT-DATE-004 TIME_SET e TIMEZONE_CHANGED disparam reconciliacao`() {
        assertTrue(Intent.ACTION_TIME_CHANGED in BootReceiver.TIME_ACTIONS)
        assertTrue(Intent.ACTION_TIMEZONE_CHANGED in BootReceiver.TIME_ACTIONS)
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.REGISTER_ACTIONS)
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in BootReceiver.REGISTER_ACTIONS)
    }

    @Test
    fun `CT-DATE-005 virada do horario de verao nao perde nem duplica atividades`() {
        val ny = ZoneId.of("America/New_York")
        val start = java.time.LocalDateTime.of(2026, 3, 7, 20, 0).atZone(ny).toInstant().toEpochMilli()
        val e = SimulationEnv(ny, officeRoutine, SleepSchedule(), emptySet())
        val tl = EventContextTimeline(emptyList()) { UserContextType.HOME }
        val r = HoodieSimulator.advance(HoodieSimulator.initial(start, tl, e), start + 20 * HOUR_MS, tl, e)
        r.completed.zipWithNext().forEach { (a, b) -> assertEquals(a.endedAt, b.startedAt) }
        assertTrue(r.completed.all { it.endedAt > it.startedAt })
    }

    @Test
    fun `politica de recuperacao explicita`() {
        val t = at(MONDAY, 9).ms()
        assertEquals(SimulationRecovery.NORMAL_CATCH_UP, SimulationRecoveryPolicy.decide(t, t + 3 * DAY_MS))
        assertEquals(SimulationRecovery.RESET_AFTER_LONG_ABSENCE, SimulationRecoveryPolicy.decide(t, t + 3 * DAY_MS + 1))
    }

    @Test
    fun `CT-TRANSITION-006 contexto mudado por tras do estado e percebido na hora`() {
        // Hoodie trabalhando desde 09:00, mas o evento de almoço só chegou depois (boundary no passado).
        val workUntilLunch = listOf(ContextSpan(UserContextType.WORK, at(MONDAY, 8).ms(), null))
        val s0 = HoodieSimulator.initial(at(MONDAY, 12).ms(), EventContextTimeline(workUntilLunch) { UserContextType.HOME }, env)
        val corrected = EventContextTimeline(
            listOf(
                ContextSpan(UserContextType.WORK, at(MONDAY, 8).ms(), at(MONDAY, 11, 50).ms()),
                ContextSpan(UserContextType.LUNCH, at(MONDAY, 11, 50).ms(), null),
            ),
        ) { UserContextType.HOME }
        val s1 = HoodieSimulator.advance(s0, at(MONDAY, 12, 1).ms(), corrected, env).state
        assertEquals(UserContextType.LUNCH, s1.userContext)
    }

    @Test
    fun `boundary novo no presente e processado no mesmo instante`() {
        val now = at(MONDAY, 12, 20).ms()
        val before = EventContextTimeline(listOf(ContextSpan(UserContextType.COMMUTING, at(MONDAY, 12, 5).ms(), null))) { UserContextType.HOME }
        val s0 = HoodieSimulator.advance(HoodieSimulator.initial(at(MONDAY, 12, 5).ms(), before, env), now - MINUTE_MS, before, env).state
        assertEquals(HoodieActivity.COMMUTING, s0.activity)
        val after = EventContextTimeline(
            listOf(
                ContextSpan(UserContextType.COMMUTING, at(MONDAY, 12, 5).ms(), now),
                ContextSpan(UserContextType.LUNCH, now, null),
            ),
        ) { UserContextType.HOME }
        val s1 = HoodieSimulator.advance(s0, now, after, env).state
        assertEquals(UserContextType.LUNCH, s1.userContext)
        assertTrue(s1.activity != HoodieActivity.COMMUTING)
    }
}
