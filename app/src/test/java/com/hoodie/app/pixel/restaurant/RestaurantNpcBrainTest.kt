package com.hoodie.app.pixel.restaurant

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.restaurant.RestaurantMealState
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcIntent
import com.hoodie.app.pixel.npc.restaurant.RestaurantSpeechLibrary
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantNpcBrainTest {
    private fun brain(seed: Int = 12, variant: Int = 0) = RestaurantNpcDirector.brain(
        SceneEnv(DayPeriod.DAY, 12 * 60, variant = variant, daySeed = seed),
    )

    @Test fun `cinco minutos produzem refeicao variada com pausas e sem fala em sequencia`() {
        val brain = brain()
        val intents = mutableListOf<RestaurantNpcIntent>()
        var lastTalkStart = -1L
        var at = 0L
        while (at <= 5 * 60_000L) {
            val state = brain.stateAt(at)
            if (intents.lastOrNull() != state.currentIntent) intents += state.currentIntent
            if (state.currentIntent == RestaurantNpcIntent.TALK) {
                assertTrue("fala menor que cooldown", state.intentStartedAt >= lastTalkStart + 45_000L || lastTalkStart < 0)
                lastTalkStart = state.intentStartedAt
            }
            at = state.nextDecisionAt.coerceAtLeast(at + 1)
        }
        assertTrue(intents.size >= 5)
        assertTrue(intents.contains(RestaurantNpcIntent.EAT))
        assertTrue(intents.contains(RestaurantNpcIntent.IDLE))
        assertFalse(intents.zipWithNext().any { (a, b) -> a == RestaurantNpcIntent.TALK && b == RestaurantNpcIntent.TALK })
        assertFalse(intents.zipWithNext().any { (a, b) -> a == RestaurantNpcIntent.CHECK_PHONE && b == RestaurantNpcIntent.CHECK_PHONE })
        assertFalse(intents.zipWithNext().any { (a, b) -> a == RestaurantNpcIntent.DRINK && b == RestaurantNpcIntent.DRINK })
    }

    @Test fun `historias reproduzem seed e variam entre dias`() {
        fun history(seed: Int, variant: Int) = (0..300 step 3).map { second ->
            val state = brain(seed, variant).stateAt(second * 1_000L)
            state.currentIntent to state.intentStartedAt
        }
        assertEquals(history(12, 0), history(12, 0))
        assertNotEquals(history(12, 0), history(13, 0))
        assertNotEquals(history(12, 0), history(12, 1))
    }

    @Test fun `cliente so sai depois de terminar a refeicao`() {
        val brain = brain()
        var at = 0L
        while (at < 10 * 60_000L) {
            val state = brain.stateAt(at)
            if (state.currentIntent == RestaurantNpcIntent.LEAVE) assertEquals(RestaurantMealState.FINISHED, state.mealState)
            at = state.nextDecisionAt.coerceAtLeast(at + 1)
        }
    }

    @Test fun `animacoes sentadas tem pose e prop coerentes com suas intencoes`() {
        val brain = brain()
        val targets = mapOf(
            RestaurantNpcIntent.EAT to NpcAnimation.SIT_EAT,
            RestaurantNpcIntent.DRINK to NpcAnimation.SIT_DRINK,
            RestaurantNpcIntent.CHECK_PHONE to NpcAnimation.SIT_PHONE,
            RestaurantNpcIntent.READ_MENU to NpcAnimation.SIT_READ_MENU,
            RestaurantNpcIntent.LOOK_AROUND to NpcAnimation.SIT_LOOK,
        )
        for (second in 0..300) {
            val state = brain.stateAt(second * 1_000L)
            targets[state.currentIntent]?.let { expected ->
                assertEquals(expected, brain.movementAt(second * 1_000L, state).animation)
            }
            if (state.mealState != RestaurantMealState.WAITING) assertFalse(brain.tableStateAt(second * 1_000L, state).menuOpen)
        }
    }

    @Test fun `falas combinam fase da refeicao com horario`() {
        val waiting = RestaurantSpeechLibrary.line(RestaurantMealState.WAITING, 12 * 60, 1, 0)
        val eating = RestaurantSpeechLibrary.line(RestaurantMealState.EATING, 12 * 60, 1, 0)
        val finished = RestaurantSpeechLibrary.line(RestaurantMealState.FINISHED, 12 * 60, 1, 0)
        assertEquals("DEMOROU HOJE.", waiting)
        assertEquals("TÁ MUITO BOM.", eating)
        assertEquals("TÁ CHEIO HOJE.", finished)
    }
}
