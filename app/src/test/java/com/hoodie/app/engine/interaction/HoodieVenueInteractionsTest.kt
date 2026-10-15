package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.npc.venue.CinemaPhase
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HoodieVenueInteractionsTest {
    private val rules = interactionRules()
    private val resolver = HoodieInteractionResolver(rules, Random(4), venuesEnabled = true)

    private fun context(rule: InteractionRule): InteractionContext {
        val animation = when (rule.condition) {
            InteractionCondition.BAKERY_CHOOSE -> AnimationId.BAKERY_CHOOSE
            InteractionCondition.BAKERY_WAIT -> AnimationId.BAKERY_WAIT_ORDER
            InteractionCondition.BAKERY_COFFEE -> AnimationId.COFFEE_SIT
            InteractionCondition.BAKERY_REST -> AnimationId.BAKERY_PEOPLE_WATCH
            InteractionCondition.MALL_WINDOW -> AnimationId.MALL_WINDOW_SHOP
            InteractionCondition.MALL_REST -> AnimationId.LEISURE_BENCH
            InteractionCondition.BEACH_SEA -> AnimationId.BEACH_LOOK_SEA
            InteractionCondition.BEACH_LOUNGE -> AnimationId.BEACH_LOUNGE
            InteractionCondition.BEACH_ICE_CREAM -> AnimationId.BEACH_ICE_CREAM
            InteractionCondition.BEACH_SAND, InteractionCondition.VENUE_WAIT, InteractionCondition.VENUE_STATIONARY -> AnimationId.IDLE
            InteractionCondition.CINEMA_WAIT, InteractionCondition.CINEMA_WATCH, InteractionCondition.CINEMA_AFTER -> AnimationId.CINEMA_WATCH
            InteractionCondition.CINEMA_REACT -> AnimationId.CINEMA_REACT
            InteractionCondition.CINEMA_POPCORN -> AnimationId.CINEMA_EAT_POPCORN
            InteractionCondition.PHARMACY_SHELVES -> AnimationId.SHOP_LOOK
            InteractionCondition.PHARMACY_PRODUCT -> AnimationId.PHARMACY_EXAMINE
            InteractionCondition.HOTEL_CHECK_IN -> AnimationId.HOTEL_CHECK_IN
            InteractionCondition.HOTEL_LOBBY -> AnimationId.LOOK_AROUND
            InteractionCondition.HOTEL_WAIT -> AnimationId.HOTEL_CHECK_WATCH
            InteractionCondition.HOTEL_WATCH -> AnimationId.HOTEL_CHECK_WATCH
            InteractionCondition.HOTEL_REST -> AnimationId.IDLE_SIT
            else -> error("Missing venue fixture ${rule.condition}")
        }
        val flags = when (rule.condition) {
            InteractionCondition.BAKERY_WAIT -> setOf(SceneFlag.ORDER_PENDING)
            InteractionCondition.BAKERY_COFFEE -> setOf(SceneFlag.MUG_IN_HAND)
            InteractionCondition.BEACH_ICE_CREAM -> setOf(SceneFlag.ICE_CREAM_IN_HAND)
            InteractionCondition.CINEMA_POPCORN -> setOf(SceneFlag.POPCORN_HELD)
            InteractionCondition.PHARMACY_PRODUCT -> setOf(SceneFlag.ITEM_HELD)
            else -> emptySet()
        }
        val phase = when (rule.condition) {
            InteractionCondition.CINEMA_WAIT -> CinemaPhase.LOBBY
            InteractionCondition.CINEMA_AFTER -> CinemaPhase.EXIT_LIGHTS
            else -> CinemaPhase.SCREENING
        }
        return InteractionContext(rule.scene!!, animation = animation, seated = true, sceneFlags = flags, cinemaPhase = phase)
    }

    private fun response(rule: InteractionRule) = InteractionResponse(rule.id, rule.animations, rule.text, 6_000, gesture = rule.gesture)

    @Test fun `all 35 approved venue rules require the actual visible activity`() {
        val venueRules = rules.filter { it.scene in HoodieInteractionResolver.venueScenes }
        assertEquals(35, venueRules.size)
        HoodieInteractionResolver.venueScenes.forEach { scene ->
            InteractionGesture.entries.forEach { gesture -> assertTrue(venueRules.count { it.scene == scene && it.gesture == gesture } >= 2) }
        }
        venueRules.forEach { rule ->
            val c = context(rule)
            assertTrue(rule.id, resolver.stillEligible(response(rule), c))
            assertFalse(rule.id, resolver.stillEligible(response(rule), c.copy(animation = AnimationId.WORK_TYPING)))
            assertFalse(rule.id, resolver.stillEligible(response(rule), c.copy(scene = SceneId.HOME)))
            assertNotEquals(InteractionCondition.ANY, rule.condition)
        }
    }

    @Test fun `objects disappearing invalidate pending dialogue before presentation`() {
        rules.filter { it.scene in HoodieInteractionResolver.venueScenes }.forEach { rule ->
            val c = context(rule)
            if (c.sceneFlags.isNotEmpty()) assertFalse(rule.id, resolver.stillEligible(response(rule), c.copy(sceneFlags = emptySet())))
        }
    }

    @Test fun `cinema completion cannot be invented from a seated pose`() {
        val after = rules.single { it.condition == InteractionCondition.CINEMA_AFTER }
        assertFalse(resolver.stillEligible(response(after), context(after).copy(cinemaPhase = CinemaPhase.SCREENING)))
        assertFalse(resolver.stillEligible(response(after), context(after).copy(cinemaPhase = null)))
        assertFalse(resolver.stillEligible(response(after), context(after).copy(seated = false)))
    }

    @Test fun `venue flag off preserves global fallback even in manually selected scenes`() {
        val disabled = HoodieInteractionResolver(rules, Random(4), venuesEnabled = false)
        val history = HoodieInteractionHistory()
        rules.filter { it.scene in HoodieInteractionResolver.venueScenes }.forEach { rule ->
            assertFalse(disabled.stillEligible(response(rule), context(rule)))
            val result = disabled.resolve(context(rule), rule.gesture, history, 10_000)
            assertTrue(rule.id, result is InteractionDecision.Accepted)
            assertTrue((result as InteractionDecision.Accepted).response.ruleId.startsWith("fallback_"))
        }
    }

    @Test fun `petting is blocked during ice cream store entry walking and naps`() {
        val policy = HoodieInteractionPolicy()
        for (animation in listOf(AnimationId.BEACH_ICE_CREAM, AnimationId.MALL_ENTER_STORE, AnimationId.NAP_SIT)) {
            assertTrue(policy.evaluate(InteractionContext(SceneId.BEACH, animation = animation), InteractionGesture.LONG_PRESS, HoodieInteractionHistory(), 10_000) is InteractionDecision.Blocked)
        }
        assertTrue(policy.evaluate(InteractionContext(SceneId.MALL, walking = true), InteractionGesture.LONG_PRESS, HoodieInteractionHistory(), 10_000) is InteractionDecision.Blocked)
    }
}
