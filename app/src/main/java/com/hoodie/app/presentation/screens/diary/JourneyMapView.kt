package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.diary.journey.JourneyHoodieMarker
import com.hoodie.app.pixel.diary.journey.JourneyMapRenderer
import com.hoodie.app.pixel.diary.journey.JourneyPalette
import com.hoodie.app.pixel.diary.journey.JourneyRect
import com.hoodie.app.pixel.diary.journey.JourneyRenderCache
import com.hoodie.app.pixel.diary.journey.JourneySide
import com.hoodie.app.pixel.diary.journey.JourneyViewport
import com.hoodie.app.pixel.diary.journey.NodeState
import com.hoodie.app.pixel.diary.journey.SegmentState
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * Mapa do Dia 2.0 — Jornada Pixel. O canvas (céu, ruas, prédios, vida, Hoodie)
 * vem do [JourneyMapRenderer]; cartões, selos dos trechos e o balão do app são
 * Compose por cima, posicionados pelo MESMO [JourneyViewport] — o que se vê é o
 * que se toca. A camada estática fica em cache por dia; o relógio do mapa roda
 * a ~2,5 FPS parado, ~10 FPS no replay e para fora da tela.
 */
@Composable
fun JourneyMapView(
    model: JourneyMapModel,
    replay: ReplayUiState,
    zone: ZoneId,
    selectedNodeId: String?,
    onNode: (JourneyNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = model.data
    val layout = model.layout
    PixelPanel(modifier.fillMaxWidth().testTag("journey_map")) {
        JourneyHeader(model)
        Spacer(Modifier.height(8.dp))
        val time = rememberDiaryMapClock(activeReplay = replay.state == ReplayState.PLAYING)
        val scene = journeyScene(model, replay, selectedNodeId, zone, time)
        val cache = remember(layout) { JourneyRenderCache.create(layout) }
        val buffer = remember(layout.width, layout.height) { PixelBuffer(layout.width, layout.height) }
        val bitmap = remember(layout.width, layout.height) { Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val viewport = with(density) { JourneyViewport(maxWidth.toPx(), layout.height) }
            Box(Modifier.fillMaxWidth().height(with(density) { viewport.screenHeight.toDp() })) {
                Canvas(Modifier.fillMaxSize().testTag("journey_canvas")) {
                    JourneyMapRenderer.render(scene, cache, buffer)
                    bitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
                    drawImage(
                        bitmap.asImageBitmap(),
                        dstOffset = IntOffset(viewport.offsetX.toInt(), 0),
                        dstSize = IntSize((layout.width * viewport.scale).toInt(), viewport.screenHeight.toInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
                if (layout.isEmpty) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        PixelPanel(color = HoodieColors.Panel) { Text(stringResource(R.string.journey_empty), color = HoodieColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                // Selos dos trechos: meio + duração + horários, sobre a rua horizontal.
                layout.segments.forEach { seg ->
                    val segment = data.segmentAfter(seg.index) ?: return@forEach
                    val state = JourneyMapRenderer.segmentState(scene.replay, seg.index)
                    SegmentChip(segment, state, viewport, seg.chipAnchor, zone)
                }
                // Toque no prédio + plataforma (o cartão também é tocável).
                layout.nodes.forEach { n ->
                    val node = data.nodes.getOrNull(n.index) ?: return@forEach
                    val (bx, by, bw, bh) = n.building.pixels.toList()
                    val area = if (n.side == JourneySide.LEFT) JourneyRect(bx, by, n.pad.right - bx, n.pad.bottom - by)
                    else JourneyRect(n.pad.x, by, bx + bw - n.pad.x, n.pad.bottom - by)
                    val r = viewport.toScreen(area)
                    with(density) {
                        Box(
                            Modifier.offset { IntOffset(r[0].toInt(), r[1].toInt()) }
                                .size(r[2].toDp().coerceAtLeast(48.dp), r[3].toDp().coerceAtLeast(48.dp))
                                .clickable(role = Role.Button) { onNode(node) }
                                .semantics { contentDescription = node.placeName },
                        )
                    }
                }
                // Cartões das paradas.
                layout.nodes.forEach { n ->
                    val node = data.nodes.getOrNull(n.index) ?: return@forEach
                    NodeCard(node, journeyNodeState(scene, n.index), n.nodeId == selectedNodeId, viewport.toScreen(n.card), zone) { onNode(node) }
                }
                AppBalloon(scene = scene, replay = replay, viewport = viewport)
            }
        }
        Text(stringResource(R.string.journey_footer), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

/** "JORNADA DO DIA" + paradas · retornos · tempo em trânsito. */
@Composable
private fun JourneyHeader(model: JourneyMapModel) {
    val data = model.data
    SectionLabel(stringResource(R.string.journey_title))
    if (data.isEmpty) return
    FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HeaderChip(pluralStringResource(R.plurals.journey_stops, data.nodes.size, data.nodes.size))
        val returns = data.nodes.count { it.isReturn }
        if (returns > 0) HeaderChip(pluralStringResource(R.plurals.journey_returns, returns, returns))
        if (data.travelMs > 0) HeaderChip(stringResource(R.string.journey_travel_total, formatDuration(data.travelMs)))
    }
}

@Composable
private fun HeaderChip(text: String) {
    Box(Modifier.background(HoodieColors.PanelLight).border(2.dp, HoodieColors.Outline).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1)
    }
}

@Composable
private fun NodeCard(node: JourneyNode, state: NodeState, selected: Boolean, rect: FloatArray, zone: ZoneId, onClick: () -> Unit) {
    val density = LocalDensity.current
    val stateLabel = stringResource(
        when (state) {
            NodeState.ACTIVE -> R.string.journey_state_active
            NodeState.UPCOMING -> R.string.journey_state_upcoming
            NodeState.VISITED -> R.string.journey_state_visited
        },
    )
    val times = if (node.departureAt == null) stringResource(R.string.journey_node_now, com.hoodie.app.core.time.formatClock(node.arrivalAt, zone))
    else JourneyText.times(node, zone)
    val description = stringResource(R.string.journey_node_description, node.visitIndex + 1, node.placeName, times, formatDuration(node.durationMs))
    val border = when {
        selected -> Color(JourneyPalette.SELECTED)
        state == NodeState.ACTIVE -> HoodieColors.Gold
        else -> HoodieColors.Outline
    }
    with(density) {
        Column(
            Modifier
                .offset { IntOffset(rect[0].toInt(), rect[1].toInt()) }
                .width(rect[2].toDp())
                .heightIn(min = rect[3].toDp().coerceAtLeast(48.dp))
                .alpha(if (state == NodeState.UPCOMING) 0.55f else 1f)
                .background(if (state == NodeState.ACTIVE) HoodieColors.PanelLight else HoodieColors.Panel)
                .border(if (selected || state == NodeState.ACTIVE) 2.dp else 1.dp, border)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    stateDescription = stateLabel
                    this.selected = selected
                    role = Role.Button
                }
                .clickable(onClick = onClick)
                .testTag("journey_card_${node.visitIndex}")
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${node.placeType.emoji} ${JourneyText.shortName(node.placeName).uppercase()}",
                    style = MaterialTheme.typography.labelLarge, color = if (state == NodeState.ACTIVE) HoodieColors.Gold else HoodieColors.Hood,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (node.isReturn) {
                    Text(
                        stringResource(R.string.journey_return_badge, node.returnNumber),
                        style = MaterialTheme.typography.labelSmall, color = HoodieColors.Outline, maxLines = 1,
                        modifier = Modifier.background(Color(JourneyPalette.SELECTED)).padding(horizontal = 3.dp),
                    )
                }
            }
            Text("$times · ${formatDuration(node.durationMs)}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SegmentChip(segment: com.hoodie.app.domain.diary.model.JourneySegment, state: SegmentState, viewport: JourneyViewport, anchor: MapPoint, zone: ZoneId) {
    val density = LocalDensity.current
    var size by remember(segment.id) { mutableStateOf(IntSize.Zero) }
    val center = viewport.toScreen(anchor)
    val mode = segment.movementMode
    val modeLabel = mode?.label ?: stringResource(R.string.journey_segment_unknown)
    val times = JourneyText.segmentTimes(segment, zone)
    val description = stringResource(
        R.string.journey_segment_description, modeLabel, formatDuration(segment.durationMs),
        com.hoodie.app.core.time.formatClock(segment.startedAt, zone), com.hoodie.app.core.time.formatClock(segment.endedAt, zone),
    )
    val style = JourneyPalette.route(mode)
    Column(
        Modifier
            .wrapContentSize()
            .onSizeChanged { size = it }
            .offset { IntOffset((center.x - size.width / 2f).toInt(), (center.y - size.height / 2f).toInt()) }
            .alpha(if (state == SegmentState.UPCOMING) 0.5f else 1f)
            .background(HoodieColors.Panel)
            .border(2.dp, if (state == SegmentState.ACTIVE) HoodieColors.Gold else Color(style.color))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("journey_segment_${segment.index}")
            .padding(horizontal = 5.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(JourneyText.segmentChip(segment), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1)
        Text(times, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, maxLines = 1)
    }
}

/** Balão do app sobre o Hoodie quando uma sessão começa no replay; some depois de [APP_BADGE_MS]. */
@Composable
private fun AppBalloon(scene: com.hoodie.app.pixel.diary.journey.JourneyScene, replay: ReplayUiState, viewport: JourneyViewport) {
    val app = replay.activePhoneApp
    var badgeFor by remember { mutableStateOf<String?>(null) }
    val sessionKey = app?.let { "${it.packageName}@${it.startedAt}" }
    LaunchedEffect(sessionKey, replay.state) {
        if (sessionKey != null && replay.state == ReplayState.PLAYING) {
            badgeFor = sessionKey
            delay(APP_BADGE_MS)
            if (badgeFor == sessionKey) badgeFor = null
        } else if (sessionKey == null) badgeFor = null
    }
    val marker = JourneyMapRenderer.marker(scene) ?: return
    if (app == null || badgeFor != sessionKey) return
    val at = viewport.toScreen(MapPoint(marker.position.x, marker.position.y - JourneyHoodieMarker.heightOf(marker) - 2))
    var size by remember { mutableStateOf(IntSize.Zero) }
    Row(
        Modifier
            .onSizeChanged { size = it }
            .offset { IntOffset((at.x - size.width / 2f).coerceAtLeast(0f).toInt(), (at.y - size.height).coerceAtLeast(0f).toInt()) }
            .border(2.dp, HoodieColors.Outline).background(HoodieColors.Panel).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppBadge(AppIconSource.Installed(app.packageName), app.category, size = 14.dp, showCategoryDot = false)
        Text(app.appLabel.take(10), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1)
    }
}

/** [JORNADA] [MAPA ANTIGO]: a Jornada é o padrão; o clássico fica como alternativa enquanto a nova é validada. */
@Composable
fun DiaryMapModeToggle(selected: DiaryMapMode, onSelect: (DiaryMapMode) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.journey_mode_label)
    Row(modifier.fillMaxWidth().border(2.dp, HoodieColors.Outline).semantics { contentDescription = label }) {
        DiaryMapMode.entries.forEach { mode ->
            val on = mode == selected
            val text = stringResource(if (mode == DiaryMapMode.JOURNEY) R.string.journey_mode_journey else R.string.journey_mode_classic)
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (on) HoodieColors.Gold else HoodieColors.Panel)
                    .semantics { this.selected = on; role = Role.Tab }
                    .clickable { onSelect(mode) }
                    .testTag("map_mode_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (on) HoodieColors.Outline else HoodieColors.Muted)
            }
        }
    }
}
