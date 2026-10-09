package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.engine.diary.StopPhase
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.pixel.diary.journey.JourneyViewport
import com.hoodie.app.pixel.diary.overworld.OverworldJourneyRenderer
import com.hoodie.app.pixel.diary.overworld.OverworldRenderCache
import com.hoodie.app.pixel.diary.overworld.SignpostPainter
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/**
 * Um mapa do overworld (o dia inteiro ou um capítulo aberto). O canvas vem do
 * [OverworldJourneyRenderer]; textos das placas, selos e áreas de toque são Compose,
 * posicionados pelo MESMO [JourneyViewport] — o que se vê é o que se toca.
 */
@Composable
fun JourneyOverworldMapView(
    model: JourneyOverworldModel,
    layout: OverworldLayout,
    replay: ReplayUiState,
    zone: ZoneId,
    selectedStopId: String?,
    onStop: (JourneyStop) -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = false,
    animate: Boolean = true,
    preparedCache: OverworldRenderCache? = null,
) {
    val time = if (animate) rememberDiaryMapClock(activeReplay = replay.state == ReplayState.PLAYING) else 0L
    val scene = overworldScene(model, layout, replay, selectedStopId, zone, time, framed)
    val cache = remember(layout, model.seed, preparedCache) {
        preparedCache?.takeIf { it.matches(scene) } ?: OverworldRenderCache.create(scene)
    }
    val buffer = remember(layout.width, layout.height) { PixelBuffer(layout.width, layout.height) }
    val bitmap = remember(layout.width, layout.height) { Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888) }
    BoxWithConstraints(modifier.fillMaxWidth().testTag("journey_overworld")) {
        val density = LocalDensity.current
        val viewport = with(density) { JourneyViewport(maxWidth.toPx(), layout.height, layout.width) }
        Box(Modifier.fillMaxWidth().height(with(density) { viewport.screenHeight.toDp() })) {
            Canvas(Modifier.fillMaxSize().testTag("journey_overworld_canvas")) {
                OverworldJourneyRenderer.render(scene, cache, buffer)
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
                    Text(stringResource(R.string.journey_empty), color = HoodieColors.Ink, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.background(HoodieColors.Panel).padding(8.dp))
                }
            }
            // Textos das plaquinhas (Press Start 2P), por cima da madeira pintada.
            layout.stops.forEach { s ->
                val stop = model.plan.stop(s.stopId) ?: return@forEach
                val phase = scene.replay.phases[s.stopId]
                val r = viewport.toScreen(s.sign)
                val (title, sub) = signTexts(model, stop, zone)
                with(density) {
                    Column(
                        Modifier.offset { IntOffset(r[0].toInt(), r[1].toInt()) }
                            .size(r[2].toDp(), r[3].toDp())
                            .alpha(if (phase == StopPhase.FUTURE || phase == StopPhase.GHOST) 0.6f else 1f)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, fontSize = 6.sp, lineHeight = 7.sp, color = HoodieColors.Outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, fontSize = 5.sp, lineHeight = 6.sp, color = HoodieColors.Outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Selo de retorno "2", "3"…
                    (stop as? JourneyStop.Visit)?.returnIndex?.takeIf { it > 1 }?.let { n ->
                        val (bx, by) = SignpostPainter.badgeCenter(s.sign)
                        val c = viewport.toScreen(com.hoodie.app.pixel.diary.MapPoint(bx.toFloat(), by.toFloat()))
                        Text("$n", fontSize = 5.sp, color = HoodieColors.Outline,
                            modifier = Modifier.offset { IntOffset((c.x - 3.dp.toPx()).toInt(), (c.y - 4.dp.toPx()).toInt()) })
                    }
                }
            }
            // Áreas de toque (construção + placa), ≥ 48 dp, com descrição para o TalkBack, na ordem do dia.
            layout.stops.forEach { s ->
                val stop = model.plan.stop(s.stopId) ?: return@forEach
                val r = viewport.toScreen(s.hit)
                val description = stopDescription(model, stop, zone)
                val stateText = when (scene.replay.phases[s.stopId]) {
                    StopPhase.CURRENT -> stringResource(R.string.journey_state_active)
                    StopPhase.FUTURE -> stringResource(R.string.journey_state_upcoming)
                    else -> stringResource(R.string.journey_state_visited)
                }
                with(density) {
                    Box(
                        Modifier.offset { IntOffset(r[0].toInt(), r[1].toInt()) }
                            .size(r[2].toDp().coerceAtLeast(48.dp), r[3].toDp().coerceAtLeast(48.dp))
                            .semantics {
                                contentDescription = description
                                stateDescription = stateText
                                role = Role.Button
                                selected = s.stopId == selectedStopId
                            }
                            .clickable { onStop(stop) }
                            .testTag("journey_stop_${s.stopId}"),
                    )
                }
            }
        }
    }
}

/** Título e linha de baixo da plaquinha. */
@Composable
private fun signTexts(model: JourneyOverworldModel, stop: JourneyStop, zone: ZoneId): Pair<String, String> = when (stop) {
    is JourneyStop.Visit -> OverworldText.signName(stop.label) to formatClock(stop.arrivalAt, zone)
    is JourneyStop.Ghost -> {
        val name = model.plan.visitOf(stop.ofStopId)?.label.orEmpty()
        OverworldText.signName(name) to stringResource(R.string.journey_ghost_label, "").trim()
    }
    is JourneyStop.QuickCluster -> stringResource(R.string.journey_cluster_label, stop.count) to formatClock(stop.startAt, zone)
}

/** Descrição completa (TalkBack): tipo, nome, horários e retorno. */
@Composable
fun stopDescription(model: JourneyOverworldModel, stop: JourneyStop, zone: ZoneId): String = when (stop) {
    is JourneyStop.Visit -> {
        val ret = if (stop.returnIndex > 1) stringResource(R.string.journey_stop_return, stop.returnIndex) else ""
        stringResource(R.string.journey_stop_description, stop.placeType.label, stop.label, formatClock(stop.arrivalAt, zone),
            listOf(formatDuration(stop.durationMs), ret).filter { it.isNotBlank() }.joinToString(", "))
    }
    is JourneyStop.Ghost -> stringResource(R.string.journey_ghost_description, model.plan.visitOf(stop.ofStopId)?.label.orEmpty())
    is JourneyStop.QuickCluster -> stringResource(R.string.journey_cluster_description, stop.count, formatClock(stop.startAt, zone), formatClock(stop.endAt, zone))
}
