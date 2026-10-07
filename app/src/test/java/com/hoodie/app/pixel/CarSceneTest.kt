package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.art.SceneArtStore
import com.hoodie.app.pixel.scene.CarSceneV3
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualDirector
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

    private val car get() = SceneRegistry[SceneId.CAR]

    @Test fun theCarIsTheLayeredScene() {
        assertTrue("carro em camadas (car.aseprite)", car is CarSceneV3)
    }

    @Test fun renderingIsDeterministicAtTheSameSceneClock() {
        val renderer = SceneRenderer()
        val first = renderer.renderEmpty(car, env(DayPeriod.NIGHT), 18_500L).pixels.copyOf()
        // Quadros no meio não mudam um quadro posterior no mesmo tempo de cena.
        renderer.renderEmpty(car, env(), 3_000L)
        renderer.renderEmpty(car, env(DayPeriod.EVENING), 72_000L)
        val again = renderer.renderEmpty(car, env(DayPeriod.NIGHT), 18_500L).pixels
        assertArrayEquals(first, again)
    }

    @Test fun wheelAndShadowStayFixedWhenTheBodyBounces() {
        val art = SceneArtStore.get("car")!!
        val wheels = art.slotsWithPrefix("wheel_").values
        assertEquals(2, wheels.size)
        val renderer = SceneRenderer()
        // Mesma posição de rodas (tempo 0); só a vibração muda: aro e sombra não podem mexer.
        val calm = renderer.renderEmpty(car, env(vibration = TransportVibration.NONE), 640).pixels.copyOf()
        val rough = renderer.renderEmpty(car, env(vibration = TransportVibration.LOW), 640).pixels
        wheels.forEach { c ->
            for (y in c.y - 18..c.y + 18) for (x in c.x - 18..c.x + 18) {
                val dx = x - c.x; val dy = y - c.y
                if (dx * dx + dy * dy in 100..18 * 18) assertEquals("roda mexeu em ($x,$y)", calm[y * 240 + x], rough[y * 240 + x])
            }
        }
        assertEquals("sombra mexeu", calm[291 * 240 + 120], rough[291 * 240 + 120])
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
