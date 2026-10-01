package com.hoodie.app.engine

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
    fun `CT-CONTEXT-002 saida do trabalho as 12h05 gera candidato LUNCH e pergunta`() {
        val c = ContextScorer.score(input(ContextSignal.Exit(WORK_PLACE), at(MONDAY, 12, 5)))
        assertEquals(UserContextType.LUNCH, c.type)
        assertEquals(ContextDecision.APPLY_AND_ASK, c.decision)
        // Após o tempo mínimo fora, confiança 80 → continua perguntando (não infere sozinho).
        val later = ContextScorer.score(input(ContextSignal.Exit(WORK_PLACE, minutesOutside = 15), at(MONDAY, 12, 20)))
        assertEquals(80, later.score)
        assertEquals(ContextDecision.APPLY_AND_ASK, later.decision)
    }

    @Test
    fun `CT-CONTEXT-003 domingo 09h no trabalho pede confirmacao`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(WORK_PLACE), at(SUNDAY, 9)))
        assertEquals(UserContextType.WORK, c.type)
        assertEquals(ContextDecision.APPLY_AND_ASK, c.decision)
    }

    @Test
    fun `dia marcado como folga tambem pede confirmacao no trabalho`() {
        val c = ContextScorer.score(input(ContextSignal.Enter(WORK_PLACE), at(MONDAY, 9), dayOff = true))
        assertEquals(ContextDecision.APPLY_AND_ASK, c.decision)
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
    fun `cooldown limita perguntas por dia e por contexto`() {
        val now = at(MONDAY, 15).ms()
        val one = listOf(AskedQuestion(QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, now - 30 * MINUTE_MS))
        assertFalse(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, one))
        assertTrue(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.CONFIRM_CONTEXT, UserContextType.WORK, one))
        val many = (1..4).map { AskedQuestion(QuestionKind.CONFIRM_CONTEXT, UserContextType.GYM, now - it * 2 * 60 * MINUTE_MS) }
        assertFalse(ConfirmationPolicy.canAsk(now, ZONE, QuestionKind.CONFIRM_CONTEXT, UserContextType.WORK, many))
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
