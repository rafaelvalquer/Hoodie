package com.hoodie.app.integration

import com.hoodie.app.core.database.LearnedRoutineSlotEntity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.data.repository.EffectiveRoutineRepository
import com.hoodie.app.domain.routine.RoutineValueSource
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.presentation.screens.routine.learnedHintText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rotina efetiva de ponta a ponta: o padrão aprendido (entrada às 9:30 nos dias úteis)
 * muda o que o contexto decide sem localização, e o interruptor volta ao manual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EffectiveRoutineIntegrationTest {
    private lateinit var g: TestGraph
    private lateinit var repo: EffectiveRoutineRepository

    @Before
    fun setUp() = runBlocking {
        g = TestGraph(at(MONDAY, 9, 10).ms())
        g.setRoutine(officeRoutine) // entrada 08:30
        g.db.intelligenceDao().replaceRoutineSlots(listOf(
            LearnedRoutineSlotEntity(dayGroup = "WEEKDAY", type = "WORK_START", medianMinute = 9 * 60 + 30, deviationMinutes = 10, sampleCount = 9, confidence = .85f, updatedAt = 0),
            LearnedRoutineSlotEntity(dayGroup = "WEEKDAY", type = "GYM_START", medianMinute = 18 * 60 + 30, deviationMinutes = 15, sampleCount = 6, confidence = .7f, updatedAt = 0),
        ))
        repo = EffectiveRoutineRepository(g.routines, g.settings, g.db.intelligenceDao(), g.clock)
    }

    @After fun tearDown() = g.close()

    @Test
    fun `repositorio aplica o padrao aprendido e mostra a origem`() = runBlocking {
        val today = repo.today()
        assertEquals(9 * 60 + 30, today.routine.startMinute)
        assertEquals(RoutineValueSource.LEARNED, today.origin(com.hoodie.app.domain.routine.RoutineEventType.WORK_START)!!.source)
        assertEquals(18 * 60 + 30, today.origin(com.hoodie.app.domain.routine.RoutineEventType.GYM_START)!!.effectiveMinute)
        assertEquals("✨ Entrada aprendido: 09:30 (configurado 08:30)", learnedHintText("Entrada", today, com.hoodie.app.domain.routine.RoutineEventType.WORK_START))
        // A Home observa a mesma rotina.
        assertEquals(today.routine, repo.observeToday().first().routine)
    }

    @Test
    fun `sem localizacao o contexto provavel segue a rotina efetiva`() = runBlocking {
        // 9:10 de segunda: pela rotina crua já seria trabalho; pelo aprendido (9:30) ainda é casa.
        assertEquals(UserContextType.WORK, RoutineEngine.probableContext(g.clock.now(), officeRoutine, false))
        g.engine.applyRoutineFallbackIfNeeded()
        assertEquals(UserContextType.HOME, g.contextDao.current()!!.type)
    }

    @Test
    fun `interruptor desligado volta a rotina manual`() = runBlocking {
        g.settings.setLearnedRoutineEnabled(false)
        val today = repo.today()
        assertEquals(officeRoutine.startMinute, today.routine.startMinute)
        assertNull(learnedHintText("Entrada", today, com.hoodie.app.domain.routine.RoutineEventType.WORK_START))
        g.engine.applyRoutineFallbackIfNeeded()
        assertEquals(UserContextType.WORK, g.contextDao.current()!!.type)
        assertTrue(!today.learningEnabled)
    }
}
