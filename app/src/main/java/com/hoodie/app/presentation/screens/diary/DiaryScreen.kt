package com.hoodie.app.presentation.screens.diary

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.domain.diary.model.DiaryMapNode
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.screens.phoneinsights.ContextPhoneUsageSection
import com.hoodie.app.presentation.screens.phoneinsights.DiaryPhoneCard
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsScreen
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiaryScreen(vm: DiaryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = state.today
    val context = LocalContext.current
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val zone = vm.zone
    var tab by rememberSaveable { mutableStateOf(DiaryTab.GENERAL) }
    var selectedContext by remember { mutableStateOf<UserContextType?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("DIÁRIO", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        DateSelector(state.selectedDate, today, context, zone, onSelect = vm::selectDate)
        DiaryTabs(tab, onSelect = { tab = it })
        if (tab == DiaryTab.DIGITAL) {
            PhoneInsightsScreen(state.selectedDate)
        } else if (state.isLoading) {
            PixelPanel(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text("Montando seu dia…", color = HoodieColors.Muted) } }
        } else if (state.error != null) {
            PixelPanel(Modifier.fillMaxWidth()) { Text(state.error.orEmpty(), color = HoodieColors.Coral) }
        } else {
            val diary = state.diary
            if (diary == null || diary.summary.totalMs == 0L && diary.timeline.isEmpty()) {
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text("🌙  Ainda não há dados suficientes", style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                    Text("Os contextos e momentos do Hoodie registrados nesta data aparecerão aqui.", color = HoodieColors.Muted, modifier = Modifier.padding(top = 6.dp))
                    Text("O mapa reconstrói lugares conhecidos; não registra uma rota GPS.", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                SummarySection(diary.summary, onContext = if (diary.phoneInsights != null) { ctx -> selectedContext = ctx } else null)
                diary.phoneInsights?.let { DiaryPhoneCard(it, onOpen = { tab = DiaryTab.DIGITAL }) }
                val layout = remember(diary.visits) { DiaryMapLayoutEngine.layout(diary.visits) }
                DiaryMapView(layout, state.replay, zone, onNode = { selectedNodeId = it.id })
                ReplayControls(
                    state.replay,
                    onToggle = { if (state.replay.state == ReplayState.PLAYING) vm.pause() else vm.play() },
                    onReset = vm::reset,
                    onSpeed = vm::setSpeed,
                )
                if (state.replay.currentTimestamp != null) {
                    Text("▶ ${formatClock(state.replay.currentTimestamp!!, zone)}", style = MaterialTheme.typography.labelLarge, color = HoodieColors.Gold)
                    LinearProgressIndicator(progress = { state.replay.progress }, modifier = Modifier.fillMaxWidth(), color = HoodieColors.Mint, trackColor = HoodieColors.PanelLight)
                }
                TimelineSection(diary.timeline, state.replay.currentTimestamp, zone, state.replay.highlightedTimelineItemIds)
            }
        }
    }
    state.diary?.let { diary ->
        val layout = remember(diary.visits) { DiaryMapLayoutEngine.layout(diary.visits) }
        layout.node(selectedNodeId)?.let { node ->
            PlaceDetailBottomSheet(node, diary.visits, diary.timeline, zone, phone = diary.phoneInsights) { selectedNodeId = null }
        }
    }
    selectedContext?.let { ctx -> ContextPhoneSheet(ctx, state.diary?.phoneInsights) { selectedContext = null } }
}

@Composable
private fun DateSelector(date: LocalDate, today: LocalDate, context: android.content.Context, zone: ZoneId, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateChip("Hoje", date == today) { onSelect(today) }
        DateChip("Ontem", date == today.minusDays(1)) { onSelect(today.minusDays(1)) }
        DateChip(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-BR"))).uppercase(), date != today && date != today.minusDays(1)) {
            val initial = date
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth,
            ).apply {
                datePicker.maxDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            }.show()
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (selected) HoodieColors.Blue else HoodieColors.PanelLight).border(2.dp, HoodieColors.Outline).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HoodieColors.Outline else HoodieColors.Ink)
    }
}

private data class SummaryItem(val label: String, val emoji: String, val duration: Long, val context: UserContextType? = null)

@Composable
internal fun SummarySection(summary: DailySummary, onContext: ((UserContextType) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("SEU DIA")
        val items = listOf(
            SummaryItem("Casa", "🏠", summary.homeMs, UserContextType.HOME), SummaryItem("Trabalho", "🏢", summary.workMs, UserContextType.WORK),
            SummaryItem("Transporte", "🚶", summary.commutingMs, UserContextType.COMMUTING), SummaryItem("Almoço", "🍽️", summary.lunchMs, UserContextType.LUNCH),
        SummaryItem("Academia", "🏋️", summary.gymMs, UserContextType.GYM), SummaryItem("Lazer", "🎉", summary.leisureMs, UserContextType.LEISURE), SummaryItem("Outros", "📍", summary.otherMs),
        ).filter { it.duration > 0 }
        if (items.isEmpty()) PixelPanel(Modifier.fillMaxWidth()) { Text("Sem contextos registrados.", color = HoodieColors.Muted) }
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val click = if (onContext != null && item.context != null) { { onContext(item.context) } } else null
                    PixelPanel(Modifier.weight(1f), color = HoodieColors.PanelLight, onClick = click) {
                        Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                        Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
                        Text(formatDuration(item.duration), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ReplayControls(replay: ReplayUiState, onToggle: () -> Unit, onReset: () -> Unit, onSpeed: (ReplaySpeed) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (replay.state == ReplayState.PLAYING) "Pausar" else if (replay.state == ReplayState.FINISHED) "Rever dia" else "▶ Reproduzir meu dia", onToggle, modifier = Modifier.weight(1f), color = HoodieColors.Mint)
        TextButton(onClick = onReset) { Text("↺", color = HoodieColors.Hood) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("VELOCIDADE", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        ReplaySpeed.entries.forEach { speed ->
            androidx.compose.material3.FilterChip(selected = replay.speed == speed, onClick = { onSpeed(speed) }, label = { Text("${speed.multiplier}×") })
        }
    }
}

@Composable
internal fun TimelineSection(items: List<com.hoodie.app.domain.diary.model.DiaryTimelineItem>, replayAt: Long?, zone: ZoneId, highlightedIds: Set<String> = emptySet()) {
    val activeIndex = if (replayAt == null) -1 else items.indexOfFirst { it.id in highlightedIds }
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("LINHA DO TEMPO")
        if (items.isEmpty()) Text("Sem eventos registrados.", color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
            val requester = remember(item.id) { BringIntoViewRequester() }
            androidx.compose.runtime.LaunchedEffect(activeIndex, index) {
                if (index == activeIndex) requester.bringIntoView()
            }
            val highlighted = index == activeIndex
            Row(Modifier.fillMaxWidth().bringIntoViewRequester(requester).padding(vertical = 6.dp).background(if (highlighted) HoodieColors.PanelLight else Color.Transparent).padding(4.dp), verticalAlignment = Alignment.Top) {
                Text(formatClock(item.timestamp, zone), style = MaterialTheme.typography.labelSmall, color = if (highlighted) HoodieColors.Gold else HoodieColors.Muted, modifier = Modifier.width(48.dp))
                Text(if (item.actor.name == "HOODIE") "🐱" else if (item.actor.name == "SYSTEM") "⚙️" else (item.emoji ?: "📍"), modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = if (highlighted) HoodieColors.Hood else HoodieColors.Ink)
                    item.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted) }
                }
            }
            }
        }
    }
}

