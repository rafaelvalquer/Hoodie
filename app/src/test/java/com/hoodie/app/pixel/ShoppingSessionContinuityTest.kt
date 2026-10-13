package com.hoodie.app.pixel

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcSession
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.pixel.renderer.SceneRenderer
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ShoppingSessionContinuityTest {
    private fun session(renderer: SceneRenderer): ShoppingNpcSession? =
        SceneRenderer::class.java.getDeclaredField("shoppingSession").let {
            it.isAccessible = true; it.get(renderer) as ShoppingNpcSession?
        }

    private fun startedAt(renderer: SceneRenderer): Long? =
        SceneRenderer::class.java.getDeclaredField("shoppingSessionStartedAt").let {
            it.isAccessible = true; it.get(renderer) as Long?
        }

    private fun frame() = AnimationStateMachine(Random(7)).let {
        it.setVisual(VisualState(SceneId.STORE, SpotId.RACK_A,
            listOf(MicroAction(AnimationId.STORE_BROWSE_RACK, 1, 60_000, 60_000))), 0)
        it.frame(0, 600, DayPeriod.DAY)!!
    }

    @Test fun periodChangesAndLongResumeKeepTheSameShopperAndClock() {
        val renderer = SceneRenderer()
        val base = frame().let { it.copy(env = it.env.copy(daySeed = 42, shoppingVenue = PlaceType.STORE)) }
        renderer.render(base, 0)
        val original = session(renderer)!!
        for (period in DayPeriod.entries) {
            renderer.render(base.copy(env = base.env.copy(period = period)), 19_000)
            assertSame(original, session(renderer))
            assertEquals(0L, startedAt(renderer))
        }
        renderer.render(base, 1_800_000)
        assertSame(original, session(renderer))
        assertEquals(0L, startedAt(renderer))
        // Recomposition with an equivalent frame must also retain the session.
        renderer.render(base.copy(), 1_800_033)
        assertSame(original, session(renderer))
        renderer.dispose()
        assertNull(session(renderer))
        assertNull(startedAt(renderer))
    }

    @Test fun changingDayVariantVenueFloorOrContextStartsAnIndependentSession() {
        val renderer = SceneRenderer()
        val base = frame()
        val frames = listOf(base,
            base.copy(env = base.env.copy(daySeed = 1)),
            base.copy(env = base.env.copy(daySeed = 1, variant = 1)),
            base.copy(env = base.env.copy(daySeed = 1, variant = 1, shoppingVenue = PlaceType.STORE)),
            base.copy(scene = SceneRegistry[SceneId.SHOPPING]),
        )
        var previous: ShoppingNpcSession? = null
        frames.forEachIndexed { index, next ->
            renderer.render(next, index * 1_000L)
            assertNotSame(previous, session(renderer))
            assertEquals(index * 1_000L, startedAt(renderer))
            previous = session(renderer)
        }
        renderer.render(base.copy(scene = SceneRegistry[SceneId.HOME]), 6_000)
        assertNull(session(renderer))
        renderer.render(base, 7_000)
        assertNotSame(previous, session(renderer))
        assertEquals(7_000L, startedAt(renderer))
    }

    @Test fun leavingDuringPaymentOrWalkingClearsOldShopperAndObjects() {
        for (intent in listOf(com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent.PAY,
            com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent.WALK_TO_CHECKOUT)) {
            val renderer = SceneRenderer()
            val base = frame()
            renderer.render(base, 0)
            val previous = session(renderer)!!
            val brain = previous.npcSlots.single().shoppingBrain!!
            val at = (0L..300_000L step 100).first { brain.stateAt(it).currentIntent == intent }
            renderer.render(base, at)
            if (intent == com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent.PAY) {
                assertTrue(brain.visualStateAt(at).checkoutActive)
            } else assertEquals(com.hoodie.app.pixel.npc.PathPhase.WALK, brain.movementAt(at).phase)
            renderer.render(base.copy(scene = SceneRegistry[SceneId.HOME]), at + 33)
            assertNull(session(renderer))
            assertNull(startedAt(renderer))
            renderer.render(base, at + 66)
            val restarted = session(renderer)!!
            assertNotSame(previous, restarted)
            assertEquals(at + 66, startedAt(renderer))
            assertFalse(restarted.npcSlots.single().shoppingBrain!!.visualStateAt(0).checkoutActive)
            renderer.dispose()
        }
    }
}
