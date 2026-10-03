package com.hoodie.app.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.components.MAP_MY_LOCATION_TAG
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.picker.PlacePickerActions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerDimensions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerLayout
import com.hoodie.app.presentation.screens.places.picker.PlacePickerTags
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import kotlin.math.abs
import android.graphics.Bitmap
import java.io.File

/**
 * A mesma tela, mas com o **MapView real** do osmdroid (AndroidView), que é o que
 * pode invadir outras áreas no aparelho. Verifica a ordem das seções, a View nativa
 * contida na área reservada e, pixel a pixel, que nada do mapa vaza para o formulário.
 */
@RunWith(AndroidJUnit4::class)
class PlacePickerRealMapTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(w: Dp, h: Dp, font: Float = 1f) {
        rule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
            WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, font)) {
                Box(Modifier.requiredSize(w, h)) {
                    HoodieTheme {
                        // map = null → MapPicker real (osmdroid).
                        PlacePickerLayout(PlacePickerState(type = PlaceType.WORK, name = "Trabalho"), PlacePickerActions())
                    }
                }
            }
        }
        rule.waitForIdle()
        // Esconder as barras do sistema dispara um novo layout; espera a altura do mapa
        // assentar (duas leituras iguais) antes de medir — evita medir um frame intermediário.
        val deadline = System.currentTimeMillis() + STABLE_TIMEOUT_MS
        var last = -1f
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(STABLE_POLL_MS)
            rule.waitForIdle()
            val now = win(PlacePickerTags.MAP).height
            if (now == last) break
            last = now
        }
    }

    /** Bounds na janela, em px. */
    private fun win(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private fun mapView(): MapView? {
        fun find(v: View): MapView? = when (v) {
            is MapView -> v
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        var found: MapView? = null
        rule.runOnUiThread { found = find(rule.activity.window.decorView) }
        return found
    }

    private fun assertRealMapContained(w: Dp, h: Dp, font: Float = 1f) {
        show(w, h, font)
        val tag = "${w}x$h/$font"
        val root = win(PlacePickerTags.ROOT)
        val search = win(PlacePickerTags.SEARCH_SECTION)
        val map = win(PlacePickerTags.MAP)
        val details = win(PlacePickerTags.DETAILS)
        val save = win(PlacePickerTags.SAVE)
        val density = rule.activity.resources.displayMetrics.density
        assertEquals("$tag altura do mapa", PlacePickerDimensions.forHeight(h, font).mapHeight.value * density, map.height, 2f)
        assertTrue("$tag SEARCH.bottom <= MAP.top", search.bottom <= map.top + 1)
        assertTrue("$tag MAP.bottom <= DETAILS.top", map.bottom <= details.top + 1)
        assertTrue("$tag SAVE abaixo dos detalhes", save.top >= details.bottom - 1)
        assertTrue("$tag SAVE.bottom <= ROOT.bottom", save.bottom <= root.bottom + 1)

        // A View nativa do osmdroid ocupa exatamente a área do mapa — nunca mais que isso.
        val view = mapView()
        assertNotNull("$tag MapView real presente", view)
        val loc = IntArray(2)
        var size = 0 to 0
        rule.runOnUiThread { view!!.getLocationInWindow(loc); size = view.width to view.height }
        assertTrue("$tag MapView.top dentro do mapa", loc[1] >= map.top - 1)
        assertTrue("$tag MapView.bottom dentro do mapa", loc[1] + size.second <= map.bottom + 1)
        assertTrue("$tag MapView.left dentro do mapa", loc[0] >= map.left - 1)
        assertTrue("$tag MapView.right dentro do mapa", loc[0] + size.first <= map.right + 1)
        rule.onNodeWithTag(MAP_MY_LOCATION_TAG).assertIsDisplayed()
        val myLocation = rule.onNodeWithTag(MAP_MY_LOCATION_TAG).fetchSemanticsNode().boundsInWindow
        assertTrue("$tag Minha localização dentro do mapa", myLocation.top >= map.top - 1 && myLocation.bottom <= map.bottom + 1)

        // Pixel a pixel: a faixa logo abaixo do mapa (padding do topo dos detalhes) é só fundo da tela.
        val rootNode = rule.onNodeWithTag(PlacePickerTags.ROOT).fetchSemanticsNode()
        val capture = rule.onNodeWithTag(PlacePickerTags.ROOT).captureToImage()
        val image = capture.toPixelMap()
        // boundsInWindow is clipped to the window; captureToImage uses the complete layer.
        // Use its unclipped origin when the requested viewport reaches a system inset.
        val y = (map.bottom - rootNode.positionInWindow.y + 6 * density).toInt().coerceIn(0, image.height - 1)
        val xs = (1..9).map { (image.width * it / 10f).toInt() }
        xs.forEach { x ->
            val px = image[x, y]
            if (!px.close(HoodieColors.Night)) {
                val diagnostics = File(rule.activity.getExternalFilesDir(null), "map-containment").apply { mkdirs() }
                File(diagnostics, "${w.value.toInt()}x${h.value.toInt()}_${(font * 100).toInt()}.png").outputStream().use {
                    capture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            assertTrue("$tag pixel ($x,$y) abaixo do mapa deveria ser o fundo, era $px", px.close(HoodieColors.Night))
        }

        // Nome e raio sempre depois do mapa.
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.NAME))
        assertTrue("$tag NAME.top >= MAP.bottom", win(PlacePickerTags.NAME).top >= map.bottom - 1)
        rule.onNodeWithTag(PlacePickerTags.DETAILS).performScrollToNode(hasTestTag(PlacePickerTags.RADIUS))
        assertTrue("$tag RADIUS.top >= MAP.bottom", win(PlacePickerTags.RADIUS).top >= map.bottom - 1)
        // O mapa não rolou junto com os detalhes.
        assertEquals("$tag mapa parado", map.top, win(PlacePickerTags.MAP).top, 1f)
    }

    @Test fun mapa_real_360x640() = assertRealMapContained(360.dp, 640.dp)
    @Test fun mapa_real_360x640_fonte130() = assertRealMapContained(360.dp, 640.dp, 1.3f)
    @Test fun mapa_real_360x800() = assertRealMapContained(360.dp, 800.dp)
    @Test fun mapa_real_393x873_tela_da_imagem() = assertRealMapContained(393.dp, 873.dp)
    @Test fun mapa_real_393x873_fonte130() = assertRealMapContained(393.dp, 873.dp, 1.3f)
    @Test fun mapa_real_411x891() = assertRealMapContained(411.dp, 891.dp)

    private fun Color.close(other: Color, tol: Float = 0.03f) =
        abs(red - other.red) < tol && abs(green - other.green) < tol && abs(blue - other.blue) < tol

    private companion object {
        const val STABLE_TIMEOUT_MS = 3_000L
        const val STABLE_POLL_MS = 150L
    }
}
