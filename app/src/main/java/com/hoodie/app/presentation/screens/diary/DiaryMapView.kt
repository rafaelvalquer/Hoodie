package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.pixel.diary.DiaryMapLayout
import com.hoodie.app.pixel.diary.DiaryMapPlaceNode
import com.hoodie.app.pixel.diary.DiaryMapRenderer
import com.hoodie.app.pixel.diary.DiaryMapScene
import com.hoodie.app.pixel.diary.DiaryMapTiles
import com.hoodie.app.pixel.diary.DiaryMapViewport
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.screens.phoneinsights.ContextPhoneUsageSection
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.Instant
import java.time.ZoneId

const val DIARY_MAP_EMPTY_TEXT = "Ainda não tenho lugares suficientes para montar o mapa deste dia."

/** Converte o estado do replay da tela na cena do renderer. */
fun diaryMapScene(layout: DiaryMapLayout, replay: ReplayUiState, zone: ZoneId, timeMs: Long): DiaryMapScene {
    val replaying = replay.state == ReplayState.PLAYING || replay.state == ReplayState.PAUSED
    val period = replay.currentTimestamp?.takeIf { replaying }?.let { DayPeriod.of(Instant.ofEpochMilli(it).atZone(zone).hour) } ?: DayPeriod.DAY
    return DiaryMapScene(
        layout = layout,
        replaying = replaying,
        activeVisitIndex = DiaryMapScene.indexOf(replay.activeNodeId, "visit-"),
        activeTripIndex = DiaryMapScene.indexOf(replay.activeEdgeId, "edge-"),
        tripProgress = replay.edgeProgress,
        period = period,
        timeMs = timeMs,
    )
}

/**
 * Mapa do dia em pixel art. Canvas, rótulos, áreas de toque e o mini Hoodie usam
 * o mesmo [DiaryMapViewport] — o toque cai exatamente no prédio desenhado.
 */
@Composable
fun DiaryMapView(layout: DiaryMapLayout, replay: ReplayUiState, zone: ZoneId, onNode: (DiaryMapPlaceNode) -> Unit, modifier: Modifier = Modifier) {
    PixelPanel(modifier.fillMaxWidth()) {
        SectionLabel("MAPA DO DIA · SEM ROTA GPS")
        Spacer(Modifier.height(8.dp))
        var time by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            // ~10 quadros por segundo bastam para fumaça, carro e passos.
            while (true) withFrameMillis { t -> if (t / 100 != time / 100) time = t }
        }
        val scene = diaryMapScene(layout, replay, zone, time)
        val buffer = remember { PixelBuffer(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT) }
        val bitmap = remember { Bitmap.createBitmap(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT, Bitmap.Config.ARGB_8888) }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(DiaryMapTiles.WIDTH.toFloat() / DiaryMapTiles.HEIGHT)) {
            val density = LocalDensity.current
            val viewport = with(density) { DiaryMapViewport(maxWidth.toPx(), maxHeight.toPx()) }
            Canvas(Modifier.fillMaxSize()) {
                buffer.clear()
                DiaryMapRenderer.render(if (layout.isEmpty) scene.copy(period = DayPeriod.NIGHT) else scene, buffer)
                bitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
                drawImage(
                    bitmap.asImageBitmap(),
                    dstOffset = IntOffset(viewport.offsetX.toInt(), viewport.offsetY.toInt()),
                    dstSize = IntSize((DiaryMapTiles.WIDTH * viewport.scale).toInt(), (DiaryMapTiles.HEIGHT * viewport.scale).toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
            if (layout.isEmpty) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    PixelPanel(color = HoodieColors.Panel) {
                        Text(DIARY_MAP_EMPTY_TEXT, color = HoodieColors.Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // Áreas de toque e rótulos: mesma conversão do Canvas.
            layout.nodes.forEach { node ->
                val (px, py, pw, ph) = node.footprint.pixels.toList()
                val topLeft = viewport.toScreen(MapPoint(px, py))
                val bottomRight = viewport.toScreen(MapPoint(px + pw, py + ph + DiaryMapTiles.SIZE))
                with(density) {
                    Box(
                        Modifier.offset(topLeft.x.toDp(), topLeft.y.toDp())
                            .size((bottomRight.x - topLeft.x).toDp(), (bottomRight.y - topLeft.y).toDp())
                            .semantics {
                                contentDescription = "${node.label}, ${node.visitIndices.size} visita(s)"
                                role = Role.Button
                            }
                            .clickable { onNode(node) },
                    )
                    val label = viewport.toScreen(MapPoint(px + pw / 2f, py + ph + DiaryMapTiles.SIZE * 2f))
                    Text(
                        node.label.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = HoodieColors.Ink,
                        maxLines = 1,
                        modifier = Modifier.offset((label.x - 30.dp.toPx()).toDp(), (label.y - 6.dp.toPx()).toDp()).width(60.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        Text(
            if (layout.isEmpty) "O mapa aparece quando o dia tiver lugares conhecidos." else "Cada prédio é um lugar do dia · toque para ver as visitas",
            style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Detalhe de um lugar (nó): todas as visitas do dia a ele, totais e eventos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailBottomSheet(
    node: DiaryMapPlaceNode,
    visits: List<PlaceVisit>,
    timeline: List<DiaryTimelineItem>,
    zone: ZoneId,
    phone: DailyPhoneInsights? = null,
    onDismiss: () -> Unit,
) {
    val mine = node.visitIndices.mapNotNull { visits.getOrNull(it) }
    val eventIds = mine.flatMap { it.relatedTimelineIds }.toSet()
    val events = timeline.filter { it.id in eventIds }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${node.type.emoji} ${node.label}", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
            Text(node.type.label, color = HoodieColors.Muted)
            DetailLine("Tempo total", formatDuration(mine.sumOf { it.durationMs }))
            DetailLine("Visitas hoje", mine.size.toString())
            mine.mapNotNull { it.dominantHoodieActivity }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { DetailLine("Hoodie", "${it.emoji} ${it.label}") }
            SectionLabel("VISITAS")
            mine.forEachIndexed { i, v ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", color = HoodieColors.Gold, modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge)
                    Text("${formatClock(v.arrivalAt, zone)} – ${v.departureAt?.let { formatClock(it, zone) } ?: "agora"}", modifier = Modifier.weight(1f))
                    Text(formatDuration(v.durationMs), color = HoodieColors.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            val visitContext = events.firstNotNullOfOrNull { it.relatedContext }
            if (phone != null && visitContext != null) ContextPhoneUsageSection(phone, visitContext)
            SectionLabel("EVENTOS RELACIONADOS")
            if (events.isEmpty()) Text("Nenhum evento relacionado neste período.", color = HoodieColors.Muted)
            else events.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(formatClock(event.timestamp, zone), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold, modifier = Modifier.width(48.dp))
                    Text("${event.emoji ?: "📍"} ${event.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Este prédio representa um lugar conhecido, não uma localização precisa.", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HoodieColors.Muted)
        Text(value, color = HoodieColors.Ink)
    }
}
