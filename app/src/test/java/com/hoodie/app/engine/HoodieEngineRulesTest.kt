package com.hoodie.app.engine

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.engine.hoodie.DecisionInput
import com.hoodie.app.engine.hoodie.HoodieDecisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HoodieEngineRulesTest {

    private fun input(ctx: UserContextType, time: java.time.ZonedDateTime, needs: Needs = Needs(), previous: HoodieActivity? = null, routine: Routine = officeRoutine) =
        DecisionInput(time, ctx, needs, previous, routine, SleepSchedule(), dayOff = false)

    @Test
    fun `CT-HOODIE-001 usuario no trabalho - Hoodie pode trabalhar e trabalhar domina`() {
        val w = HoodieDecisionEngine.weights(input(UserContextType.WORK, at(MONDAY, 10)))
        assertTrue(HoodieActivity.WORKING in w)
        assertEquals(HoodieActivity.WORKING, w.maxBy { it.value }.key)
        // Mas não copia 100%: há outras opções (vida própria).
        assertTrue(w.size > 1)
    }

    @Test
    fun `CT-HOODIE-002 energia 5 nao inicia atividade longa`() {
        val tired = Needs(energy = 5)
        listOf(UserContextType.WORK, UserContextType.HOME, UserContextType.GYM).forEach { ctx ->
            val w = HoodieDecisionEngine.weights(input(ctx, at(SATURDAY, 15), tired))
            assertFalse("$ctx -> $w", w.keys.any { it.isLong })
        }
        repeat(50) { seed ->
            val d = HoodieDecisionEngine.decide(input(UserContextType.WORK, at(MONDAY, 10), tired), Random(seed))
            assertFalse(d.activity.isLong)
        }
    }

    @Test
    fun `CT-HOODIE-003 23h40 em casa aumenta muito a chance de dormir`() {
        val w = HoodieDecisionEngine.weights(input(UserContextType.HOME, at(MONDAY, 23, 40)))
        val total = w.values.sum().toDouble()
        assertTrue((w[HoodieActivity.SLEEPING] ?: 0) / total > 0.8)
        // E o sono dura até a hora de acordar.
        val d = HoodieDecisionEngine.decide(input(UserContextType.HOME, at(MONDAY, 23, 40)), Random(1))
        if (d.activity == HoodieActivity.SLEEPING) assertEquals((7 * 60 + 20) * 60_000L, d.durationMs)
    }

    @Test
    fun `sabado em casa - vida autonoma com videogame leitura cafe`() {
        val w = HoodieDecisionEngine.weights(input(UserContextType.HOME, at(SATURDAY, 10, 32)))
        assertEquals(HoodieActivity.GAMING, w.maxBy { it.value }.key)
        assertTrue(listOf(HoodieActivity.READING, HoodieActivity.COFFEE, HoodieActivity.CLEANING, HoodieActivity.WALKING).all { it in w })
    }

    @Test
    fun `acorda depois de dormir e toma cafe da manha`() {
        assertEquals(mapOf(HoodieActivity.WAKING_UP to 100), HoodieDecisionEngine.weights(input(UserContextType.HOME, at(MONDAY, 7), previous = HoodieActivity.SLEEPING)))
        assertEquals(mapOf(HoodieActivity.BREAKFAST to 100), HoodieDecisionEngine.weights(input(UserContextType.HOME, at(MONDAY, 7, 10), previous = HoodieActivity.WAKING_UP)))
    }

    @Test
    fun `deslocamento sempre vira deslocamento e home office conta como trabalho`() {
        assertEquals(setOf(HoodieActivity.COMMUTING), HoodieDecisionEngine.weights(input(UserContextType.COMMUTING, at(MONDAY, 8))).keys)
        val ho = Routine(workMode = WorkMode.HOME_OFFICE)
        val w = HoodieDecisionEngine.weights(input(UserContextType.HOME, at(MONDAY, 10), routine = ho))
        assertEquals(HoodieActivity.WORKING, w.maxBy { it.value }.key)
    }

    @Test
    fun `decisao e deterministica para a mesma seed`() {
        val i = input(UserContextType.HOME, at(SATURDAY, 15))
        assertEquals(HoodieDecisionEngine.decide(i, Random(42)), HoodieDecisionEngine.decide(i, Random(42)))
    }
}
