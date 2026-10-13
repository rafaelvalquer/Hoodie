package com.hoodie.app.engine.routine

import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.domain.routine.RoutineEventType.*
import com.hoodie.app.domain.routine.RoutineValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EffectiveRoutineResolverTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val saturday = LocalDate.of(2026, 10, 10)
    private val office = Routine(workMode = WorkMode.OFFICE) // 08:30–17:30, almoço 12:00–13:00, seg–sex
    private val sleep = SleepSchedule(7 * 60, 23 * 60)
    private fun hm(h: Int, m: Int = 0) = h * 60 + m
    private fun slot(type: RoutineEventType, minute: Int, group: String = "WEEKDAY", conf: Float = .85f, dev: Int = 10, n: Int = 8) =
        LearnedRoutineSlot(group, type, minute, dev, n, ConfidenceScore(conf))

    private fun resolve(vararg slots: LearnedRoutineSlot, date: LocalDate = monday, routine: Routine = office, s: SleepSchedule = sleep, enabled: Boolean = true) =
        EffectiveRoutineResolver.resolve(date, routine, s, slots.toList(), enabled)

    @Test
    fun `sem padroes ou desligado segue a rotina manual`() {
        val none = resolve()
        assertEquals(office, none.routine); assertEquals(sleep, none.sleep)
        assertEquals(0, none.learnedCount)
        val off = resolve(slot(WORK_START, hm(9)), enabled = false)
        assertEquals(office, off.routine)
        assertFalse(off.learningEnabled)
    }

    @Test
    fun `padrao confiavel ajusta cada horario e registra a origem`() {
        val e = resolve(slot(WORK_START, hm(8, 50)), slot(LUNCH_START, hm(12, 40)), slot(LUNCH_END, hm(13, 30)), slot(WORK_END, hm(18, 10)))
        assertEquals(hm(8, 50), e.routine.startMinute)
        assertEquals(hm(12, 40), e.routine.lunchStartMinute)
        assertEquals(hm(13, 30), e.routine.lunchEndMinute)
        assertEquals(hm(18, 10), e.routine.endMinute)
        val o = e.origin(LUNCH_START)!!
        assertEquals(RoutineValueSource.LEARNED, o.source)
        assertEquals(hm(12), o.manualMinute); assertEquals(hm(12, 40), o.learnedMinute)
        assertEquals(4, e.learnedCount)
    }

    @Test
    fun `ajuste limitado a 90 minutos do configurado`() {
        val e = resolve(slot(WORK_START, hm(11)))
        assertEquals(hm(10), e.routine.startMinute)
        assertEquals(RoutineValueSource.LEARNED_LIMITED, e.origin(WORK_START)!!.source)
        assertEquals(hm(11), e.origin(WORK_START)!!.learnedMinute)
    }

    @Test
    fun `padrao pouco confiavel ou disperso nao ajusta`() {
        assertEquals(office.startMinute, resolve(slot(WORK_START, hm(9), conf = .5f)).routine.startMinute)
        assertEquals(office.startMinute, resolve(slot(WORK_START, hm(9), dev = 60)).routine.startMinute)
    }

    @Test
    fun `padrao do dia da semana vence o do grupo e fim de semana usa o proprio grupo`() {
        val e = resolve(slot(WORK_START, hm(9), group = "WEEKDAY"), slot(WORK_START, hm(8), group = "MONDAY"))
        assertEquals(hm(8), e.routine.startMinute)
        assertEquals("WEEKDAY", e.dayGroup)
        // Sábado ignora o padrão de dia útil e usa o de sábado.
        val sat = resolve(slot(WAKE, hm(7, 30), group = "WEEKDAY"), slot(WAKE, hm(9), group = "SATURDAY"), date = saturday)
        assertEquals(hm(8, 30), sat.sleep.wakeMinute) // 9:00 limitado a +90 de 7:00
        assertEquals("SATURDAY", sat.dayGroup)
    }

    @Test
    fun `ordem do dia preservada - ajuste que quebra a ordem volta ao manual`() {
        // Almoço aprendido às 13:10 passaria da volta configurada (13:00).
        val e = resolve(slot(LUNCH_START, hm(13, 10)))
        assertEquals(hm(12), e.routine.lunchStartMinute)
        assertEquals(RoutineValueSource.MANUAL, e.origin(LUNCH_START)!!.source)
        // Almoço com menos de 15 min também não vale: cede o lado menos confiável.
        val short = resolve(slot(LUNCH_START, hm(12, 50), conf = .7f), slot(LUNCH_END, hm(12, 55), conf = .9f))
        assertTrue(short.routine.lunchEndMinute - short.routine.lunchStartMinute >= EffectiveRoutineResolver.MIN_LUNCH_MIN)
        assertEquals(hm(12), short.routine.lunchStartMinute)
    }

    @Test
    fun `sono coerente e acordar antes de entrar no trabalho`() {
        // Dormir aprendido 00:15 (cruza a meia-noite): +75 min a partir de 23:00.
        val late = resolve(slot(RoutineEventType.SLEEP, hm(0, 15)))
        assertEquals(hm(0, 15), late.sleep.sleepMinute)
        // Acordar aprendido às 9:00 seria depois da entrada (8:30): cede.
        val wake = resolve(slot(WAKE, hm(9)))
        assertEquals(hm(7), wake.sleep.wakeMinute)
        // Sono com menos de 4 h: o acordar aprendido cede.
        val tiny = resolve(slot(RoutineEventType.SLEEP, hm(1, 30)), slot(WAKE, hm(5, 0)), routine = Routine(workMode = WorkMode.NONE))
        assertTrue((tiny.sleep.wakeMinute - tiny.sleep.sleepMinute + 1440) % 1440 >= 4 * 60)
    }

    @Test
    fun `sem trabalho ou turno que cruza a meia-noite nao mexe no expediente`() {
        val noWork = resolve(slot(WORK_START, hm(9)), routine = Routine(workMode = WorkMode.NONE))
        assertNull(noWork.origin(WORK_START))
        val night = Routine(workMode = WorkMode.OFFICE, startMinute = hm(22), endMinute = hm(6), lunchStartMinute = hm(2), lunchEndMinute = hm(3))
        assertEquals(night, resolve(slot(WORK_START, hm(22, 30)), routine = night).routine)
    }

    @Test
    fun `marcos so aprendidos entram como informacao`() {
        val e = resolve(slot(GYM_START, hm(18, 30)), slot(LEAVE_HOME, hm(7, 50), conf = .4f))
        assertEquals(hm(18, 30), e.origin(GYM_START)!!.effectiveMinute)
        assertNull(e.origin(GYM_START)!!.manualMinute)
        assertNull(e.origin(LEAVE_HOME)) // pouco confiável
        assertEquals(office, e.routine)
    }

    @Test
    fun `diferenca circular pelo caminho mais curto`() {
        assertEquals(45, EffectiveRoutineResolver.circularDelta(hm(23, 30), hm(0, 15)))
        assertEquals(-45, EffectiveRoutineResolver.circularDelta(hm(0, 15), hm(23, 30)))
        assertEquals(40, EffectiveRoutineResolver.circularDelta(hm(12), hm(12, 40)))
    }
}
