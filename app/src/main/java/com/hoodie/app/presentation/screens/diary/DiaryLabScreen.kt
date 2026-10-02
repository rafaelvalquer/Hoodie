package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.hoodie.app.data.repository.DeviceUsageRepository
import com.hoodie.app.engine.diary.DiaryRegressionScenario
import com.hoodie.app.engine.diary.ReplayHudAssembler
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.pixel.diary.DiaryMapPerf
import com.hoodie.app.pixel.diary.DiaryMapTiles
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DiaryLabViewModel @Inject constructor(private val deviceUsage: DeviceUsageRepository) : ViewModel() {
    suspend fun storedSessions(): Int = runCatching { deviceUsage.storedSessionCount() }.getOrDefault(-1)
}

/**
 * Diary Lab: o dia de regressão da V0.2 (Casa → transporte → Trabalho → Restaurante →
 * Trabalho → Academia → Casa, com WhatsApp, Maps, Teams, YouTube, Chrome e Spotify)
 * para revisar mapa, HUD, celular por visita e o replay sem dados reais.
 */
@Composable
fun DiaryLabScreen(modifier: Modifier = Modifier, vm: DiaryLabViewModel = hiltViewModel()) {
    val zone = ZoneId.systemDefault()
    val diary = remember { DiaryRegressionScenario.create(LocalDate.now(zone), zone) }
    val layout = remember { DiaryMapLayoutEngine.layout(diary.visits) }
    val details = remember { ReplayHudAssembler.visitDetails(diary, diary.replay.endAt) }
    var replay by remember { mutableStateOf(ReplayUiState()) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var stored by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { stored = vm.storedSessions() }

    LaunchedEffect(replay.state, replay.speed) {
        if (replay.state == ReplayState.PLAYING) {
            var timestamp = replay.currentTimestamp ?: diary.replay.startAt
            var last = android.os.SystemClock.elapsedRealtime()
            while (timestamp < diary.replay.endAt) {
                delay(80)
                val now = android.os.SystemClock.elapsedRealtime()
                timestamp = advanceReplay(timestamp, now - last, replay.speed, diary.replay.endAt)
                last = now
                replay = replayAt(replay, diary, timestamp, zone)
            }
            replay = replayAt(replay.copy(state = ReplayState.FINISHED), diary, diary.replay.endAt, zone)
        }
    }

    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DIARY LAB", style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel("CENÁRIO DE REGRESSÃO V0.2")
            Text("06:50 Casa → 08:00 sai → 08:45 Trabalho → 12:00 Restaurante → 13:00 Trabalho → 18:00 Academia → 19:30 Casa (até 22h)", color = HoodieColors.Muted)
        }
        SummarySection(diary.summary)
        DiaryMapView(layout, replay, onNode = { selectedNodeId = it.id })
        DiaryReplayHud(replay.visual, zone)
        ReplayControls(
            replay,
            onToggle = {
                replay = when (replay.state) {
                    ReplayState.PLAYING -> replay.copy(state = ReplayState.PAUSED)
                    ReplayState.PAUSED -> replay.copy(state = ReplayState.PLAYING)
                    else -> replayAt(ReplayUiState(state = ReplayState.PLAYING, speed = replay.speed), diary, diary.replay.startAt, zone)
                }
            },
            onReset = { replay = ReplayUiState(speed = replay.speed) },
            onSpeed = { replay = replay.copy(speed = it) },
        )
        if (replay.currentTimestamp != null) {
            androidx.compose.material3.LinearProgressIndicator(progress = { replay.progress }, modifier = Modifier.fillMaxWidth(), color = HoodieColors.Mint)
        }
        DiaryMapPerformance(layout.nodes.size, layout.trips.size, replay, stored)
        TimelineSection(diary.timeline, replay.currentTimestamp, zone, replay.highlightedTimelineItemIds)
        PixelPanel(Modifier.fillMaxWidth()) {
            Text("O cenário é sintético e permanece no dispositivo.", style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        }
    }

    layout.node(selectedNodeId)?.let { node -> PlaceDetailBottomSheet(node, details, zone) { selectedNodeId = null } }
}

/** DIARY MAP PERFORMANCE + PHONE (Developer Lab). Atualiza a cada segundo. */
@Composable
private fun DiaryMapPerformance(nodes: Int, trips: Int, replay: ReplayUiState, storedSessions: Int) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); tick++ } }
    val active = replay.state == ReplayState.PLAYING
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("DIARY MAP PERFORMANCE")
        tick.let { _ ->
            PerfRow("Map size", "${DiaryMapTiles.WIDTH}×${DiaryMapTiles.HEIGHT}")
            PerfRow("Nodes", nodes.toString())
            PerfRow("Trips", trips.toString())
            PerfRow("Static cache", if (DiaryMapPerf.lastCacheHit) "HIT" else "MISS (${DiaryMapPerf.staticBuilds} builds)")
            PerfRow("Last render", "%.1fms".format(DiaryMapPerf.lastRenderNanos / 1_000_000.0))
            PerfRow("FPS target", DiaryMapClock.targetFps(active).toString())
            PerfRow("FPS actual", DiaryMapPerf.fpsActual.toString())
        }
        SectionLabel("PHONE")
        PerfRow("Stored sessions", if (storedSessions < 0) "—" else storedSessions.toString())
        PerfRow("Active replay app", replay.activePhoneApp?.appLabel ?: "—")
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink)
    }
}
