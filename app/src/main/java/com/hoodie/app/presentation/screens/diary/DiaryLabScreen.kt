package com.hoodie.app.presentation.screens.diary

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
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
    suspend fun storedSessions(): Int = try { deviceUsage.storedSessionCount() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (error: Exception) { android.util.Log.e("DiaryLabViewModel", "Failed to count stored sessions", error); -1 }
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
        Text(stringResource(R.string.ui_diary_lab_screen_1), style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.ui_diary_lab_screen_2))
            Text(stringResource(R.string.ui_diary_lab_screen_3), color = HoodieColors.Muted)
        }
        SummarySection(diary.summary)
        DiaryJourneyLab(zone, LocalDate.now(zone))
        // Mapa clássico: só aqui durante a transição para a Jornada 3.0 (plano §12).
        SectionLabel("MAPA CLÁSSICO · TRANSIÇÃO")
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
            Text(stringResource(R.string.ui_diary_lab_screen_4), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        }
    }

    layout.node(selectedNodeId)?.let { node -> PlaceDetailBottomSheet(node, details, zone) { selectedNodeId = null } }
}

/** DIARY MAP PERFORMANCE + PHONE (Developer Lab). Atualiza a cada segundo. */
@Composable
private fun DiaryMapPerformance(nodes: Int, trips: Int, replay: ReplayUiState, storedSessions: Int) {
    val uiTextContext = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); tick++ } }
    val active = replay.state == ReplayState.PLAYING
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ui_diary_lab_screen_5))
        tick.let { _ ->
            PerfRow("Map size", "${DiaryMapTiles.WIDTH}×${DiaryMapTiles.HEIGHT}")
            PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_1), nodes.toString())
            PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_2), trips.toString())
            PerfRow("Static cache", if (DiaryMapPerf.lastCacheHit) "HIT" else "MISS (${DiaryMapPerf.staticBuilds} builds)")
            PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_3), uiTextContext.getString(R.string.ui_extra_diary_lab_screen_4).format(DiaryMapPerf.lastRenderNanos / 1_000_000.0))
            PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_5), DiaryMapClock.targetFps(active).toString())
            PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_6), DiaryMapPerf.fpsActual.toString())
        }
        SectionLabel(stringResource(R.string.ui_diary_lab_screen_6))
        PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_7), if (storedSessions < 0) "—" else storedSessions.toString())
        PerfRow(uiTextContext.getString(R.string.ui_extra_diary_lab_screen_8), replay.activePhoneApp?.appLabel ?: "—")
    }
}

@Composable
private fun PerfRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink)
    }
}
