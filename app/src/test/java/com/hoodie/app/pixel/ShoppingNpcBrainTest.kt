package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcSpot
import com.hoodie.app.pixel.npc.shopping.ShoppingNavigationGraph
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.ShoppingScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingNpcBrainTest {
    private fun copyImage(source: PixelBuffer) = PixelBuffer(source.width, source.height).also {
        System.arraycopy(source.pixels, 0, it.pixels, 0, source.pixels.size)
    }

    private fun env(seed: Int = 42, period: DayPeriod = DayPeriod.DAY) =
        SceneEnv(period, 9 * 60, variant = seed, daySeed = seed)

    private fun brain(seed: Int = 42) = ShoppingNpcDirector.plan(env(seed)).single().shoppingBrain!!

    @Test fun sameSeedRebuildsTheSameFiveMinuteStoryAndDifferentSeedsVaryIt() {
        val first = brain(42)
        val repeated = brain(42)
        val story = (0L..300_000L step 1_000).map { first.stateAt(it) to first.movementAt(it) }
        val same = (0L..300_000L step 1_000).map { repeated.stateAt(it) to repeated.movementAt(it) }
        assertEquals(story, same)
        val alternatives = (1..16).map { seed -> (0L..180_000L step 5_000).map { brain(seed).stateAt(it).currentIntent } }
        assertTrue("seeds diferentes não alteraram nenhuma história", alternatives.any { it != story.take(alternatives.first().size).map { row -> row.first.currentIntent } })
    }

    @Test fun fiveMinutesIncludeBrowsingProductsListBasketAndCheckoutWithoutRepeatedPicks() {
        val shopper = brain(42)
        val states = (0L..300_000L step 250).map { shopper.stateAt(it) }
        val intents = states.map { it.currentIntent }.toSet()
        assertTrue("pouca variedade: $intents", intents.size >= 8)
        assertTrue(ShoppingNpcIntent.BROWSE_AISLE in intents)
        assertTrue(ShoppingNpcIntent.LOOK_PRODUCT in intents)
        assertTrue(ShoppingNpcIntent.PICK_PRODUCT in intents)
        assertTrue(ShoppingNpcIntent.READ_LABEL in intents)
        assertTrue(ShoppingNpcIntent.PUT_IN_BASKET in intents)
        assertTrue(ShoppingNpcIntent.CHECK_LIST in intents || ShoppingNpcIntent.CHECK_PHONE in intents)
        assertTrue(ShoppingNpcIntent.WALK_TO_OTHER_AISLE in intents)
        assertTrue(ShoppingNpcIntent.WAIT_CHECKOUT in intents)
        assertTrue(ShoppingNpcIntent.PAY in intents)
        states.zipWithNext().forEach { (a, b) ->
            assertTrue(b.basket.itemCount in 0..b.basket.maxItems)
        }
        val transitions = states.map { it.currentIntent }.fold(mutableListOf<ShoppingNpcIntent>()) { distinct, intent ->
            if (distinct.lastOrNull() != intent) distinct += intent
            distinct
        }
        assertTrue(transitions.zipWithNext().all { (a, b) ->
            a != ShoppingNpcIntent.PICK_PRODUCT || b != ShoppingNpcIntent.PICK_PRODUCT
        })
        val picks = transitions.withIndex().filter { it.value == ShoppingNpcIntent.PICK_PRODUCT }.map { it.index }
        assertTrue(picks.size >= 2)
        assertTrue(picks.zipWithNext().all { (a, b) -> b - a > 1 })
        assertTrue(states.any { it.basket.itemCount > 0 })
    }

    @Test fun shoppingRoutesUseOnlyConnectedPointsAndStayWithinSceneBounds() {
        ShoppingNpcSpot.entries.forEach { from -> ShoppingNpcSpot.entries.forEach { to ->
            val route = ShoppingNavigationGraph.route(from, to)
            assertEquals(from, route.first())
            assertEquals(to, route.last())
            assertTrue(route.all { point ->
                val p = ShoppingNavigationGraph.spots.getValue(point)
                p.x in -32..239 && p.floorY in 0..319
            })
            assertTrue(route.zipWithNext().all { (a, b) -> ShoppingNavigationGraph.isAdjacent(a, b) })
        } }
        val crossing = ShoppingNavigationGraph.route(ShoppingNpcSpot.AISLE_A_MIDDLE, ShoppingNpcSpot.AISLE_B_MIDDLE)
        assertTrue(ShoppingNpcSpot.CENTER in crossing)
    }

    @Test fun contextualSpeechRespectsTheMinimumCooldown() {
        val shopper = (1..128).map(::brain).firstOrNull { candidate ->
            (0L..300_000L step 500).any { candidate.stateAt(it).speechLine != null }
        } ?: error("nenhuma seed produziu fala contextual")
        val events = mutableListOf<Long>()
        var speaking = false
        for (time in 0L..300_000L step 100) {
            val nowSpeaking = shopper.stateAt(time).speechLine != null
            if (nowSpeaking && !speaking) events += time
            speaking = nowSpeaking
        }
        assertTrue(events.isNotEmpty())
        assertTrue(events.zipWithNext().all { (a, b) -> b - a >= 45_000L })
    }

    @Test fun everySeedCompletesItsShoppingTripBeforeTheFiveMinuteReturnCycle() {
        (0..64).forEach { seed ->
            val shopper = brain(seed)
            assertEquals("trip seed $seed", ShoppingNpcIntent.IDLE, shopper.stateAt(299_999).currentIntent)
            assertEquals("trip seed $seed", -30, shopper.movementAt(299_999).x)
        }
    }

    @Test fun checkoutAndShelfChangesAreNpcLocalAndRenderWithTheMarket() {
        val env = env()
        val scene = ShoppingScene()
        val shopper = scene.ambientNpcs(env).single()
        val brain = shopper.shoppingBrain!!
        val payAt = (0L..300_000L step 100).first { brain.stateAt(it).currentIntent == ShoppingNpcIntent.PAY }
        val pickAt = (0L..300_000L step 100).first { brain.stateAt(it).currentIntent == ShoppingNpcIntent.READ_LABEL }
        val payVisual = brain.visualStateAt(payAt)
        val shelfVisual = brain.visualStateAt(pickAt)
        assertTrue(payVisual.checkoutActive)
        assertTrue(shelfVisual.productRemovedA || shelfVisual.productRemovedB)
        assertTrue(env.flags.isEmpty())
        assertFalse(SceneFlag.CHECKOUT_ACTIVE in env.flags)
        val renderer = com.hoodie.app.pixel.renderer.SceneRenderer()
        val withShopper = renderer.renderAmbientNpc(scene, env, payAt).pixels.copyOf()
        val withoutShopper = renderer.renderEmpty(scene, env, payAt, includeAmbientNpcs = false).pixels
        assertTrue(withShopper.indices.any { withShopper[it] != withoutShopper[it] })
        assertEquals(240 * 320, withShopper.size)
    }

}
