package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionPolicyTest {
    private val policy = HoodieInteractionPolicy()
    private fun blocked(c: InteractionContext, g: InteractionGesture = InteractionGesture.TAP) =
        policy.evaluate(c, g, HoodieInteractionHistory(), 1_000) is InteractionDecision.Blocked

    @Test fun `sleep transition inactivity and busy sequence block before cooldown`() {
        val c = InteractionContext(SceneId.HOME)
        assertTrue(blocked(c.copy(asleep = true), InteractionGesture.LONG_PRESS))
        assertTrue(blocked(c.copy(animation = AnimationId.SLEEP_TURN)))
        assertTrue(blocked(c.copy(transitioning = true)))
        assertTrue(blocked(c.copy(screenActive = false)))
        assertTrue(blocked(c.copy(sequenceBusy = true)))
    }
    @Test fun `transport is denied unless stopped passenger state is explicit`() {
        HoodieInteractionPolicy.transport.forEach { scene ->
            val c = InteractionContext(scene)
            assertTrue(blocked(c))
            assertTrue(blocked(c.copy(vehicleMoving = true, safePassengerStop = true)))
            assertTrue(blocked(c.copy(vehicleMoving = false, safePassengerStop = true), InteractionGesture.LONG_PRESS))
            assertEquals(scene !in HoodieInteractionPolicy.passengers, blocked(c.copy(vehicleMoving = false, safePassengerStop = true)))
        }
    }
    @Test fun `petting never starts while walking or lifting but tap may wait for safe boundary`() {
        assertTrue(blocked(InteractionContext(SceneId.GYM, animation = AnimationId.LIFT), InteractionGesture.LONG_PRESS))
        assertTrue(blocked(InteractionContext(SceneId.LEISURE, walking = true), InteractionGesture.LONG_PRESS))
        assertFalse(blocked(InteractionContext(SceneId.GYM, animation = AnimationId.LIFT, canInterrupt = false)))
        assertTrue(blocked(InteractionContext(SceneId.LEISURE, animation = AnimationId.LEISURE_WALK), InteractionGesture.LONG_PRESS))
        assertTrue(blocked(InteractionContext(SceneId.SHOPPING, animation = AnimationId.SHOP_CART), InteractionGesture.LONG_PRESS))
        assertTrue(blocked(InteractionContext(SceneId.SCHOOL, animation = AnimationId.STUDY_WRITE), InteractionGesture.LONG_PRESS))
    }
}
