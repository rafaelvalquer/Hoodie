package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.ClockTick
import com.hoodie.app.domain.diary.journey.DayClockData
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.journey.DayClockAssembler
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.clock.DayClockRenderer
import com.hoodie.app.pixel.diary.clock.DayClockScene
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import kotlin.math.floor

/**
 * Relógio do dia: anel de 24 h, tamanho fixo. Toque num arco abre o mesmo detalhe
 * da Jornada; num tique agrupado, a lista das paradas rápidas. O replay é o mesmo
 * da Jornada (ponteiro + Hoodie no anel, futuro dessaturado).
 */
@Composable
fun DayClockView(
    data: DayClockData,
    journey: JourneyMapData,
    replay: ReplayUiState,
    zone: ZoneId,
    seed: Long,
    selectedStopId: String?,
    onStop: (String) -> Unit,
    onTick: (ClockTick) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = remember(data) { DayClockAssembler.Day(data.dayStart, data.dayEnd) }
    val replayDeg = replay.currentTimestamp?.takeIf { replay.replaying || replay.state == ReplayState.FINISHED }?.let { day.deg(it) }
    val time = rememberDiaryMapClock(activeReplay = replay.state == ReplayState.PLAYING)
    val static = remember(data, seed) { DayClockRenderer.staticLayer(data, seed) }
    val buffer = remember { PixelBuffer(DayClockRenderer.SIZE, DayClockRenderer.SIZE) }
    val bitmap = remember { Bitmap.createBitmap(DayClockRenderer.SIZE, DayClockRenderer.SIZE, Bitmap.Config.ARGB_8888) }
    val selectedArc = data.arcs.firstOrNull { it.stopId == selectedStopId }?.id
    val summary = stringResource(R.string.clock_description, data.arcs.count { it.kind == ClockArc.Kind.STAY }, data.arcs.count { it.kind == ClockArc.Kind.LEG })
    PixelPanel(modifier.fillMaxWidth().testTag("day_clock")) {
        SectionLabel(stringResource(R.string.clock_title))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val fit = widthPx / DayClockRenderer.SIZE
            val scale = if (fit >= 1f) floor(fit) else fit
            val offsetX = (widthPx - DayClockRenderer.SIZE * scale) / 2f
            fun toScreen(p: MapPoint) = IntOffset((offsetX + p.x * scale).toInt(), (p.y * scale).toInt())
            Box(Modifier.fillMaxWidth().aspectRatio(widthPx / (DayClockRenderer.SIZE * scale)).semantics { contentDescription = summary }) {
                Canvas(
                    Modifier.fillMaxSize().testTag("day_clock_canvas").pointerInput(data) {
                        detectTapGestures { o ->
                            val p = MapPoint((o.x - offsetX) / scale, o.y / scale)
                            val r = DayClockRenderer.radiusAt(p)
                            if (r < DayClockRenderer.R_INNER - 8 || r > DayClockRenderer.R_OUTER + 12) return@detectTapGestures
                            when (val hit = DayClockAssembler.hit(data, DayClockRenderer.degAt(p))) {
                                is ClockTick -> if (hit.grouped) onTick(hit) else onStop(hit.stopIds.first())
                                is ClockArc -> hit.stopId?.let(onStop)
                            }
                        }
                    },
                ) {
                    DayClockRenderer.render(DayClockScene(data, replayDeg, selectedArc, time, seed), static, buffer)
                    bitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
                    drawImage(
                        bitmap.asImageBitmap(),
                        dstOffset = IntOffset(offsetX.toInt(), 0),
                        dstSize = IntSize((DayClockRenderer.SIZE * scale).toInt(), (DayClockRenderer.SIZE * scale).toInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
                // Horas 00 / 06 / 12 / 18 fora do anel.
                val date = java.time.Instant.ofEpochMilli(data.dayStart).atZone(zone).toLocalDate()
                listOf(0, 6, 12, 18).forEach { h ->
                    val at = date.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
                    CenteredLabel(toScreen(DayClockRenderer.point(day.deg(at), DayClockRenderer.R_OUTER + 13f)), "%02d".format(h), 6)
                }
                // Rótulos dos arcos longos (raio interno).
                data.labels.forEach { l -> CenteredLabel(toScreen(DayClockRenderer.point(l.deg, 62f)), l.text, 5) }
                // Centro: lugar atual (ou "indo para X"), hora e "parada k de n".
                ClockCenter(journey, replay, zone, toScreen(MapPoint(DayClockRenderer.CX.toFloat(), DayClockRenderer.CY + 26f)))
                // Nós de acessibilidade/toque na ordem do dia: arcos de permanência e tiques.
                val items = data.arcs.filter { it.kind == ClockArc.Kind.STAY }.map { it.midDeg to (it as Any) } + data.ticks.map { it.deg to (it as Any) }
                items.sortedBy { it.first }.forEach { (deg, item) ->
                    val c = toScreen(DayClockRenderer.point(deg, DayClockRenderer.R_MID.toFloat()))
                    val description = when (item) {
                        is ClockArc -> stringResource(
                            R.string.clock_arc_description, journey.node(item.stopId)?.placeName.orEmpty(),
                            formatClock(item.startAt, zone), formatClock(item.endAt, zone), formatDuration(item.endAt - item.startAt),
                        )
                        is ClockTick -> stringResource(R.string.clock_tick_description, item.count, formatClock(item.startAt, zone), formatClock(item.endAt, zone))
                        else -> ""
                    }
                    val tag = when (item) { is ClockArc -> "clock_arc_${item.stopId}"; is ClockTick -> "clock_tick_${item.id}"; else -> "" }
                    with(density) {
                        Box(
                            Modifier.offset { IntOffset(c.x - 24.dp.roundToPx(), c.y - 24.dp.roundToPx()) }.size(48.dp)
                                .semantics { contentDescription = description; role = Role.Button }
                                .clickable {
                                    when (item) {
                                        is ClockArc -> item.stopId?.let(onStop)
                                        is ClockTick -> if (item.grouped) onTick(item) else onStop(item.stopIds.first())
                                    }
                                }
                                .testTag(tag),
                        )
                    }
                }
                if (data.isEmpty) {
                    Text(stringResource(R.string.clock_empty), color = HoodieColors.Ink, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun CenteredLabel(at: IntOffset, text: String, sizeSp: Int) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Text(
        text, fontSize = sizeSp.sp, lineHeight = (sizeSp + 1).sp, color = HoodieColors.Outline, maxLines = 1,
        modifier = Modifier.onSizeChanged { size = it }.offset { IntOffset(at.x - size.width / 2, at.y - size.height / 2) },
    )
}

@Composable
private fun ClockCenter(journey: JourneyMapData, replay: ReplayUiState, zone: ZoneId, at: IntOffset) {
    val t = replay.currentTimestamp
    val nodes = journey.nodes
    val current = t?.let { ts -> nodes.lastOrNull { it.arrivalAt <= ts && (it.departureAt ?: Long.MAX_VALUE) > ts } }
    val going = t?.let { ts -> journey.segments.firstOrNull { ts >= it.startedAt && ts < it.endedAt } }?.let { journey.node(it.toNodeId) }
    val reached = t?.let { ts -> nodes.indexOfLast { it.arrivalAt <= ts } } ?: nodes.lastIndex
    val place = when {
        going != null -> stringResource(R.string.clock_center_going, going.placeName)
        current != null -> current.placeName
        t == null -> nodes.lastOrNull()?.placeName ?: ""
        else -> ""
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(
        Modifier.onSizeChanged { size = it }.offset { IntOffset(at.x - size.width / 2, at.y) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (place.isNotBlank()) Text(JourneyText.shortName(place, 16), fontSize = 7.sp, color = HoodieColors.Outline, maxLines = 1, textAlign = TextAlign.Center)
        if (t != null) Text(formatClock(t, zone), fontSize = 6.sp, color = HoodieColors.Outline)
        if (nodes.isNotEmpty()) Text(stringResource(R.string.clock_center_stop, (reached + 1).coerceAtLeast(1), nodes.size), fontSize = 5.sp, color = HoodieColors.Outline)
    }
}
