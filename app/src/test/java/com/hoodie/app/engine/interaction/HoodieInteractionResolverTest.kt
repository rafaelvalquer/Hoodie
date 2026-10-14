package com.hoodie.app.engine.interaction

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HoodieInteractionResolverTest {
    private val resolver = HoodieInteractionResolver(interactionRules(), Random(12))
    private fun response(c: InteractionContext, gesture: InteractionGesture = InteractionGesture.TAP) =
        (resolver.resolve(c, gesture, HoodieInteractionHistory(), 1_000) as InteractionDecision.Accepted).response

    @Test fun `specific activity precedes environment and fallback`() {
        val r = response(InteractionContext(SceneId.HOME, animation = AnimationId.WATCH_TV))
        assertTrue(r.ruleId.startsWith("home_tv_"))
        assertEquals(listOf(AnimationId.GLANCE, AnimationId.NOD), r.animations)
    }
    @Test fun `office rules apply to home office and not to ordinary home`() {
        val c = InteractionContext(SceneId.HOME, UserContextType.WORK, HoodieActivity.WORKING, AnimationId.WORK_TYPING)
        assertTrue(response(c).ruleId.startsWith("office_typing_"))
        assertFalse(response(c.copy(userContext = UserContextType.HOME)).ruleId.startsWith("office_"))
    }
    @Test fun `absent NPC and butterfly conditions are never assumed`() {
        val office = InteractionContext(SceneId.OFFICE)
        assertTrue(response(office).ruleId.startsWith("fallback_"))
        assertTrue(response(office.copy(presentElements = setOf(InteractionElement.BULLDOG))).ruleId.startsWith("office_bulldog_"))
        assertTrue(response(InteractionContext(SceneId.LEISURE)).ruleId.startsWith("fallback_"))
        assertTrue(response(InteractionContext(SceneId.LEISURE, presentElements = setOf(InteractionElement.BUTTERFLY))).ruleId.startsWith("leisure_butterfly_"))
    }
    @Test fun `low energy typing can choose tired rule without inventing NPCs`() {
        val c = InteractionContext(SceneId.OFFICE, activity = HoodieActivity.WORKING, animation = AnimationId.WORK_TYPING, energy = 20f)
        assertTrue(response(c).ruleId.startsWith("office_tired_"))
    }
    @Test fun `market and clothing store have different rules and no payment animation`() {
        val market = response(InteractionContext(SceneId.SHOPPING, animation = AnimationId.SHOP_LOOK))
        val store = response(InteractionContext(SceneId.STORE, animation = AnimationId.STORE_BROWSE_RACK))
        assertTrue(market.ruleId.startsWith("shopping_shelves_"))
        assertTrue(store.ruleId.startsWith("store_clothes_"))
        assertFalse(market.animations.contains(AnimationId.SHOP_PAY))
    }
    @Test fun `restaurant waiting or resting takes precedence over generic seated table petting`() {
        val c = InteractionContext(SceneId.RESTAURANT, animation = AnimationId.WAIT_FOOD, seated = true)
        val waiting = response(c, InteractionGesture.LONG_PRESS)
        assertTrue(waiting.ruleId.startsWith("restaurant_resting_"))
        assertEquals(listOf(AnimationId.PET_HEAD, AnimationId.PURR), waiting.animations)
        val eating = response(c.copy(activity = HoodieActivity.EATING, animation = AnimationId.EAT), InteractionGesture.LONG_PRESS)
        assertTrue(eating.ruleId.startsWith("restaurant_table_"))
        assertEquals(listOf(AnimationId.PET_HEAD, AnimationId.SMILE), eating.animations)
    }

    @Test fun `deterministic random produces the same choices`() {
        val c = InteractionContext(SceneId.HOME, animation = AnimationId.WATCH_TV)
        val a = HoodieInteractionResolver(interactionRules(), Random(42))
        val b = HoodieInteractionResolver(interactionRules(), Random(42))
        assertEquals(a.resolve(c, InteractionGesture.TAP, HoodieInteractionHistory(), 0), b.resolve(c, InteractionGesture.TAP, HoodieInteractionHistory(), 0))
    }
    @Test fun `silent long press retains gesture for execution safety checks`() {
        val c = InteractionContext(SceneId.HOME)
        val h = HoodieInteractionHistory()
        val first = (resolver.resolve(c, InteractionGesture.LONG_PRESS, h, 1_000) as InteractionDecision.Accepted).response
        h.acceptGesture(1_000)
        h.remember(first, 1_000)
        val silent = (resolver.resolve(c, InteractionGesture.LONG_PRESS, h, 2_000) as InteractionDecision.Accepted).response
        assertNull(silent.text)
        assertEquals(InteractionGesture.LONG_PRESS, silent.gesture)
        val exhausted = interactionRules().filter { it.id == first.ruleId }
        val exhaustedResolver = HoodieInteractionResolver(exhausted, Random(12))
        val noFreshPhrase = (exhaustedResolver.resolve(c, InteractionGesture.LONG_PRESS, h, 12_000) as InteractionDecision.Accepted).response
        assertNull(noFreshPhrase.text)
        assertEquals(InteractionGesture.LONG_PRESS, noFreshPhrase.gesture)
    }

    @Test fun `repeated taps are small silent reactions and do not queue speech`() {
        val h = HoodieInteractionHistory()
        val c = InteractionContext(SceneId.HOME, animation = AnimationId.WATCH_TV)
        val first = (resolver.resolve(c, InteractionGesture.TAP, h, 1_000) as InteractionDecision.Accepted).response
        h.acceptGesture(1_000)
        h.remember(first, 1_000)
        assertTrue(resolver.resolve(c, InteractionGesture.TAP, h, 1_100) is InteractionDecision.Cooldown)
        val silent = (resolver.resolve(c, InteractionGesture.TAP, h, 2_000) as InteractionDecision.Accepted).response
        assertNull(silent.text)
        assertEquals(1, silent.animations.size)
        val next = (resolver.resolve(c, InteractionGesture.TAP, h, 12_000) as InteractionDecision.Accepted).response
        assertNotEquals(first.text, next.text)
    }
}
