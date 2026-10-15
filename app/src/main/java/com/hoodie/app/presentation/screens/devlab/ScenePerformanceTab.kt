package com.hoodie.app.presentation.screens.devlab

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionRequest
import com.hoodie.app.engine.interaction.InteractionResponse
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.performance.*
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.presentation.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.Locale

/** Foreground-only benchmark through the production Canvas and bitmap publication path. */
@Composable
fun ScenePerformanceTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(DayPeriod.DAY) }
    var speed by remember { mutableFloatStateOf(1f) }
    var visual by remember { mutableStateOf(SceneBenchmarkScenes.cases.first().visual()) }
    var stage by remember { mutableStateOf("idle") }
    var result by remember { mutableStateOf("") }
    var publications by remember { mutableIntStateOf(0) }
    var starts by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf(SceneDiagnostics.snapshot(visual.scene)) }
    val rows = remember { mutableStateListOf<String>() }
    val requests = remember { MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1) }
    val benchmarkJob = remember { java.util.concurrent.atomic.AtomicReference<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        var lease: AutoCloseable? = if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) SceneDiagnostics.acquire() else null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                foreground = true
                if (lease == null) lease = SceneDiagnostics.acquire()
            }
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                benchmarkJob.getAndSet(null)?.cancel()
                running = false; preview = false; speed = 1f; stage = "idle"
                lease?.close(); lease = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); benchmarkJob.getAndSet(null)?.cancel(); lease?.close() }
    }
    LaunchedEffect(visual.scene, foreground) {
        if (!foreground) return@LaunchedEffect
        while (isActive && foreground) { snapshot = SceneDiagnostics.snapshot(visual.scene); delay(500) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val data = (listOf(SceneDiagnosticReport.HEADER) + rows.toList()).joinToString("\n", postfix = "\n")
            result = runCatching {
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(data) } }
                "Relatório salvo localmente."
            }.getOrElse { "Falha ao salvar: ${it.message}" }
        }
    }
    LaunchedEffect(run, running) {
        if (!running) return@LaunchedEffect
        val ownJob = requireNotNull(currentCoroutineContext()[Job])
        benchmarkJob.set(ownJob)
        rows.clear(); result = ""
        try {
            for ((index, case) in SceneBenchmarkScenes.cases.withIndex()) {
                selected = index; visual = case.visual(); period = DayPeriod.DAY; speed = 1f; preview = true
                suspend fun sample(name: String, frames: Int, touch: Boolean = false) {
                    stage = name
                    val first = publications
                    val firstStarts = starts
                    // An explicit snapshot establishes the ART process counter baseline.
                    SceneDiagnostics.snapshot(visual.scene)
                    withTimeout(30_000) {
                        var requestedAt = 0L
                        while (publications - first < frames) {
                            if (touch && SystemClock.uptimeMillis() - requestedAt > 1_000) {
                                requestedAt = SystemClock.uptimeMillis()
                                requests.tryEmit(InteractionRequest(InteractionResponse("benchmark", listOf(AnimationId.PET_HEAD, AnimationId.PURR), null, 0),
                                    case.scene, run.toLong(), requestId = requestedAt, createdAtMs = requestedAt, expiresAtMs = requestedAt + 2_000))
                            }
                            delay(25)
                        }
                    }
                    if (touch) check(starts > firstStarts) { "${case.scene}: reação não apresentada" }
                    rows += SceneDiagnosticReport.row(name, SceneDiagnostics.snapshot(visual.scene))
                }
                sample("cold", 15)
                sample("warmup", 45)
                sample("steady", 90)
                speed = 30f; sample("story", 90); speed = 1f
                period = DayPeriod.NIGHT; sample("night", 90)
                visual = VisualDirector.resolve(com.hoodie.app.core.model.HoodieActivity.IDLE, com.hoodie.app.core.model.UserContextType.HOME, now = 0, personality = null)
                sample("exit", 60)
                visual = case.visual(); period = DayPeriod.DAY; sample("return", 90)
                visual = case.visual().copy(actions = listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000)),
                    enter = emptyList(), exit = emptyList(), approach = emptyList(), steadyFlags = emptySet())
                // Let the real transition finish before requesting a reaction.
                sample("safe-pause", 60)
                sample("touch", 90, touch = true)
            }
            result = "Benchmark concluído: ${rows.size} amostras. Exporte o relatório para comparar."
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            result = "Benchmark interrompido: ${failure.message}"
        } finally {
            if (benchmarkJob.compareAndSet(ownJob, null)) { preview = false; running = false; stage = "idle"; speed = 1f }
        }
    }
    fun n(value: Double?) = value?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
    SectionLabel("Desempenho das Cenas")
    Text("Diagnóstico local com dados fictícios. A coleta termina ao sair desta seção.")
    if (!running) ChipRow(SceneBenchmarkScenes.cases.map { it.scene.name }, selected, {
        selected = it; visual = SceneBenchmarkScenes.cases[it].visual(); preview = true
    })
    PixelButton(if (running) "Parar benchmark" else "Iniciar benchmark das 11 cenas", onClick = {
        if (running) running = false else { run++; running = true }
    })
    if (rows.isNotEmpty()) PixelButton("Exportar relatório local", onClick = { export.launch("hoodie-scenes.csv") })
    if (preview) key(run) {
        HoodieSceneView(visual, Modifier.fillMaxWidth().height(260.dp), greet = false, periodOverride = period, speed = speed,
            animationStepMs = 33, clockMinuteOverride = if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60,
            interactionsEnabled = true, interactionRequests = requests, randomSeed = 26,
            onScenePresented = { if (it == visual.scene) publications++ }, onInteractionStarted = { starts++ })
    }
    Text("${visual.scene} · $stage · FPS ${n(snapshot.fps)} · P95 ${n(snapshot.renderP95Ms)} ms · P99 ${n(snapshot.renderP99Ms)} ms")
    Text("Intervalo P95 ${n(snapshot.intervalP95Ms)} ms · bitmap ${n(snapshot.bitmapP95Ms)} ms · NPCs ${snapshot.activeNpcs} ativos / ${snapshot.visibleNpcs} visíveis")
    Text("NPCs P95 ${n(snapshot.stageP95Ms[3])} ms · sessões ${snapshot.sessionsCreated} · timelines ${snapshot.timelineRebuilds} · snapshots ${snapshot.snapshotsBuilt}")
    Text("Heap ${snapshot.heapBytes / 1_048_576} MiB · alocação ART ${snapshot.processAllocatedBytes ?: "indisponível"} · GC ${snapshot.processGcCount ?: "—"} · thread ${snapshot.renderThread}")
    if (result.isNotBlank()) Text(result)
}
