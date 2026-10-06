package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.engine.diary.journey.DayClockAssembler
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.JourneyPlanConfig
import com.hoodie.app.engine.diary.journey.SyntheticJourneyDays
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/**
 * Diary Lab · Jornada 3.0: dias sintéticos (3, 9, 10, 14, 20 paradas e "tarde inteira
 * no trabalho"), slider de paradas, SINGLE/CHAPTERS forçado e Jornada × Relógio lado a lado.
 */
@Composable
fun DiaryJourneyLab(zone: ZoneId, date: LocalDate, modifier: Modifier = Modifier) {
    var kind by remember { mutableStateOf(SyntheticJourneyDays.Kind.TEN) }
    var custom by remember { mutableFloatStateOf(-1f) }
    var force by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf<DayChapter?>(null) }
    var replay by remember { mutableStateOf(ReplayUiState()) }
    var quick by remember { mutableStateOf<List<JourneyNode>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    val data = remember(kind, custom) {
        if (custom >= 0f) SyntheticJourneyDays.custom(custom.toInt(), date, zone) else SyntheticJourneyDays.build(kind, date, zone)
    }
    val config = JourneyPlanConfig(forceChapters = when (force) { 1 -> false; 2 -> true; else -> null })
    val model = remember(data, force) { JourneyOverworldModel.build(data, zone, config = config) }
    val clock = remember(data) { DayClockAssembler.build(data, zone) }

    LaunchedEffect(replay.state, replay.speed, data) {
        if (replay.state != ReplayState.PLAYING) return@LaunchedEffect
        var t = replay.currentTimestamp ?: data.startAt
        var last = android.os.SystemClock.elapsedRealtime()
        while (t < data.endAt) {
            delay(80)
            val now = android.os.SystemClock.elapsedRealtime()
            t = advanceReplay(t, now - last, replay.speed, data.endAt)
            last = now
            replay = replay.copy(currentTimestamp = t, progress = ((t - data.startAt).toFloat() / (data.endAt - data.startAt).coerceAtLeast(1)))
        }
        replay = replay.copy(state = ReplayState.FINISHED)
    }

    PixelPanel(modifier.fillMaxWidth().testTag("diary_journey_lab")) {
        SectionLabel("JORNADA 3.0 · LAB")
        ChipRow(SyntheticJourneyDays.Kind.entries.map { it.label }, if (custom < 0f) kind.ordinal else null, { kind = SyntheticJourneyDays.Kind.entries[it]; custom = -1f; manual = null; replay = ReplayUiState() })
        Text("Paradas: ${if (custom < 0f) data.nodes.size else custom.toInt()}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        Slider(value = if (custom < 0f) data.nodes.size.toFloat() else custom, onValueChange = { custom = it; manual = null }, valueRange = 0f..24f, steps = 23)
        ChipRow(listOf("Automático", "Forçar SINGLE", "Forçar CHAPTERS"), force, { force = it; manual = null })
        ChipRow(
            listOf(if (replay.state == ReplayState.PLAYING) "⏸ Pausar" else "▶ Replay", "⟲ Início", "10 min/s"), null,
            {
                when (it) {
                    0 -> replay = if (replay.state == ReplayState.PLAYING) replay.copy(state = ReplayState.PAUSED)
                    else replay.copy(state = ReplayState.PLAYING, currentTimestamp = replay.currentTimestamp?.takeIf { replay.state == ReplayState.PAUSED } ?: data.startAt)
                    1 -> replay = ReplayUiState(speed = replay.speed)
                    else -> replay = replay.copy(speed = ReplaySpeed.VERY_FAST)
                }
            },
        )
        Text("Plano: ${if (model.isChapters) "CHAPTERS" else "SINGLE"} · ${model.plan.stops.size} paradas no plano", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        // Jornada e Relógio lado a lado (mesmo replay).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                val onStop: (com.hoodie.app.domain.diary.journey.JourneyStop) -> Unit = { stop ->
                    val nodes = model.clusterNodes(stop.id)
                    if (nodes.isNotEmpty()) quick = nodes else selected = stop.id
                }
                if (model.isChapters) JourneyChaptersView(model, replay, zone, manual, isToday = false, nowMillis = data.endAt, selectedStopId = selected, onOpenChapter = { manual = it }, onStop = onStop)
                else model.single?.let { JourneyOverworldMapView(model, it, replay, zone, selected, onStop) }
            }
            Column(Modifier.weight(1f)) {
                DayClockView(clock, data, replay, zone, model.seed, selected, onStop = { selected = it }, onTick = { t -> quick = t.stopIds.mapNotNull { data.node(it) } })
            }
        }
    }
    if (quick.isNotEmpty()) QuickStopsSheet(quick, zone, onNode = { quick = emptyList(); selected = it.id }, onDismiss = { quick = emptyList() })
}
