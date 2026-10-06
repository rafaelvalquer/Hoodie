package com.hoodie.app.pixel.diary.clock

import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.engine.diary.DayClockAssembler
import com.hoodie.app.engine.diary.SyntheticClockDays
import com.hoodie.app.engine.diary.SyntheticClockDays.Kind
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
 * Goldens do Relógio do Dia 2.0 (modelo do JourneyGoldenTest): PNGs em
 * build/pixel-preview/day-clock/ e SHA-256 em src/test/resources/day-clock-v1.sha256.
 * Regravar depois de revisar os PNGs: `DAY_CLOCK_GOLDEN_RECORD=1`.
 */
class DayClockGoldenTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val date: LocalDate = LocalDate.of(2026, 10, 5)

    private fun data(kind: Kind, now: LocalTime? = LocalTime.of(21, 40), z: ZoneId = zone, d: LocalDate = date): DayClockData {
        val day = SyntheticClockDays.build(kind, d, z, now)
        return DayClockAssembler.build(day.diary, d, z, day.now)
    }

    private fun render(data: DayClockData, selected: String? = null, timeMs: Long = 1_200): PixelBuffer =
        DayClockRenderer.render(DayClockScene(data, data.nowMinute, selected, timeMs))

    private fun digest(b: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + b.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(b.width); bytes.putInt(b.height)
        b.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }

    private val states: Map<String, PixelBuffer> by lazy {
        val full = data(Kind.FULL)
        linkedMapOf(
            "empty" to render(data(Kind.EMPTY, LocalTime.of(10, 0))),
            "morning_one_stop" to render(data(Kind.MORNING_ONE_STOP, LocalTime.of(9, 15))),
            "full_day" to render(full),
            "old_stop_selected" to render(full, full.stays.first { it.placeName == "Trabalho" }.id),
            "move_selected" to render(full, full.segments.first { it is ClockSegment.Move && it.mode == com.hoodie.app.core.mobility.MovementMode.BUS }.id),
            "past_full_day" to render(data(Kind.FULL, null)),
            "many_short_moves" to render(data(Kind.MANY_SHORT_MOVES, LocalTime.of(20, 0))),
            "dst_day" to render(data(Kind.DST, null, ZoneId.of("Europe/Berlin"), LocalDate.of(2026, 3, 29))),
        )
    }

    @Test fun dayClockGoldens() {
        states.forEach { (name, img) -> PreviewExport.write(File(PreviewExport.dir, "day-clock/$name.png"), img, 6, 0xFF2B2E4A.toInt()) }
        val actual = states.mapValues { digest(it.value) }
        if (System.getenv("DAY_CLOCK_GOLDEN_RECORD") == "1") {
            File("src/test/resources/day-clock-v1.sha256").writeText(actual.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            return
        }
        val stream = javaClass.getResourceAsStream("/day-clock-v1.sha256")
        assertTrue("Sem golden; revise build/pixel-preview/day-clock e grave com DAY_CLOCK_GOLDEN_RECORD=1", stream != null)
        val expected = stream!!.bufferedReader().useLines { l -> l.filter { it.isNotBlank() }.associate { it.split('\t').let { (k, v) -> k to v } } }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (k, v) -> assertEquals("Relógio do Dia 2.0 mudou visualmente: $k", expected.getValue(k), v) }
    }

    @Test fun everyRenderedPixelBelongsToTheClockPalette() {
        states.forEach { (name, img) ->
            img.pixels.filter { it ushr 24 != 0 }.toSet().forEach { c ->
                assertTrue("$name usa cor fora da paleta: #%08X".format(c), c in DayClockPalette.ALL)
            }
        }
    }

    @Test fun sameDayRendersTheSamePixels() {
        assertTrue(render(data(Kind.FULL)).pixels.contentEquals(render(data(Kind.FULL)).pixels))
    }
}
