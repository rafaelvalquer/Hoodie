package com.hoodie.app.engine

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.util.Geo
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.dialogue.DialogueInput
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.engine.memory.MilestoneRules
import com.hoodie.app.engine.timeline.ActivitySpan
import com.hoodie.app.engine.timeline.ContextSpan
import com.hoodie.app.engine.timeline.DailySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MiscEngineTest {

    private val dialogues = DialogueEngine(DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText()))

    private fun dialogueInput(ctx: UserContextType, activity: HoodieActivity, minutes: Long = 30, overtime: Boolean = false) =
        DialogueInput(ctx, activity, minutes, 14, weekend = false, overtime = overtime, energy = 70, arrivedEarly = false)

    @Test
    fun `dialogo de dia longo no trabalho apos 10 horas`() {
        val text = dialogues.pick(dialogueInput(UserContextType.WORK, HoodieActivity.WORKING, minutes = 620), Random(1))
        assertTrue(text in setOf("Hoje o expediente está longo.", "Dia longo..."))
    }

    @Test
    fun `dialogo de cafe no trabalho`() {
        assertNotNull(dialogues.pick(dialogueInput(UserContextType.WORK, HoodieActivity.COFFEE), Random(1)))
    }

    @Test
    fun `ids de dialogo sao unicos`() {
        val rules = DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText())
        assertEquals(rules.size, rules.map { it.id }.toSet().size)
    }

    @Test
    fun `sem regra compativel nao fala`() {
        val engine = DialogueEngine(emptyList())
        assertNull(engine.pick(dialogueInput(UserContextType.WORK, HoodieActivity.WORKING), Random(1)))
    }

    @Test
    fun `resumo diario recorta duracoes na janela do dia`() {
        val dayStart = at(MONDAY, 0).ms(); val dayEnd = at(MONDAY + 1, 0).ms()
        val ctx = listOf(
            ContextSpan(UserContextType.HOME, at(MONDAY - 1, 20).ms(), at(MONDAY, 8).ms()),
            ContextSpan(UserContextType.WORK, at(MONDAY, 8, 30).ms(), at(MONDAY, 12).ms()),
        )
        val acts = listOf(
            ActivitySpan(HoodieActivity.COFFEE, at(MONDAY, 9).ms(), at(MONDAY, 9, 10).ms()),
            ActivitySpan(HoodieActivity.COFFEE, at(MONDAY, 10).ms(), at(MONDAY, 10, 8).ms()),
        )
        val s = DailySummaryCalculator.compute(ctx, acts, null, dayStart, dayEnd, at(MONDAY, 13).ms())
        assertEquals(8 * 60 * MINUTE_MS, s.userTotals.first { it.first == UserContextType.HOME }.second)
        assertEquals(210 * MINUTE_MS, s.userTotals.first { it.first == UserContextType.WORK }.second)
        assertEquals(2, s.coffees)
    }

    @Test
    fun `marcos por dias juntos e por contexto`() {
        assertEquals(listOf(Milestone.WEEK_1, Milestone.DAYS_10), MilestoneRules.forDaysTogether(12))
        assertEquals(Milestone.FIRST_GYM, MilestoneRules.forContext(UserContextType.GYM, UserContextType.COMMUTING))
        assertEquals(Milestone.FIRST_HOME_RETURN, MilestoneRules.forContext(UserContextType.HOME, UserContextType.COMMUTING))
        assertNull(MilestoneRules.forContext(UserContextType.HOME, null))
    }

    @Test
    fun `CT-GEO distancia e parsing de coordenadas`() {
        val d = Geo.distanceMeters(-23.5505, -46.6333, -23.5515, -46.6333)
        assertTrue(d in 100.0..120.0)
        assertEquals(-23.5505 to -46.6333, Geo.parse("-23.5505, -46.6333"))
        assertNull(Geo.parse("abc"))
        assertNull(Geo.parse("100, 10"))
    }
}
