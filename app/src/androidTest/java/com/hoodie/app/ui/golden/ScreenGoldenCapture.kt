package com.hoodie.app.ui.golden

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/** Captures production Compose content; expected images are committed Android test assets. */
internal object ScreenGoldenCapture {
    private const val ASSET_DIRECTORY = "goldens/screens"

    fun verify(name: String, node: SemanticsNodeInteraction) {
        require(name.matches(Regex("[a-z0-9_]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actual = node.captureToImage().asAndroidBitmap()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), ASSET_DIRECTORY)
        check(output.mkdirs() || output.isDirectory)
        if (InstrumentationRegistry.getArguments().getString("recordGoldens") == "true") {
            save(actual, File(output, "$name.png"))
            return
        }
        // Comparação pixel a pixel só vale no aparelho em que as baselines foram gravadas:
        // a rasterização de texto/emoji muda entre emuladores e versões do Android (CI em API 34).
        // Rode com -e verifyGoldens true no aparelho de referência; nos demais o caso é pulado.
        val hasBaseline = instrumentation.context.assets.list(ASSET_DIRECTORY).orEmpty().contains("$name.png")
        val verifyRequested = InstrumentationRegistry.getArguments().getString("verifyGoldens") == "true"
        if (!hasBaseline || !verifyRequested) {
            save(actual, File(output, "${name}_actual.png"))
            assumeTrue(
                if (!hasBaseline) "Sem baseline revisada para $name (grave com recordGoldens=true e revise)"
                else "Golden $name não verificado: use -e verifyGoldens true no aparelho de referência",
                false,
            )
        }
        val expected = instrumentation.context.assets.open("$ASSET_DIRECTORY/$name.png").use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "Invalid baseline: $name" }
        }
        try {
            assertEquals("$name width", expected.width, actual.width)
            assertEquals("$name height", expected.height, actual.height)
            val reference = IntArray(actual.width * actual.height)
            val rendered = IntArray(reference.size)
            expected.getPixels(reference, 0, actual.width, 0, 0, actual.width, actual.height)
            actual.getPixels(rendered, 0, actual.width, 0, 0, actual.width, actual.height)
            val diff = IntArray(reference.size)
            var changed = 0
            reference.indices.forEach { i ->
                if (reference[i] != rendered[i]) {
                    changed++
                    diff[i] = Color.MAGENTA
                } else {
                    diff[i] = Color.argb(255, Color.red(rendered[i]) / 3, Color.green(rendered[i]) / 3, Color.blue(rendered[i]) / 3)
                }
            }
            if (changed > 0) {
                save(actual, File(output, "${name}_actual.png"))
                val bitmap = Bitmap.createBitmap(diff, actual.width, actual.height, Bitmap.Config.ARGB_8888)
                try { save(bitmap, File(output, "${name}_diff.png")) } finally { bitmap.recycle() }
            }
            assertTrue("$name changed $changed pixels; actual/diff: $output", changed == 0)
        } finally {
            expected.recycle()
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
