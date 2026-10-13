package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.npc.events.SceneStoryEventType
import com.hoodie.app.pixel.npc.events.StoryObjectHolder
import com.hoodie.app.pixel.npc.leisure.LeisureNpcDirector
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.sprite.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Microeventos dentro das simulações compartilhadas (padaria, praia, hotel, passeio). */
class VenueStoryEventsTest {
    private val horizon = 40 * 60_000L

    private fun env(seed: Int, variant: Int) = SceneEnv(DayPeriod.DAY, 10 * 60, variant = variant, daySeed = seed)

    private fun venue(scene: SceneId, seed: Int, variant: Int): List<VenueStoryRecord> =
        VenueNpcDirector.createSession(scene, env(seed, variant)).storiesUntil(horizon)

    private fun leisure(seed: Int, variant: Int): List<VenueStoryRecord> =
        LeisureNpcDirector.createSession(env(seed, variant)).storiesUntil(horizon)

    private fun cases() = (0 until 6).flatMap { seed -> (0 until 3).map { seed to it } }

    private fun assertBudget(records: List<VenueStoryRecord>) = records.forEach { r ->
        assertTrue(records.count { it.event.startAt > r.event.startAt - 600_000 && it.event.startAt <= r.event.startAt } <= 2)
    }

    private fun checkScene(name: String, type: SceneStoryEventType, all: (Int, Int) -> List<VenueStoryRecord>): List<Pair<Pair<Int, Int>, VenueStoryRecord>> {
        val found = cases().flatMap { (s, v) -> all(s, v).map { (s to v) to it } }
        assertTrue("$name: nenhum evento em ${cases().size} sessões de 40 min", found.isNotEmpty())
        found.forEach { (_, r) -> assertEquals(type, r.event.type) }
        cases().forEach { (s, v) -> assertBudget(all(s, v)) }
        val (s, v) = found.first().first
        assertEquals("$name: determinístico", all(s, v).map { it.event }, all(s, v).map { it.event })
        return found
    }

    @Test fun `padaria entrega o cafe na mao do cliente que ja esperava`() {
        val found = checkScene("padaria", SceneStoryEventType.CAFE_ORDER_HANDOFF) { s, v -> venue(SceneId.BAKERY, s, v) }
        found.forEach { (_, r) ->
            assertEquals(listOf(StoryObjectHolder.HELD_BY_HELPER, StoryObjectHolder.ON_COUNTER, StoryObjectHolder.HELD_BY_OWNER),
                r.objects.single().steps.map { it.holder })
            assertEquals(Item.MUG, r.objects.single().item)
        }
    }

    @Test fun `praia tira a foto em dupla no meio da quadra`() {
        val found = checkScene("praia", SceneStoryEventType.BEACH_SHARED_PHOTO) { s, v -> venue(SceneId.BEACH, s, v) }
        val (key, r) = found.first()
        val session = VenueNpcDirector.createSession(SceneId.BEACH, env(key.first, key.second))
        val snap = session.snapshotAt((r.event.interactAt + r.event.resolveAt) / 2)
        val pair = snap.npcs.filter { it.npcId.startsWith("ball_player") }
        assertEquals(setOf(BeachVenue.PHOTO_L, BeachVenue.PHOTO_R), pair.map { it.spot }.toSet())
    }

    @Test fun `hotel - o visitante leva a mala, um so dono por vez, e o hospede sobe com ela`() {
        val found = checkScene("hotel", SceneStoryEventType.HOTEL_LUGGAGE_HELP) { s, v -> venue(SceneId.HOTEL, s, v) }
        found.forEach { (key, r) ->
            assertEquals(listOf(StoryObjectHolder.HELD_BY_OWNER, StoryObjectHolder.HELD_BY_HELPER, StoryObjectHolder.ON_FLOOR, StoryObjectHolder.HELD_BY_OWNER),
                r.objects.single().steps.map { it.holder })
            val session = VenueNpcDirector.createSession(SceneId.HOTEL, env(key.first, key.second))
            var t = r.event.startAt
            while (t < r.event.endsAt) {
                val snap = session.snapshotAt(t)
                val holders = snap.npcs.count { it.held == Item.SUITCASE && !it.hidden }
                val floor = (snap.ambient as HotelAmbient).suitcaseOnFloor != null
                assertTrue("mala duplicada em $t", holders + (if (floor) 1 else 0) <= 1)
                t += 100
            }
        }
    }

    @Test fun `passeio - dois visitantes param juntos diante da vista`() {
        checkScene("passeio", SceneStoryEventType.LEISURE_SHARED_VIEW) { s, v -> leisure(s, v) }
    }

    @Test fun `exporta folhas de revisao`() {
        fun export(name: String, scene: SceneId, key: Pair<Int, Int>, r: VenueStoryRecord) {
            val e = r.event
            val moments = listOf(e.startAt + 200, (e.startAt + e.interactAt) / 2, e.interactAt + 200,
                (e.interactAt + e.resolveAt) / 2, e.resolveAt - 200, (e.resolveAt + e.endsAt) / 2, e.endsAt - 200, e.endsAt + 1_500)
            val renderer = SceneRenderer()
            val frames = moments.map { t ->
                val f = renderer.renderEmpty(SceneRegistry[scene], env(key.first, key.second), t)
                PixelBuffer(f.width, f.height).apply { copyFrom(f) }
            }
            PreviewExport.sheet("story-events/$name", frames, columns = 4, scale = 2)
        }
        cases().firstNotNullOfOrNull { (s, v) -> venue(SceneId.BAKERY, s, v).firstOrNull()?.let { (s to v) to it } }?.let { export("bakery-order-handoff", SceneId.BAKERY, it.first, it.second) }
        cases().firstNotNullOfOrNull { (s, v) -> venue(SceneId.BEACH, s, v).firstOrNull()?.let { (s to v) to it } }?.let { export("beach-shared-photo", SceneId.BEACH, it.first, it.second) }
        cases().firstNotNullOfOrNull { (s, v) -> venue(SceneId.HOTEL, s, v).firstOrNull()?.let { (s to v) to it } }?.let { export("hotel-luggage-help", SceneId.HOTEL, it.first, it.second) }
        cases().firstNotNullOfOrNull { (s, v) -> leisure(s, v).firstOrNull()?.let { (s to v) to it } }?.let { export("leisure-shared-view", SceneId.LEISURE, it.first, it.second) }
    }
}
