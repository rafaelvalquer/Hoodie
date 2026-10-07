package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.AnimationEvent
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.CarScene
import com.hoodie.app.pixel.scene.CarStrip
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.TrafficPhase
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.SpriteAnchors
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.transport.TransportAmbientProfile
import com.hoodie.app.pixel.transport.TransportLighting
import com.hoodie.app.pixel.transport.TransportVibration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.random.Random

class CarSceneTest {
    private fun env(period: DayPeriod = DayPeriod.DAY, vibration: TransportVibration = TransportVibration.LOW) = SceneEnv(
        period = period,
        clockMinute = 10 * 60,
        daySeed = 771,
        transportAmbient = TransportAmbientProfile(true, .8f, vibration, TransportLighting.DAYLIGHT_INTERIOR),
    )

    @Test fun renderingIsDeterministicAtTheSameSceneClock() {
        val scene = CarScene()
        val renderer = SceneRenderer()
        val first = renderer.renderEmpty(scene, env(DayPeriod.NIGHT), 18_500L).pixels.copyOf()
        // Intervening frames must not change a later render at the same scene time.
        renderer.renderEmpty(scene, env(), 3_000L)
        renderer.renderEmpty(scene, env(DayPeriod.EVENING), 72_000L)
        val again = renderer.renderEmpty(scene, env(DayPeriod.NIGHT), 18_500L).pixels
        assertArrayEquals(first, again)
    }

    @Test fun cachedParallaxStripsRepeatWithoutASeam() {
        val scene = CarScene()
        val state = env(DayPeriod.NIGHT)
        scene.drawBackground(PixelBuffer(240, 320), state)
        for (layer in CarStrip.entries) {
            val height = if (layer == CarStrip.CITY) CarScene.CITY_H else 116
            for (y in 0 until height) for (x in 0 until CarScene.PERIOD) {
                assertEquals("$layer seam at ($x,$y)", scene.stripPixel(layer, x, y, state), scene.stripPixel(layer, x + CarScene.PERIOD, y, state))
            }
        }
    }

    @Test fun characterPixelsAreClippedToTheDriverWindow() {
        val scene = CarScene()
        val source = PixelBuffer(48, 72).also { it.fill(0xFFFFFFFF.toInt()) }
        val anchors = SpriteAnchors(Point(8, 42), Point(20, 42), Point(20, 8), Point(33, 44), Point(24, 70))
        val frame = SpriteFrame(source, anchors, 100, setOf(AnimationEvent.SIT))
        val out = PixelBuffer(240, 320)
        val left = 96; val top = 176
        scene.drawCharacter(out, frame, left, top, 0, env())
        for (y in 0 until 320) for (x in 0 until 240) {
            if (out[x, y] ushr 24 != 0) assertTrue("pixel outside window ($x,$y)", scene.isDriverWindowPixel(x, y))
        }
        assertTrue(out.pixels.any { it ushr 24 != 0 })
    }

    @Test fun wheelAndShadowStayFixedWhenTheBodyBounces() {
        val scene = CarScene()
        val renderer = SceneRenderer()
        val calm = renderer.renderEmpty(scene, env(vibration = TransportVibration.NONE), 640).pixels.copyOf()
        val rough = renderer.renderEmpty(scene, env(vibration = TransportVibration.LOW), 640).pixels
        for (cx in listOf(62, 184)) for (y in 262..294) for (x in cx - 16..cx + 16) {
            val dx = x - cx; val dy = y - 278
            if (dx * dx + dy * dy <= 16 * 16 + 16) assertEquals("wheel moved at ($x,$y)", calm[y * 240 + x], rough[y * 240 + x])
        }
        assertEquals("shadow moved", calm[300 * 240 + 100], rough[300 * 240 + 100])
    }

    @Test fun trafficLightEasesThroughSlowRedAndGreenPhases() {
        val scene = CarScene()
        assertEquals(TrafficPhase.GO, scene.trafficPhase(0))
        assertEquals(TrafficPhase.SLOW, scene.trafficPhase(CarScene.TRAFFIC_GREEN_MS))
        assertEquals(TrafficPhase.STOP, scene.trafficPhase(CarScene.TRAFFIC_SLOW_END_MS))
        assertEquals(TrafficPhase.SLOW, scene.trafficPhase(CarScene.TRAFFIC_STOP_END_MS))
        assertEquals(TrafficPhase.GO, scene.trafficPhase(CarScene.TRAFFIC_ACCEL_END_MS))
        assertEquals(0L, scene.traveledClockMs(CarScene.TRAFFIC_SLOW_END_MS + 1) - scene.traveledClockMs(CarScene.TRAFFIC_SLOW_END_MS))
        assertEquals(1L, scene.traveledClockMs(CarScene.TRAFFIC_CYCLE_MS) - scene.traveledClockMs(CarScene.TRAFFIC_CYCLE_MS - 1))
    }

    @Test fun exportTwelveCarReviewMoments() {
        val renderer = SceneRenderer()
        val images = ArrayList<PixelBuffer>(12)
        val actual = linkedMapOf<String, String>()
        val periods = listOf(DayPeriod.MORNING, DayPeriod.DAY, DayPeriod.EVENING, DayPeriod.NIGHT)
        val moments = listOf(4_000L, 640L, 18_500L) // cruise, body bounce and red-light stop
        for (period in periods) for (time in moments) {
            val machine = AnimationStateMachine(Random(771))
            val visual = VisualDirector.resolve(
                HoodieActivity.COMMUTING, UserContextType.COMMUTING,
                commute = CommuteStyle.WALK, mobilityMode = MovementMode.CAR,
            )
            machine.setVisual(visual, 1)
            val sampled = machine.frame(time, 10 * 60, period)!!
            val renderFrame = sampled.copy(env = sampled.env.copy(daySeed = 771))
            val image = renderer.render(renderFrame, time)
            val name = "car/${period.name.lowercase()}_${time}ms"
            PreviewExport.save(name, image, scale = 3)
            actual[name.removePrefix("car/")] = digest(image)
            images += PixelBuffer(image.width, image.height).also { it.copyFrom(image) }
        }
        PreviewExport.sheet("car/review_gallery", images, columns = 3, scale = 2)
        assertEquals(12, images.size)
        assertFalse(images[0].pixels.contentEquals(images[3].pixels))
        val candidate = File(PreviewExport.dir, "car/car-scenes-v1-candidate.sha256")
        candidate.writeText(actual.entries.joinToString("\n", postfix = "\n") { (name, hash) -> "$name\t$hash" })
        val golden = File("src/test/resources/car-scenes-v1.sha256")
        if (System.getenv("CAR_GOLDEN_RECORD") == "1") {
            golden.parentFile?.mkdirs()
            golden.writeText(actual.entries.joinToString("\n", postfix = "\n") { (name, hash) -> "$name\t$hash" })
            return
        }
        val expected = javaClass.getResourceAsStream("/car-scenes-v1.sha256")
        assertTrue("Sem golden de carro; revise build/pixel-preview/car/review_gallery.png e rode com CAR_GOLDEN_RECORD=1", expected != null)
        val hashes = expected!!.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.associate { it.split('\t').let { (key, value) -> key to value } } }
        assertEquals(actual.keys, hashes.keys)
        actual.forEach { (name, hash) -> assertEquals("Cena de carro mudou: $name", hashes[name], hash) }
    }

    private fun digest(buffer: PixelBuffer): String {
        val bytes = ByteBuffer.allocate(8 + buffer.pixels.size * Int.SIZE_BYTES)
        bytes.putInt(buffer.width); bytes.putInt(buffer.height)
        buffer.pixels.forEach(bytes::putInt)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()))
    }
}
