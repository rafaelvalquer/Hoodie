package com.hoodie.app.pixel

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.pixel.performance.SceneDiagnosticReport
import com.hoodie.app.pixel.performance.SceneDiagnostics
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.devlab.ScenePerformanceTab
import com.hoodie.app.presentation.theme.HoodieTheme
import com.hoodie.app.presentation.theme.HoodieColors
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScenePerformanceLabDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun panelExportsActualMeasurementsAndStopsOnBackground() {
        rule.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exported = File(context.cacheDir, "scene-lab-export.csv").apply { delete() }
        var launches = 0
        // Only the system destination picker is substituted. The production export callback,
        // ContentResolver writer, CSV contents and UI success state run unchanged.
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertTrue(contract is ActivityResultContracts.CreateDocument)
                assertEquals("hoodie-scenes.csv", input)
                launches++
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, Uri.fromFile(exported)) }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        SceneDiagnostics.reset()
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                HoodieTheme { Surface(color = HoodieColors.Night) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) { ScenePerformanceTab() }
                } }
            }
        }
        fun advanceUntil(predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!predicate() && SystemClock.elapsedRealtime() < deadline) {
                rule.mainClock.advanceTimeBy(50)
                SystemClock.sleep(25)
            }
            assertTrue("panel did not reach expected state", predicate())
        }
        rule.onNodeWithText("DESEMPENHO DAS CENAS").assertExists()
        rule.onNodeWithContentDescription("Iniciar benchmark das 11 cenas").performClick()
        advanceUntil { rule.onAllNodesWithContentDescription("Exportar relatório local").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(SceneDiagnostics.snapshot(SceneId.OFFICE).presentations >= 15)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (screenshot != null) {
            File(context.filesDir, "scene-performance-lab.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
        rule.onNodeWithContentDescription("Parar benchmark").performClick()
        rule.onNodeWithContentDescription("Exportar relatório local").performClick()
        advanceUntil { exported.exists() && exported.length() > 0 }
        advanceUntil { rule.onAllNodesWithText("Relatório salvo localmente.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, launches)
        val lines = exported.readLines()
        assertEquals(SceneDiagnosticReport.HEADER, lines.first())
        assertTrue(lines.size >= 2)
        assertTrue(lines[1].startsWith("OFFICE,cold,"))
        assertEquals(lines.first().split(',').size, lines[1].split(',').size)
        rule.onNodeWithText("Relatório salvo localmente.").assertExists()
        rule.onNodeWithContentDescription("Iniciar benchmark das 11 cenas").performClick()
        advanceUntil { SceneDiagnostics.snapshot(SceneId.OFFICE).presentations > 20 }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(SceneDiagnostics.enabled)
        val before = SceneDiagnostics.snapshot(SceneId.OFFICE).presentations
        SystemClock.sleep(200)
        assertEquals(before, SceneDiagnostics.snapshot(SceneId.OFFICE).presentations)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithContentDescription("Iniciar benchmark das 11 cenas").assertExists()
        val resumed = SceneDiagnostics.snapshot(SceneId.OFFICE).presentations
        SystemClock.sleep(200)
        assertEquals("background cancellation must not auto-restart", resumed, SceneDiagnostics.snapshot(SceneId.OFFICE).presentations)
    }
}
