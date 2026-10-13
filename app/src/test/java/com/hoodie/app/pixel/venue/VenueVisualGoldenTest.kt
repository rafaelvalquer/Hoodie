package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.random.Random

/**
 * Folhas de revisão e goldens das cenas de estabelecimento (build/pixel-preview/venues/).
 * Os hashes só são gravados (RECORD_VENUE_GOLDENS=true) depois da aprovação humana das folhas.
 */
class VenueVisualGoldenTest {

    data class Shot(
        val spot: SpotId, val anim: AnimationId, val atMs: Long, val period: DayPeriod = DayPeriod.DAY,
        val steady: Set<SceneFlag> = emptySet(), val direction: Direction = Direction.FRONT, val variant: Int = 0,
    )

    /** Momentos do Hoodie por cena (nome → quadro). */
    private val shots: Map<SceneId, Map<String, Shot>> = mapOf(
        SceneId.BAKERY to linkedMapOf(
            "bakery_choose" to Shot(SpotId.COUNTER, AnimationId.BAKERY_CHOOSE, 2_700),
            "bakery_wait" to Shot(SpotId.COUNTER, AnimationId.BAKERY_WAIT_ORDER, 3_700, DayPeriod.MORNING, variant = 1),
            "bakery_eat" to Shot(SpotId.CAFE_TABLE, AnimationId.BAKERY_EAT_PASTRY, 1_300, steady = setOf(SceneFlag.CHAIR_OCCUPIED, SceneFlag.PASTRY_IN_HAND)),
            "bakery_coffee" to Shot(SpotId.CAFE_TABLE, AnimationId.DRINK, 900, DayPeriod.EVENING, setOf(SceneFlag.CHAIR_OCCUPIED, SceneFlag.MUG_IN_HAND), variant = 2),
            "bakery_watch" to Shot(SpotId.CAFE_TABLE, AnimationId.BAKERY_PEOPLE_WATCH, 30_000, DayPeriod.NIGHT, setOf(SceneFlag.CHAIR_OCCUPIED)),
        ),
        SceneId.PHARMACY to linkedMapOf(
            "pharmacy_look" to Shot(SpotId.SHELF_A, AnimationId.SHOP_LOOK, 1_500, DayPeriod.MORNING),
            "pharmacy_examine" to Shot(SpotId.SHELF_B, AnimationId.PHARMACY_EXAMINE, 2_300),
            "pharmacy_pay" to Shot(SpotId.CHECKOUT, AnimationId.SHOP_PAY, 1_400, DayPeriod.EVENING, variant = 1),
            "pharmacy_wait" to Shot(SpotId.CENTER, AnimationId.IDLE_PHONE, 2_000, DayPeriod.NIGHT, variant = 2),
        ),
    )

    private fun shot(scene: SceneId, s: Shot): PixelBuffer {
        val sm = AnimationStateMachine(Random(7))
        sm.setVisual(
            VisualState(scene, s.spot, listOf(MicroAction(s.anim, 1, 120_000, 120_000, direction = s.direction)), steadyFlags = s.steady, variant = s.variant, daySeed = 30_000),
            1,
        )
        val renderer = SceneRenderer()
        var t = 1L
        val minute = if (s.period == DayPeriod.NIGHT) 22 * 60 else 10 * 60 + 8
        var frame = sm.frame(t, minute, s.period)!!
        while (t < s.atMs) { t += 33; frame = sm.frame(t, minute, s.period)!! }
        return PixelBuffer(240, 320).also { it.copyFrom(renderer.render(frame, t)) }
    }

    private fun empty(scene: SceneId, variant: Int, period: DayPeriod, t: Long, daySeed: Int = 30_000) =
        PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().renderEmpty(SceneRegistry[scene], VenueTestFixtures.env(variant, daySeed, period), t)) }

    private fun digest(buf: PixelBuffer): String {
        val md = MessageDigest.getInstance("SHA-256")
        buf.pixels.forEach { p -> md.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
        return HexFormat.of().formatHex(md.digest())
    }

    /** Variantes × períodos e uma linha do tempo de 2 min por cena (folhas para revisão). */
    @Test fun exportReviewSheets() {
        VenueTestFixtures.scenes.forEach { scene ->
            val name = scene.name.lowercase()
            val grid = (0 until 3).flatMap { v -> DayPeriod.entries.map { p -> empty(scene, v, p, 45_000L + v * 7_000) } }
            PreviewExport.sheet("venues/${name}_variants_periods", grid, columns = 4, scale = 2)
            val timeline = (0L..120_000L step 10_000L).map { empty(scene, 0, DayPeriod.DAY, it + 5_000) }
            PreviewExport.sheet("venues/${name}_timeline", timeline, columns = 5, scale = 2)
            timeline.zipWithNext().forEach { (a, b) -> assertFalse("$scene parada no tempo", a.pixels.contentEquals(b.pixels)) }
            assertEquals(DayPeriod.entries.size, DayPeriod.entries.map { p -> empty(scene, 0, p, 1_000).pixels.toList() }.toSet().size)
        }
    }

    @Test fun venueGoldens() {
        val actual = shots.filterKeys { it in VenueTestFixtures.scenes }.flatMap { (scene, named) ->
            named.map { (name, s) -> name to shot(scene, s) }
        }.toMap()
        shots.keys.filter { it in VenueTestFixtures.scenes }.forEach { scene ->
            val own = shots.getValue(scene).keys.map { actual.getValue(it) }
            PreviewExport.sheet("venues/${scene.name.lowercase()}_hoodie", own, columns = 5, scale = 2)
        }
        val lines = actual.map { (name, buf) -> "$name\t${digest(buf)}" }
        val candidate = File(PreviewExport.dir, "venues/venue-goldens-v1-candidate.sha256")
        candidate.parentFile.mkdirs()
        candidate.writeText(lines.joinToString("\n", postfix = "\n"))
        if (System.getenv("RECORD_VENUE_GOLDENS") == "true") {
            File("src/test/resources/venue-goldens-v1.sha256").writeText(lines.joinToString("\n", postfix = "\n"))
            return
        }
        val stream = javaClass.getResourceAsStream("/venue-goldens-v1.sha256")
        if (stream == null) {
            // Ainda sem aprovação humana: só as folhas e o candidato são gerados.
            assertTrue(actual.isNotEmpty())
            return
        }
        val expected = stream.bufferedReader().readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t').trim() }
        actual.forEach { (name, buf) -> expected[name]?.let { assertEquals("golden mudou: $name", it, digest(buf)) } }
    }
}
