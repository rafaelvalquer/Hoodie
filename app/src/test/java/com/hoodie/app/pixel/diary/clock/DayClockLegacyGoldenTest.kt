package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.engine.diary.journey.DayClockLegacyAssembler
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays.Kind
import com.hoodie.app.pixel.PreviewExport
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
 * Goldens do Relógio do dia (plano §8.2): PNGs em build/pixel-preview/journey-v3/clock_*.png
 * e SHA-256 em src/test/resources/day-clock-legacy.sha256. Regravar: `JOURNEY_GOLDEN_RECORD=1`.
 */
class DayClockLegacyGoldenTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val date: LocalDate = LocalDate.of(2026, 10, 5)
    private fun at(h: Int, m: Int = 0) = date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun render(kind: Kind, replayAt: Long? = null): PixelBuffer {
        val data = DayClockLegacyAssembler.build(SyntheticJourneyDays.build(kind, date, zone), zone, date)
        val deg = replayAt?.let { DayClockLegacyAssembler.day(date, zone).deg(it) }
        return DayClockLegacyRenderer.render(DayClockLegacyScene(data, deg, null, 2_400, date.toEpochDay()))
    }

    private fun digest(b: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + b.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(b.width); bytes.putInt(b.height)
        b.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }

    private val states by lazy {
        linkedMapOf(
            "clock_empty" to render(Kind.EMPTY),
            "clock_common_day" to render(Kind.NINE),
            "clock_many_short_stops" to render(Kind.TWENTY),
            "clock_replay_mid" to render(Kind.NINE, at(13, 20)),
        )
    }

    @Test fun dayClockGoldens() {
        states.forEach { (name, img) -> PreviewExport.write(File(PreviewExport.dir, "journey-v3/$name.png"), img, 3, 0xFF2B2E4A.toInt()) }
        val actual = states.mapValues { digest(it.value) }
        if (System.getenv("JOURNEY_GOLDEN_RECORD") == "1") {
            File("src/test/resources/day-clock-legacy.sha256").writeText(actual.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            return
        }
        val stream = javaClass.getResourceAsStream("/day-clock-legacy.sha256")
        assertTrue("Sem golden do relógio; revise build/pixel-preview/journey-v3 e grave com JOURNEY_GOLDEN_RECORD=1", stream != null)
        val expected = stream!!.bufferedReader().useLines { l -> l.filter { it.isNotBlank() }.associate { it.split('\t').let { (k, v) -> k to v } } }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (k, v) -> assertEquals("Relógio do dia mudou visualmente: $k", expected.getValue(k), v) }
    }

    @Test fun clockIsDeterministic() {
        assertTrue(render(Kind.TWENTY, at(10)).pixels.contentEquals(render(Kind.TWENTY, at(10)).pixels))
    }
}
