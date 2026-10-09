package com.hoodie.app.engine

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.engine.context.AskedQuestion
import com.hoodie.app.engine.context.ConfirmationPolicy
import com.hoodie.app.engine.context.ConfirmationRecord
import com.hoodie.app.engine.context.ContextDecision
import com.hoodie.app.engine.context.ContextInput
import com.hoodie.app.engine.context.ContextScorer
import com.hoodie.app.engine.context.ContextSignal
import com.hoodie.app.engine.routine.RoutineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ContextEngineRulesTest {

    private fun input(signal: ContextSignal, now: java.time.ZonedDateTime, confirmations: List<ConfirmationRecord> = emptyList(), dayOff: Boolean = false) =
        ContextInput(signal, now, officeRoutine, dayOff, null, confirmations)

    @Test
    fun `CT-CONTEXT-001 segunda 08h30 entrando no trabalho vira WORK com alta confianca`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(WORK_PLACE), at(MONDAY, 8, 30)))
        assertEquals(UserContextType.WORK, c.type)
        assertEquals(ContextDecision.APPLY, c.decision)
        assertTrue(c.score >= 90)
    }

    @Test
    fun `CT-CONTEXT-002 saida do trabalho as 12h05 aceita LUNCH provisoriamente sem pergunta`() {
        val c = ContextScorer.score(input(ContextSignal.Exit(WORK_PLACE), at(MONDAY, 12, 5)))
        assertEquals(UserContextType.LUNCH, c.type)
        assertEquals(ContextDecision.APPLY, c.decision)
        // Após o tempo mínimo fora, confiança 80 permanece provisória e sem interrupção.
        val later = ContextScorer.score(input(ContextSignal.Exit(WORK_PLACE, minutesOutside = 15), at(MONDAY, 12, 20)))
        assertEquals(80, later.score)
        assertEquals(ContextDecision.APPLY, later.decision)
    }

    @Test
    fun `CT-CONTEXT-003 domingo 09h no trabalho permanece desconhecido com 40 por cento`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(WORK_PLACE), at(SUNDAY, 9)))
        assertEquals(UserContextType.WORK, c.type)
        assertEquals(ContextDecision.UNKNOWN, c.decision)
    }

    @Test
    fun `dia marcado como folga tambem permanece desconhecido sem evidencia suficiente`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(WORK_PLACE), at(MONDAY, 9), dayOff = true))
        assertEquals(ContextDecision.UNKNOWN, c.decision)
    }

    @Test
    fun `confirmacoes repetidas de almoco classificam automaticamente sem perguntar`() {
        val history = listOf(MONDAY - 7, MONDAY - 6, MONDAY - 5).map { d ->
            ConfirmationRecord(UserContextType.LUNCH, null, DayOfWeek.MONDAY, 12 * 60 + 8, true, at(MONDAY, 12).ms() - (MONDAY - d) * 86_400_000L)
        }
        val c = ContextScorer.score(input(ContextSignal.Exit(WORK_PLACE), at(MONDAY, 12, 5), history))
        assertEquals(UserContextType.LUNCH, c.type)
        assertEquals(ContextDecision.APPLY, c.decision)
    }

    @Test
    fun `saida de casa vira deslocamento`() {
        val c = ContextScorer.score(input(ContextSignal.Exit(HOME_PLACE), at(MONDAY, 8, 3)))
        assertEquals(UserContextType.COMMUTING, c.type)
        assertEquals(ContextDecision.APPLY, c.decision)
    }

    @Test
    fun `segunda visita a academia classifica sem perguntar`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(GYM_PLACE), at(MONDAY, 18, 10)))
        assertEquals(UserContextType.GYM, c.type)
        assertEquals(ContextDecision.APPLY, c.decision)
    }

    @Test
    fun `restaurante inferido e almoco somente dentro da rotina`() {
        val restaurant = WORK_PLACE.copy(id = 90, type = com.hoodie.app.core.model.PlaceType.RESTAURANT, name = "Restaurante")
        listOf(
            Triple(12, 0, UserContextType.LUNCH),
            Triple(12, 45, UserContextType.LUNCH),
            Triple(16, 0, UserContextType.DINING),
            Triple(20, 0, UserContextType.DINING),
        ).forEach { (hour, minute, expected) ->
            val actual = ContextScorer.score(input(ContextSignal.Enter(restaurant), at(MONDAY, hour, minute))).type
            assertEquals("restaurant at $hour:$minute", expected, actual)
        }
    }

    @Test
    fun `cooldown limita perguntas de transporte e confirmacao de contexto fica passiva`() {
        val now = at(MONDAY, 15).ms()
        val one = listOf(AskedQuestion(QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.WORK, now - 30 * MINUTE_MS))
        assertFalse(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.WORK, one))
        assertTrue(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.LUNCH, one))
        assertFalse(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.CONFIRM_CONTEXT, UserContextType.WORK, emptyList()))
        val retiredContextQuestions = (1..HoodieConfig.MAX_QUESTIONS_PER_DAY).map {
            AskedQuestion(QuestionKind.CONFIRM_CONTEXT, UserContextType.WORK, now - it * MINUTE_MS)
        }
        assertTrue("retired context confirmations do not consume the transport question budget",
            ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.SELECT_TRANSPORT_MODE, null, retiredContextQuestions))
        val many = (1..4).map { AskedQuestion(QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.GYM, now - it * 2 * 60 * MINUTE_MS) }
        assertFalse(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.WORK, many))
    }

    @Test
    fun `rotina provavel sem localizacao`() {
        assertEquals(UserContextType.WORK, RoutineEngine.probableContext(at(MONDAY, 10), officeRoutine, false))
        assertEquals(UserContextType.LUNCH, RoutineEngine.probableContext(at(MONDAY, 12, 30), officeRoutine, false))
        assertEquals(UserContextType.HOME, RoutineEngine.probableContext(at(MONDAY, 20), officeRoutine, false))
        assertEquals(UserContextType.HOME, RoutineEngine.probableContext(at(SATURDAY, 10), officeRoutine, false))
        assertEquals(UserContextType.HOME, RoutineEngine.probableContext(at(MONDAY, 10), officeRoutine, dayOff = true))
    }
}
