package com.hoodie.app.pixel

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.performance.*
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.presentation.components.HoodieSceneView
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real production frame callback, bitmap, and renderer scheduling. No synthetic FPS estimate. */
class ScenePerformanceDeviceTest {
    @Test fun elevenScenesPublishFramesAndReportAllStages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val label = InstrumentationRegistry.getArguments().getString("benchmarkLabel") ?: "latest"
        val selectedScene = InstrumentationRegistry.getArguments().getString("benchmarkScene")
        val cases = SceneBenchmarkScenes.cases.filter { selectedScene == null || it.scene.name == selectedScene }
        require(cases.isNotEmpty())
        val warmFrames = InstrumentationRegistry.getArguments().getString("warmFrames")?.toIntOrNull() ?: 45
        val steadyFrames = InstrumentationRegistry.getArguments().getString("steadyFrames")?.toIntOrNull() ?: 90
        require(warmFrames in 15..600 && steadyFrames in 15..600)
        require(label.matches(Regex("[a-z0-9-]+")))
        val report = File(instrumentation.targetContext.filesDir, "scene-performance-$label.csv")
        // Keep a copy in UTP's additional output directory so it survives app cleanup after tests.
        val runnerOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
        val rows = mutableListOf(SceneDiagnosticReport.HEADER)
        val published = AtomicInteger()
        val starts = AtomicInteger()
        val requests = MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1)
        var visual by mutableStateOf(SceneBenchmarkScenes.cases.first().visual())
        var period by mutableStateOf(DayPeriod.DAY)
        var speed by mutableFloatStateOf(1f)
        var touch by mutableStateOf(false)
        SceneDiagnostics.acquire().use {
            ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
                activity.onActivity { a -> a.setContentView(ComposeView(a).apply {
                    setContent {
                        HoodieSceneView(visual, Modifier.size(240.dp, 320.dp), greet = false, randomSeed = 26,
                            animationStepMs = 33, clockMinuteOverride = if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60,
                            periodOverride = period, speed = speed, interactionsEnabled = touch,
                            interactionRequests = requests, onInteractionStarted = { starts.incrementAndGet() },
                            onScenePresented = { if (it == visual.scene) published.incrementAndGet() })
                    }
                }) }
                fun awaitFrames(count: Int) {
                    val first = published.get()
                    val deadline = SystemClock.elapsedRealtime() + 60_000
                    while (published.get() - first < count && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
                    assertTrue("frame publication stalled", published.get() - first >= count)
                }
                for (case in cases) {
                    activity.onActivity { visual = case.visual(); period = DayPeriod.DAY; speed = 1f; touch = false }
                    SceneDiagnostics.stopPresentation(case.scene)
                    fun sample(stage: String, count: Int) {
                        SceneDiagnostics.snapshot(case.scene)
                        awaitFrames(count)
                        val s = SceneDiagnostics.snapshot(case.scene)
                        assertTrue("${case.scene} $stage never rendered", s.frames > 0)
                        assertTrue(s.activeNpcs >= s.visibleNpcs)
                        val row = SceneDiagnosticReport.row(stage, s)
                        rows += row
                        println("SCENE_DEVICE_CSV $row")
                        val csv = rows.joinToString("\n", postfix = "\n")
                        report.writeText(csv)
                        runnerOutput?.let { directory ->
                            check(directory.isDirectory || directory.mkdirs()) { "Cannot create UTP output directory: $directory" }
                            File(directory, report.name).writeText(csv)
                        }
                        println("SCENE_DEVICE ${case.scene} $stage fps=${s.fps} renderP95=${s.renderP95Ms} bitmap=${s.bitmapP95Ms}")
                    }
                    sample("cold", 15)
                    sample("warmup", warmFrames)
                    sample("steady", steadyFrames)
                    activity.onActivity { speed = 30f }; sample("story", 90)
                    activity.onActivity { speed = 1f; period = DayPeriod.NIGHT }; sample("night", 90)
                    activity.onActivity { visual = VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME, now = 0, personality = null) }
                    awaitFrames(90)
                    rows += SceneDiagnosticReport.row("exit", SceneDiagnostics.snapshot(com.hoodie.app.pixel.scene.SceneId.HOME))
                    activity.onActivity { visual = case.visual(); period = DayPeriod.DAY }
                    sample("return", 90)
                    activity.onActivity {
                        touch = true
                        visual = case.visual().copy(actions = listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000)),
                            approach = emptyList(), enter = emptyList(), exit = emptyList(), steadyFlags = emptySet())
                    }
                    sample("safe-pause", 90)
                    val before = starts.get()
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    while (starts.get() == before && SystemClock.elapsedRealtime() < deadline) {
                        val now = SystemClock.uptimeMillis()
                        requests.tryEmit(InteractionRequest(InteractionResponse("benchmark", listOf(AnimationId.PET_HEAD, AnimationId.PURR), null, 0),
                            case.scene, 1, requestId = now, createdAtMs = now, expiresAtMs = now + 2_000))
                        SystemClock.sleep(1_000)
                    }
                    assertTrue("${case.scene} must really present touch", starts.get() > before)
                    sample("touch", 90)
                }
            }
        }
        assertEquals(1 + cases.size * 9, rows.size)
        val environment = "model=${android.os.Build.MODEL}\nsdk=${android.os.Build.VERSION.SDK_INT}\nabis=${android.os.Build.SUPPORTED_ABIS.joinToString()}\nlabel=$label\nscenes=${cases.joinToString { it.scene.name }}\nwarmFrames=$warmFrames; steadyFrames=$steadyFrames\nseed=20026; random=26; variant=0; place=42\n" +
            "Paced Canvas, production renderAndPresent path. Rolling windows retain at most 180 frames.\n"
        File(report.parentFile, "scene-performance-$label-environment.txt").writeText(environment)
        runnerOutput?.let { File(it, "scene-performance-$label-environment.txt").writeText(environment) }
        println("SCENE_DEVICE_REPORT ${report.absolutePath}")
    }
}
