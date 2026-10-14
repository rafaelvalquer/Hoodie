package com.hoodie.app.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.*
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.engine.hoodie.HoodieSnapshot
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.theme.HoodieTheme
import com.hoodie.app.presentation.theme.HoodieColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.time.ZoneId

/** Production Home viewport matrix; local configuration matches the requested logical dimensions. */
@RunWith(Parameterized::class)
class HoodieInteractionUiMatrixTest(private val width: Int, private val height: Int, private val fontScale: Float, private val seated: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun sceneBubbleAndContextRemainReachable() {
        val activity = if (seated) HoodieActivity.COFFEE else HoodieActivity.RESTING
        val animation = if (seated) AnimationId.COFFEE_SIT else AnimationId.IDLE
        val context = ContextEvent(42, UserContextType.WORK, 1_000, null, .94f, 8, ContextSource.GEOFENCE)
        val snapshot = HoodieSnapshot(HoodieState(activity, 1_000, null, Needs(), 1_000, UserContextType.WORK), Needs(), emptyList())
        val homeNow = HomeNowAssembler.assemble(null, context, "Escritório", null, null, 4_000)
        rule.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = width; screenHeightDp = height }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale), LocalPixelRenderFrame provides PixelRenderFrame()) {
                HoodieTheme {
                    Box(Modifier.requiredSize(width.dp, height.dp).background(HoodieColors.Night).testTag("interaction_matrix")) {
                        HomeContent(HomeUiState(loading = false, now = 4_000, context = context, snapshot = snapshot,
                            visual = VisualState(SceneId.OFFICE, SpotId.DESK, listOf(MicroAction(animation, 1)),
                                steadyFlags = if (seated) setOf(SceneFlag.MUG_IN_HAND, SceneFlag.CHAIR_OCCUPIED) else emptySet()),
                            dialogue = "Uma pausa para carinho não faz mal."),
                            busy = false, zone = ZoneId.of("UTC"), onOpen = {}, homeNow = homeNow, interactionsEnabled = true)
                    }
                }
            }
        }
        diagnostic()
        rule.onNodeWithTag("hoodie_interaction_target").assertIsDisplayed()
        rule.onNodeWithTag("home_dialogue").assertIsDisplayed()
        capture("scene")
        rule.onNodeWithTag("home_user_context").performScrollTo().assertIsDisplayed()
        capture("context")
    }

    private fun capture(part: String) {
        val output = File(rule.activity.getExternalFilesDir(null), "hoodie-interactions/matrix-${width}x${height}-${(fontScale * 100).toInt()}-${if (seated) "seated" else "standing"}-$part.png")
        output.parentFile?.mkdirs()
        val bitmap = rule.onNodeWithTag("interaction_matrix").captureToImage().asAndroidBitmap()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun diagnostic() {
        val prefix = "debug-${width}x${height}-${(fontScale * 100).toInt()}-${if (seated) "seated" else "standing"}"
        val folder = File(rule.activity.getExternalFilesDir(null), "hoodie-interactions").apply { mkdirs() }
        File(folder, "$prefix.txt").writeText(
            "windowFocus=${rule.activity.window.decorView.hasWindowFocus()} shown=${rule.activity.window.decorView.isShown}\n" +
                rule.onRoot(useUnmergedTree = true).printToString(),
        )
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (screenshot != null) {
            File(folder, "$prefix.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1} font={2} seated={3}")
        fun cases(): List<Array<Any>> = listOf(360 to 640, 360 to 800, 411 to 891).flatMap { (w, h) ->
            listOf(1f, 1.3f).flatMap { font -> listOf(false, true).map { seated -> arrayOf<Any>(w, h, font, seated) } }
        }
    }
}
