package com.hoodie.app.pixel.restaurant

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcIntent
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantNpcRitualTest {
    @Test fun `reacao a comida de Hoodie e opcional e unica`() {
        val brain = RestaurantNpcDirector.brain(
            SceneEnv(DayPeriod.DAY, 12 * 60, daySeed = 12, flags = setOf(SceneFlag.FOOD_SERVED)),
        )
        val reactions = mutableListOf<Long>()
        var at = 0L
        while (at <= 10 * 60_000L) {
            val state = brain.stateAt(at)
            if (state.currentIntent == RestaurantNpcIntent.REACT_TO_HOODIE && reactions.lastOrNull() != state.intentStartedAt) {
                reactions += state.intentStartedAt
            }
            at = state.nextDecisionAt.coerceAtLeast(at + 1)
        }
        assertTrue("reação única ou nenhuma", reactions.size <= 1)
        reactions.singleOrNull()?.let { start -> assertEquals(start, brain.stateAt(start).intentStartedAt) }
    }

    @Test fun `tempo de comer permanece perto da meta durante a fase de refeicao`() {
        val brain = RestaurantNpcDirector.brain(SceneEnv(DayPeriod.DAY, 12 * 60, daySeed = 12))
        val limit = 5 * 60_000L
        var at = 0L
        var eaten = 0L
        var mealStartedAt: Long? = null
        while (at < limit) {
            val state = brain.stateAt(at)
            val end = state.nextDecisionAt.coerceAtLeast(at + 1).coerceAtMost(limit)
            if (state.currentIntent == RestaurantNpcIntent.EAT) {
                if (mealStartedAt == null) mealStartedAt = at
                eaten += end - at
            }
            at = end
        }
        val ratio = eaten.toFloat() / (limit - requireNotNull(mealStartedAt))
        assertTrue("proporção de comer = $ratio", ratio in 0.35f..0.50f)
    }
}
