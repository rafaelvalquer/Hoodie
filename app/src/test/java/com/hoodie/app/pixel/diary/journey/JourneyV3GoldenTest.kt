package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays.Kind
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.diary.overworld.BiomeState
import com.hoodie.app.pixel.diary.overworld.OverworldBiomeCatalog
import com.hoodie.app.pixel.diary.overworld.OverworldJourneyRenderer
import com.hoodie.app.pixel.diary.overworld.OverworldPalette
import com.hoodie.app.pixel.diary.overworld.OverworldScene
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.HexFormat

/**
 * Goldens da Jornada 3.0 (plano §8.2): PNGs em build/pixel-preview/journey-v3/ para
 * revisão e SHA-256 em src/test/resources/journey-map-v3.sha256.
 * Regravar depois de revisar: `JOURNEY_GOLDEN_RECORD=1`.
 */
class JourneyV3GoldenTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val date: LocalDate = LocalDate.of(2026, 10, 5)
    private fun at(h: Int, m: Int = 0) = date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private fun model(kind: Kind) = JourneyOverworldModel.build(SyntheticJourneyDays.build(kind, date, zone), zone)

    private fun render(model: JourneyOverworldModel, layout: OverworldLayout, ts: Long?, replaying: Boolean, framed: Boolean, timeMs: Long = 2_400): PixelBuffer {
        val replay = JourneyReplayAssembler.overworld(model.plan, layout, ts, replaying)
        val light = if (ts != null) JourneyLightingRenderer.resolve(ts, zone) else JourneyLightingRenderer.DAYLIGHT
        return OverworldJourneyRenderer.render(OverworldScene(model.plan, layout, replay, light, null, timeMs, model.seed, framed))
    }

    /** Capítulos empilhados (como o usuário veria um após o outro). */
    private fun chapters(model: JourneyOverworldModel, ts: Long?, replaying: Boolean): PixelBuffer {
        val parts = DayChapter.entries.mapNotNull { c -> model.chapters[c]?.let { render(model, it, ts, replaying, framed = true) } }
        val h = parts.sumOf { it.height + 4 }
        return PixelBuffer(240, h.coerceAtLeast(1)).also { out ->
            out.fill(0xFF2B2E4A.toInt())
            var y = 0
            parts.forEach { out.blit(it, 0, y); y += it.height + 4 }
        }
    }

    private fun biomeSheet(): PixelBuffer {
        val biomes = BiomeType.entries
        val states = BiomeState.entries
        val out = PixelBuffer(2 + states.size * 2 * 36, 2 + biomes.size * 36)
        out.fill(OverworldPalette.GRASS)
        biomes.forEachIndexed { r, biome ->
            states.forEachIndexed { c, s ->
                listOf(false, true).forEachIndexed { k, night ->
                    val x = 2 + (c * 2 + k) * 36; val y = 2 + r * 36
                    if (night) out.box(x - 1, y - 1, x + 33, y + 33, 0xFF2F3366.toInt())
                    OverworldBiomeCatalog.paint(out, biome, x, y, s, night, 1_200)
                }
            }
        }
        return out
    }

    private fun digest(b: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + b.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(b.width); bytes.putInt(b.height)
        b.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }

    private val states: Map<String, PixelBuffer> by lazy {
        val three = model(Kind.THREE)
        val nine = model(Kind.NINE)
        val ten = model(Kind.TEN)
        val twenty = model(Kind.TWENTY)
        val ghost = model(Kind.AFTERNOON_AT_WORK)
        val empty = model(Kind.EMPTY)
        // Trecho que atravessa capítulos (sai pela borda de baixo, entra pela de cima).
        val crossing = twenty.plan.legs.first { leg ->
            val ch = (twenty.plan as com.hoodie.app.domain.diary.journey.JourneyPlan.Chapters).chapters
            ch.any { it.exitLegId == leg.id }
        }
        linkedMapOf(
            "empty" to render(empty, empty.single ?: OverworldLayout.EMPTY, null, false, framed = false),
            "three_stops" to render(three, three.single!!, null, false, framed = false),
            "nine_stops_single_limit" to render(nine, nine.single!!, null, false, framed = false),
            "ten_stops_chapters" to chapters(ten, null, false),
            "twenty_stops_clustered" to chapters(twenty, null, false),
            "ghost_only_chapter" to render(ghost, ghost.chapters.getValue(DayChapter.AFTERNOON), null, false, framed = true),
            "replay_mid_segment" to render(three, three.single!!, at(7, 55), true, framed = false),
            "replay_portal" to chapters(twenty, crossing.startedAt + (crossing.endedAt - crossing.startedAt) / 3, true),
            "night_lights" to render(nine, nine.single!!, at(22, 30), true, framed = false),
            "biomes" to biomeSheet(),
        )
    }

    @Test fun journeyV3Goldens() {
        states.forEach { (name, img) -> PreviewExport.write(File(PreviewExport.dir, "journey-v3/$name.png"), img, 3, 0xFF2B2E4A.toInt()) }
        val actual = states.mapValues { digest(it.value) }
        if (System.getenv("JOURNEY_GOLDEN_RECORD") == "1") {
            File("src/test/resources/journey-map-v3.sha256").writeText(actual.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            return
        }
        val stream = javaClass.getResourceAsStream("/journey-map-v3.sha256")
        assertTrue("Sem golden v3; revise build/pixel-preview/journey-v3 e grave com JOURNEY_GOLDEN_RECORD=1", stream != null)
        val expected = stream!!.bufferedReader().useLines { l -> l.filter { it.isNotBlank() }.associate { it.split('\t').let { (k, v) -> k to v } } }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (k, v) -> assertEquals("Jornada 3.0 mudou visualmente: $k", expected.getValue(k), v) }
    }

    @Test fun sameDayRendersTheSamePixels() {
        val m = model(Kind.NINE)
        val a = render(m, m.single!!, at(12), true, framed = false)
        val b = render(model(Kind.NINE), model(Kind.NINE).single!!, at(12), true, framed = false)
        assertTrue(a.pixels.contentEquals(b.pixels))
    }
}
