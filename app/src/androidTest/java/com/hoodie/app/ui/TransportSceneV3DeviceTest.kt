package com.hoodie.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.art.SceneArtStore
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A arte V3 em camadas é lida do APK pelo classloader (igual à JVM) e renderiza no aparelho.
 * Rode com `am instrument` num emulador descartável — nunca `connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class TransportSceneV3DeviceTest {
    @Test fun carArtLoadsFromTheApkAndRenders() {
        val art = SceneArtStore.get("car")
        assertNotNull("car.aseprite não está no APK", art)
        assertTrue(art!!.slots.keys.containsAll(listOf("seat_feet", "steering", "wheel_0", "wheel_1")))
        val scene = SceneRegistry[com.hoodie.app.pixel.scene.SceneId.CAR]
        assertTrue("o carro deveria ser a cena em camadas", scene is com.hoodie.app.pixel.scene.CarSceneV3)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "transport-v3").apply { mkdirs() }
        DayPeriod.entries.forEach { p ->
            val img = SceneRenderer().renderEmpty(scene, SceneEnv(p, 12 * 60), 4_000L)
            val bmp = android.graphics.Bitmap.createBitmap(img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.setPixels(img.pixels, 0, img.width, 0, 0, img.width, img.height)
            File(dir, "car-${p.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("$p: cena vazia", img.pixels.toSet().size > 10)
        }
    }
}
