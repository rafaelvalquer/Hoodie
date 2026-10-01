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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryMapNode
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.presentation.components.PixelButton
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
    var selectedVisit by remember { mutableStateOf<PlaceVisit?>(null) }
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("DIÁRIO", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        DateSelector(state.selectedDate, onSelect = vm::selectDate)
        if (state.isLoading) {
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
                SummarySection(diary.summary)
                DiaryMapCard(
                    diary.map.nodes, diary.map.edges, state.replay.activeNodeId, state.replay.activeEdgeId,
                    onVisit = { index -> selectedVisit = diary.visits.getOrNull(index) },
                )
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
                TimelineSection(diary.timeline.map { item -> item.copy(title = item.title, subtitle = item.subtitle) }, state.replay.currentTimestamp, zone)
            }
        }
    }
    if (selectedVisit != null) PlaceDetailSheet(selectedVisit!!, zone) { selectedVisit = null }
}

@Composable
private fun DateSelector(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = androidx.compose.runtime.remember { LocalDate.now(ZoneId.systemDefault()) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateChip("Hoje", date == today) { onSelect(today) }
        DateChip("Ontem", date == today.minusDays(1)) { onSelect(today.minusDays(1)) }
        DateChip(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-BR"))).uppercase(), date != today && date != today.minusDays(1)) {
            val initial = date
            DatePickerDialog(
                androidx.compose.ui.platform.LocalContext.current,
                { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth,
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (selected) HoodieColors.Blue else HoodieColors.PanelLight).border(2.dp, HoodieColors.Outline).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HoodieColors.Outline else HoodieColors.Ink)
    }
}

private data class SummaryItem(val label: String, val emoji: String, val duration: Long)

@Composable
private fun SummarySection(summary: DailySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("SEU DIA")
        val items = listOf(
            SummaryItem("Casa", "🏠", summary.homeMs), SummaryItem("Trabalho", "🏢", summary.workMs),
            SummaryItem("Transporte", "🚶", summary.commutingMs), SummaryItem("Almoço", "🍽️", summary.lunchMs),
            SummaryItem("Academia", "🏋️", summary.gymMs), SummaryItem("Lazer", "🎉", summary.leisureMs), SummaryItem("Outros", "📍", summary.otherMs),
        ).filter { it.duration > 0 }
        if (items.isEmpty()) PixelPanel(Modifier.fillMaxWidth()) { Text("Sem contextos registrados.", color = HoodieColors.Muted) }
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    PixelPanel(Modifier.weight(1f), color = HoodieColors.PanelLight) {
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
private fun DiaryMapCard(nodes: List<DiaryMapNode>, edges: List<com.hoodie.app.domain.diary.model.DiaryMapEdge>, activeNodeId: String?, activeEdgeId: String?, onVisit: (Int) -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("MAPA SIMBÓLICO · SEM ROTA GPS")
        Spacer(Modifier.height(8.dp))
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("Nenhum lugar reconhecido neste dia", color = HoodieColors.Muted, textAlign = TextAlign.Center) }
        } else {
            val maxX = (nodes.maxOf { it.x } + 1).coerceAtLeast(1)
            val maxY = (nodes.maxOf { it.y } + 1).coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height((maxY * 86).coerceIn(160, 340).dp).background(Color(0xFF36465A)).border(2.dp, HoodieColors.Outline)) {
                Canvas(Modifier.fillMaxSize()) {
                    val cols = (maxX + 1).coerceAtLeast(2)
                    val rows = (maxY + 1).coerceAtLeast(2)
                    val cellW = size.width / cols
                    val cellH = size.height / rows
                    val positions = nodes.associate { it.id to Offset(cellW * (it.x + .5f), cellH * (it.y + .5f)) }
                    // Pixel blocks suggest city lots; dashed paths encode only visit sequence.
                    for (r in 0 until rows) for (c in 0 until cols) {
                        if ((r + c) % 2 == 0) drawRect(Color(0xFF3F594D), Offset(c * cellW + 3.dp.toPx(), r * cellH + 3.dp.toPx()), androidx.compose.ui.geometry.Size(cellW - 6.dp.toPx(), cellH - 6.dp.toPx()))
                    }
                    edges.forEach { edge ->
                        val from = positions[edge.fromNodeId] ?: return@forEach
                        val to = positions[edge.toNodeId] ?: return@forEach
                        val active = edge.id == activeEdgeId
                        drawLine(if (active) HoodieColors.Gold else Color(0xFFE7D99A), from, to, strokeWidth = if (active) 5.dp.toPx() else 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) , cap = StrokeCap.Square)
                    }
                    nodes.forEach { node ->
                        val point = positions[node.id] ?: return@forEach
                        val active = node.id == activeNodeId
                        val color = when (node.type.name) { "HOME" -> Color(0xFF86C99A); "WORK" -> Color(0xFF88AFE9); "RESTAURANT" -> Color(0xFFE99C7D); "GYM" -> Color(0xFFE6C86D); else -> Color(0xFFB79AE9) }
                        drawRect(HoodieColors.Outline, Offset(point.x - 17.dp.toPx(), point.y - 14.dp.toPx()), androidx.compose.ui.geometry.Size(34.dp.toPx(), 28.dp.toPx()))
                        drawRect(if (active) HoodieColors.Gold else color, Offset(point.x - 13.dp.toPx(), point.y - 10.dp.toPx()), androidx.compose.ui.geometry.Size(26.dp.toPx(), 20.dp.toPx()))
                    }
                }
                nodes.forEach { node ->
                    val cols = (maxX + 1).coerceAtLeast(2); val rows = (maxY + 1).coerceAtLeast(2)
                    val xFraction = (node.x + .5f) / cols
                    val yFraction = (node.y + .5f) / rows
                }
                MapNodeOverlay(nodes, activeNodeId, onVisit)
            }
        }
        Text("Lugares na ordem do dia · toque para ver detalhes", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MapNodeOverlay(nodes: List<DiaryMapNode>, activeNodeId: String?, onVisit: (Int) -> Unit) {
    val cols = ((nodes.maxOfOrNull { it.x } ?: 0) + 2).coerceAtLeast(2)
    val rows = ((nodes.maxOfOrNull { it.y } ?: 0) + 2).coerceAtLeast(2)
    // Float positioning in BoxWithConstraints uses the same grid cells as the Canvas.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        nodes.forEach { node ->
            val left = maxWidth * ((node.x + .5f) / cols)
            val top = maxHeight * ((node.y + .5f) / rows)
            Column(
                Modifier.padding(start = (left - 41.dp).coerceAtLeast(0.dp), top = (top - 26.dp).coerceAtLeast(0.dp))
                    .width(82.dp).height(52.dp).clickable { onVisit(node.visitIndex - 1) }.padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val emoji = when (node.type.name) { "HOME" -> "🏠"; "WORK" -> "🏢"; "RESTAURANT" -> "🍽️"; "GYM" -> "🏋️"; "SCHOOL" -> "🎓"; "MARKET" -> "🛒"; "FAMILY" -> "👪"; "LEISURE" -> "🎉"; else -> "📍" }
                Text(if (activeNodeId == node.id) "🐱 $emoji" else "$emoji ${node.visitIndex}", style = MaterialTheme.typography.labelLarge)
                Text(node.label.take(11), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReplayControls(replay: ReplayUiState, onToggle: () -> Unit, onReset: () -> Unit, onSpeed: (ReplaySpeed) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (replay.state == ReplayState.PLAYING) "Pausar" else if (replay.state == ReplayState.FINISHED) "Rever dia" else "▶ Reproduzir meu dia", onToggle, modifier = Modifier.weight(1f), color = HoodieColors.Mint)
        TextButton(onClick = onReset) { Text("↺") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("VELOCIDADE", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        ReplaySpeed.entries.forEach { speed ->
            DateChip("${speed.multiplier}×", replay.speed == speed) { onSpeed(speed) }
        }
    }
}

@Composable
private fun TimelineSection(items: List<com.hoodie.app.domain.diary.model.DiaryTimelineItem>, replayAt: Long?, zone: ZoneId) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("LINHA DO TEMPO")
        if (items.isEmpty()) Text("Sem eventos registrados.", color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
        items.forEach { item ->
            val highlighted = replayAt != null && item.timestamp <= replayAt && items.lastOrNull { it.timestamp <= replayAt }?.id == item.id
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(if (highlighted) HoodieColors.PanelLight else Color.Transparent).padding(4.dp), verticalAlignment = Alignment.Top) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceDetailSheet(visit: PlaceVisit, zone: ZoneId, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${visit.placeType.emoji} ${visit.placeName}", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
            Text(visit.placeType.label, color = HoodieColors.Muted)
            DetailRow("Chegada", formatClock(visit.arrivalAt, zone))
            DetailRow("Saída", visit.departureAt?.let { formatClock(it, zone) } ?: "Em andamento")
            DetailRow("Tempo", formatDuration(visit.durationMs))
            DetailRow("Visitas deste lugar hoje", visit.visitsCount.toString())
            visit.dominantHoodieActivity?.let { DetailRow("Hoodie", "${it.emoji} ${it.label}") }
            if (visit.relatedTimelineIds.isEmpty()) Text("Nenhum evento relacionado neste período.", color = HoodieColors.Muted)
            else Text("${visit.relatedTimelineIds.size} evento(s) da timeline neste período", color = HoodieColors.Muted)
            Text("Este ponto representa um contexto conhecido, não uma localização precisa.", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HoodieColors.Muted)
        Text(value, color = HoodieColors.Ink)
    }
}
