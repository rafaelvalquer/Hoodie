package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures.t
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Golden da Jornada (plano §18.4): vazio, dia curto, dia longo, replay tocando,
 * manhã, entardecer, noite e parada selecionada. Cada estado vira um PNG em
 * build/pixel-preview/journey/ (para revisão visual) e um SHA-256 comparado com
 * src/test/resources/journey-map-v1.sha256.
 *
 * Mudou o visual de propósito? Revise os PNGs e regrave com
 * `JOURNEY_GOLDEN_RECORD=1 ./gradlew :app:testDebugUnitTest --tests '*JourneyGoldenTest*'`.
 */
class JourneyGoldenTest {

    private fun scene(data: JourneyMapData, ts: Long?, replaying: Boolean, selected: String? = null, timeMs: Long = 2_400): JourneyScene {
        val layout = JourneyLayoutEngine.layout(data)
        return JourneyScene(
            data, layout, JourneyReplayAssembler.stateAt(data, ts, replaying),
            JourneyLightingRenderer.resolve(ts, JourneyTestFixtures.ZONE), selected, timeMs,
        )
    }

    private val data = JourneyTestFixtures.data

    private val states: Map<String, JourneyScene> by lazy {
        linkedMapOf(
            "empty" to scene(JourneyMapData.EMPTY, null, false),
            "short_day" to scene(JourneyTestFixtures.shortData, null, false),
            "long_day" to scene(JourneyTestFixtures.longData, null, false),
            "replay_playing" to scene(data, t(12, 12), true),
            "morning" to scene(data, t(7, 58), true),
            "evening" to scene(data, t(19, 35), true),
            "night" to scene(data, t(22, 30), true),
            "node_selected" to scene(data, null, false, selected = data.nodes[3].id),
        )
    }

    private fun digest(b: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + b.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(b.width); bytes.putInt(b.height)
        b.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }

    @Test
    fun `estados da jornada batem com o golden`() {
        val actual = states.mapValues { (name, scene) ->
            val img = JourneyMapRenderer.render(scene)
            PreviewExport.save("journey/$name", img, scale = 3)
            digest(img)
        }
        assertEquals(8, actual.size)
        if (System.getenv("JOURNEY_GOLDEN_RECORD") == "1") {
            File("src/test/resources/journey-map-v1.sha256").writeText(actual.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            return
        }
        val stream = javaClass.getResourceAsStream("/journey-map-v1.sha256")
        assertTrue("Sem golden: grave com JOURNEY_GOLDEN_RECORD=1 depois de revisar build/pixel-preview/journey/", stream != null)
        val expected = stream!!.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.associate { it.split('\t').let { (k, v) -> k to v } } }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (name, hash) -> assertEquals("Jornada mudou visualmente: $name (veja build/pixel-preview/journey/$name.png)", expected[name], hash) }
    }
}
