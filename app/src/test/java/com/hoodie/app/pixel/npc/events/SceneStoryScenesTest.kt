package com.hoodie.app.pixel.npc.events

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.restaurant.RestaurantNpcDirector
import com.hoodie.app.pixel.npc.restaurant.RestaurantStorySession
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingStoryEvents
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.RestaurantScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.ShoppingScene
import com.hoodie.app.pixel.sprite.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Microeventos do restaurante e das compras: acontecem, um objeto só, sem teleporte, e folha de revisão. */
class SceneStoryScenesTest {
    private fun env(seed: Int, minute: Int = 12 * 60) = SceneEnv(DayPeriod.DAY, minute, variant = seed, daySeed = seed)
    private val horizon = 40 * 60_000L

    private fun restaurant(seed: Int): Pair<RestaurantStorySession, List<ScriptedStory>> {
        val e = env(seed)
        val session = RestaurantStorySession(RestaurantStorySession.key(e), RestaurantNpcDirector.brain(e))
        return session to session.overlay.storiesUntil(horizon)
    }

    private fun shopping(seed: Int): Pair<ShoppingStoryEvents, List<ScriptedStory>> {
        val slots = ShoppingNpcDirector.plan(env(seed, 10 * 60))
        val overlay = slots.first { it.shoppingBrain != null }.story as PeriodicStoryOverlay
        val story = ShoppingStoryEvents(slots.first { it.shoppingBrain != null }.shoppingBrain!!, com.hoodie.app.pixel.npc.shopping.ShoppingNavigationGraph)
        return story to story.overlay.storiesUntil(horizon).also { assertEquals(it.map { s -> s.event.id }, overlay.storiesUntil(horizon).map { s -> s.event.id }) }
    }

    @Test
    fun `copo derramado acontece, respeita o orcamento e o cliente fica calmo e sentado`() {
        val all = (1..8).flatMap { restaurant(it).second }
        assertTrue("só ${all.size} eventos", all.size >= 4)
        (1..8).forEach { seed ->
            val (_, stories) = restaurant(seed)
            stories.forEach { s ->
                assertTrue(stories.count { it.event.startAt > s.event.startAt - 600_000 && it.event.startAt <= s.event.startAt } <= 2)
                val brain = RestaurantNpcDirector.brain(env(seed))
                val guestTrack = s.track(RestaurantNpcDirector.GUEST_ID)!!
                var t = guestTrack.start
                while (t < guestTrack.end) {
                    assertTrue(brain.stateAt(t).currentIntent in RestaurantStorySession.CALM)
                    assertTrue(guestTrack.movementAt(t)!!.seated)
                    t += 250
                }
                noTeleport(s.track(RestaurantStorySession.WAITER_ID)!!)
                val glass = s.objects.first { it.item == Item.GLASS }
                assertEquals(listOf(StoryObjectHolder.SPILLED, StoryObjectHolder.ON_TABLE), glass.steps.map { it.holder })
            }
        }
    }

    @Test
    fun `cesta cai, o mesmo objeto volta para o dono e ninguem atravessa o outro`() {
        val all = (1..8).flatMap { shopping(it).second }
        assertTrue("só ${all.size} eventos", all.size >= 4)
        all.forEach { s ->
            val basket = s.objects.single()
            assertEquals(listOf(StoryObjectHolder.HELD_BY_OWNER, StoryObjectHolder.ON_FLOOR, StoryObjectHolder.HELD_BY_HELPER, StoryObjectHolder.HELD_BY_OWNER),
                basket.steps.map { it.holder })
            val owner = s.tracks.first { it.actorId != ShoppingStoryEvents.HELPER_ID }
            val helper = s.track(ShoppingStoryEvents.HELPER_ID)!!
            noTeleport(helper)
            var t = s.event.startAt
            while (t < owner.end) {
                val holder = basket.stateAt(t)!!.holder
                val o = owner.movementAt(t)!!
                val h = helper.movementAt(t)
                val inHands = listOf(o.heldItem, h?.heldItem).count { it == Item.BASKET }
                // Na mão do dono no começo é a cesta normal (desenhada pelo renderer); depois, só um a segura.
                if (holder != StoryObjectHolder.HELD_BY_OWNER || t > basket.steps[1].at) {
                    assertEquals("$t $holder", if (holder == StoryObjectHolder.ON_FLOOR) 0 else 1, inHands)
                }
                if (h != null && !helper.hiddenAt(t)) assertTrue("encostou em $t", abs(h.x - o.x) >= 16 || h.floorY != o.floorY)
                t += 50
            }
        }
    }

    private fun noTeleport(track: StoryTrack) {
        var prev = track.movementAt(track.start)!!
        var t = track.start
        while (t < track.end) {
            val m = track.movementAt(t) ?: break
            assertTrue("salto em $t", abs(m.x - prev.x) <= 3 && abs(m.floorY - prev.floorY) <= 3)
            prev = m; t += 20
        }
    }

    @Test
    fun `exporta folhas de revisao`() {
        val (rs, rStories) = (1..8).map { restaurant(it) }.first { it.second.isNotEmpty() }
        val spill = rStories.first()
        val rEnv = env(rs.key.daySeed)
        val waiter = spill.track(RestaurantStorySession.WAITER_ID)!!
        val rMoments = listOf(spill.event.startAt - 400, spill.event.startAt + 500, spill.event.interactAt + 900,
            waiter.segments.first { it.animation == NpcAnimation.WIPE }.start + 800, spill.event.resolveAt + 400,
            waiter.segments.first { it.speech != null }.start + 300, waiter.end - 900, spill.event.endsAt + 600)
        PreviewExport.sheet("story-events/restaurant-spill-help", render(RestaurantScene(), rEnv, rMoments), columns = 4, scale = 2)

        val seed = (1..8).first { shopping(it).second.isNotEmpty() }
        val drop = shopping(seed).second.first()
        val helper = drop.track(ShoppingStoryEvents.HELPER_ID)!!
        val sMoments = listOf(drop.event.startAt - 400, drop.event.startAt + 600, (helper.start + drop.event.interactAt) / 2,
            drop.event.interactAt + 300, drop.event.interactAt + 1_000, helper.segments.first { it.speech != null }.start + 300,
            drop.event.resolveAt - 600, drop.event.resolveAt + 1_500)
        PreviewExport.sheet("story-events/shopping-dropped-basket", render(ShoppingScene(), env(seed, 10 * 60), sMoments), columns = 4, scale = 2)
    }

    private fun render(scene: PixelScene, env: SceneEnv, moments: List<Long>): List<PixelBuffer> {
        val renderer = SceneRenderer()
        return moments.map { t ->
            val frame = renderer.renderEmpty(scene, env, t)
            PixelBuffer(frame.width, frame.height).apply { copyFrom(frame) }
        }
    }
}
