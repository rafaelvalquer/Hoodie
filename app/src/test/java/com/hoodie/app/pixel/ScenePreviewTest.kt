package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.transport.TransportVisualRegistry
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.random.Random

/** Gera a "Scene Gallery" em PNG para revisão visual (build/pixel-preview). */
class ScenePreviewTest {

    private fun shot(activity: HoodieActivity, ctx: UserContextType, period: DayPeriod, commute: CommuteStyle = CommuteStyle.WALK, at: Long = 3_000): PixelBuffer {
        val sm = AnimationStateMachine(Random(4))
        val v = VisualDirector.resolve(activity, ctx, commute = commute, variant = 1)
        sm.setVisual(v, 1)
        var frame = sm.frame(1, 10 * 60 + 8, period)!!
        var t = 1L
        while (t < at) { t += 33; frame = sm.frame(t, 10 * 60 + 8, period)!! }
        val out = PixelBuffer(240, 320)
        out.copyFrom(SceneRenderer().render(frame, t))
        return out
    }

    @Test
    fun exportVerticalSlice() {
        val slice = listOf(
            shot(HoodieActivity.SLEEPING, UserContextType.HOME, DayPeriod.NIGHT),
            shot(HoodieActivity.BREAKFAST, UserContextType.HOME, DayPeriod.MORNING),
            shot(HoodieActivity.COMMUTING, UserContextType.COMMUTING, DayPeriod.MORNING),
            shot(HoodieActivity.WORKING, UserContextType.WORK, DayPeriod.DAY),
            shot(HoodieActivity.EATING, UserContextType.LUNCH, DayPeriod.DAY),
            shot(HoodieActivity.COMMUTING, UserContextType.COMMUTING, DayPeriod.EVENING, CommuteStyle.BUS),
            shot(HoodieActivity.GAMING, UserContextType.HOME, DayPeriod.EVENING),
            shot(HoodieActivity.TRAINING, UserContextType.GYM, DayPeriod.EVENING),
            shot(HoodieActivity.IDLE, UserContextType.UNKNOWN, DayPeriod.NIGHT),
            shot(HoodieActivity.WALKING, UserContextType.LEISURE, DayPeriod.DAY),
            shot(HoodieActivity.PHONE, UserContextType.VISITING, DayPeriod.DAY),
            shot(HoodieActivity.WORKING, UserContextType.WORK, DayPeriod.NIGHT),
        )
        PreviewExport.sheet("vertical_slice", slice, columns = 4, scale = 2)
    }

    @Test
    fun exportTransportGallery() {
        val modes = listOf(MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO, MovementMode.BICYCLE, MovementMode.OTHER, MovementMode.PUBLIC_TRANSPORT, MovementMode.VEHICLE_UNKNOWN)
        val actual = linkedMapOf<String, String>()
        val shots = modes.flatMap { mode -> listOf(DayPeriod.DAY, DayPeriod.NIGHT).map { period ->
            val sm = AnimationStateMachine(Random(401 + mode.ordinal))
            val visual = VisualDirector.resolve(
                HoodieActivity.COMMUTING, UserContextType.COMMUTING,
                commute = CommuteStyle.WALK, mobilityMode = mode,
            )
            sm.setVisual(visual, 1)
            var t = 1L
            var frame = sm.frame(t, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, period)!!
            while (t < 4_000L) { t += 33; frame = sm.frame(t, if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, period)!! }
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            frame = frame.copy(env = frame.env.copy(transportAmbient = profile.ambient))
            val image = PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().render(frame, t)) }
            val name = "${mode.name.lowercase()}_${period.name.lowercase()}"
            PreviewExport.save("transport/$name", image, scale = 2)
            actual[name] = digest(image)
            image
        } }
        PreviewExport.sheet("transport/gallery", shots, columns = 4, scale = 2)
        assertEquals(16, actual.size)
        if (System.getenv("RECORD_TRANSPORT_GOLDENS") == "true") {
            File("src/test/resources/transport-scenes-v1.sha256").writeText(actual.entries.joinToString("\n") { "${it.key}\t${it.value}" } + "\n")
            return
        }
        val stream = javaClass.getResourceAsStream("/transport-scenes-v1.sha256")
        assertTrue("Sem golden de transportes; revise build/pixel-preview/transport/gallery.png e rode com RECORD_TRANSPORT_GOLDENS=true", stream != null)
        val expected = stream!!.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.associate { it.split('\t').let { (k, v) -> k to v } } }
        assertEquals(actual.keys, expected.keys)
        actual.forEach { (name, hash) -> assertEquals("Transporte mudou visualmente: $name", expected[name], hash) }
    }

    private fun digest(buffer: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + buffer.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(buffer.width); bytes.putInt(buffer.height)
        buffer.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }
}
