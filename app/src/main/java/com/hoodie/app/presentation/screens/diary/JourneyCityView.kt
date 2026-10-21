package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.engine.diary.city.CityHoodieState
import com.hoodie.app.engine.diary.city.CityStay
import com.hoodie.app.engine.diary.city.CityTown
import com.hoodie.app.engine.diary.city.CityTrip
import com.hoodie.app.pixel.diary.city.CityAmbient
import com.hoodie.app.pixel.diary.city.CityRenderer
import com.hoodie.app.pixel.diary.city.CityScene
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt

private enum class CityCameraMode { FOLLOW, OVERVIEW, PLACE }

/**
 * Jornada como uma pequena aventura numa cidade pixel art (perspectiva 3/4 de RPG clássico):
 * o Hoodie anda pelas calçadas entre os lugares do dia e a câmera o acompanha. Os lugares
 * repetidos são o mesmo prédio. O relógio vem do replay do Diário; o cenário (moradores,
 * trânsito, pombos) anda sozinho enquanto a tela está visível.
 */
@Composable
fun JourneyCityView(
    scene: CityScene,
    replay: ReplayUiState,
    zone: ZoneId,
    onOpenNode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = scene.plan
    val replayState by rememberUpdatedState(replay)
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember { isReduceMotion(context) }
    val fixedFrame = LocalPixelRenderFrame.current
    val ambient = remember(scene) { CityAmbient() }
    val renderer = remember { CityRenderer() }
    var mode by remember(scene) { mutableStateOf(CityCameraMode.FOLLOW) }
    var selectedSlot by remember(scene) { mutableStateOf<String?>(null) }
    var cardStay by remember(scene) { mutableStateOf<CityStay?>(null) }
    var cardUntil by remember(scene) { mutableLongStateOf(0L) }
    var hoodie by remember(scene) { mutableStateOf(plan.hoodieAt(replay.currentTimestamp ?: plan.startAt, replay.speed.minutesPerSecond)) }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(CITY_HEIGHT).clip(RoundedCornerShape(6.dp)).background(HoodieColors.Night)
            .testTag("journey_city").semantics { contentDescription = "Jornada do dia na cidade" },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { CITY_HEIGHT.roundToPx() }
        val scale = maxOf(2, widthPx / LOGICAL_WIDTH)
        val viewW = (widthPx + scale - 1) / scale; val viewH = (heightPx + scale - 1) / scale
        val buffer = remember(viewW, viewH) { PixelBuffer(viewW, viewH) }
        val bitmap = remember(viewW, viewH) { Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        var frame by remember { mutableIntStateOf(0) }
        val cam = remember(scene, viewW, viewH) { floatArrayOf(hoodie.x, hoodie.y - 10) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(scene, viewW, viewH, lifecycle, fixedFrame,
            replay.takeIf { fixedFrame != null }, mode.takeIf { fixedFrame != null },
            selectedSlot.takeIf { fixedFrame != null }) {
            if (fixedFrame != null) {
                // Preview/capture uses the same renderer and ambient simulation, but
                // owns no frame-clock coroutine. Rebuild from the seeded initial state
                // so recomposition, scrolling and camera toggles cannot advance time.
                val r = replayState
                val ts = r.currentTimestamp ?: plan.startAt
                val h = plan.hoodieAt(ts, r.speed.minutesPerSecond)
                val previewAmbient = CityAmbient()
                val camera = floatArrayOf(h.x, h.y - 10)
                val (tx, ty) = target(mode, selectedSlot, h, r.state == ReplayState.PLAYING)
                var elapsed = 0L
                while (elapsed < fixedFrame.animationMillis) {
                    val stepMs = minOf(33L, fixedFrame.animationMillis - elapsed)
                    elapsed += stepMs
                    val step = stepMs / 1_000f
                    previewAmbient.update(step, elapsed / 1_000f, if (h.inside != null) -999f else h.x, if (h.inside != null) -999f else h.y)
                    previewAmbient.updateSmoke(step, scene.buildings)
                    val k = if (reduceMotion) 1f else 1f - exp(-step * if (h.walking) 3.2f else 2.2f)
                    camera[0] += (tx - camera[0]) * k; camera[1] += (ty - camera[1]) * k
                }
                val seconds = fixedFrame.animationMillis / 1_000f
                if (mode == CityCameraMode.OVERVIEW) {
                    renderer.renderOverview(buffer, scene, h, ts, seconds)
                } else {
                    camera[0] = camera[0].coerceIn(viewW / 2f, CityTown.WW - viewW / 2f)
                    camera[1] = camera[1].coerceIn(viewH / 2f, CityTown.WH - viewH / 2f)
                    val minute = Instant.ofEpochMilli(ts).atZone(zone).let { it.hour * 60 + it.minute }
                    renderer.render(buffer, scene, previewAmbient, h,
                        (camera[0] - viewW / 2f).roundToInt(), (camera[1] - viewH / 2f).roundToInt(),
                        seconds, minute, selectedSlot, reduceMotion)
                }
                hoodie = h
                bitmap.setPixels(buffer.pixels, 0, viewW, 0, 0, viewW, viewH)
                frame++
                return@LaunchedEffect
            }
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = 0L; var acc = 0f; var seconds = 0f; var lastStay: CityStay? = null
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
                    seconds += dt; acc += dt
                    if (acc < 1f / 30f) continue // 30 quadros/s bastam para pixel art e poupam bateria
                    val step = acc; acc = 0f
                    val r = replayState
                    val ts = r.currentTimestamp ?: plan.startAt
                    val h = plan.hoodieAt(ts, r.speed.minutesPerSecond)
                    // o HUD só recompõe quando muda o que ele mostra
                    if (h.trip != hoodie.trip || h.stay != hoodie.stay || (h.inside == null) != (hoodie.inside == null) || h.dir != hoodie.dir) hoodie = h
                    if (cardStay != null && System.currentTimeMillis() > cardUntil) cardStay = null
                    if (h.stay != lastStay) {
                        if (lastStay != null && h.stay != null && r.state == ReplayState.PLAYING && h.inside == null) { cardStay = h.stay; cardUntil = System.currentTimeMillis() + 2_800 }
                        lastStay = h.stay
                    }
                    ambient.update(step, seconds, if (h.inside != null) -999f else h.x, if (h.inside != null) -999f else h.y)
                    ambient.updateSmoke(step, scene.buildings)
                    val minute = Instant.ofEpochMilli(ts).atZone(zone).let { it.hour * 60 + it.minute }
                    if (mode == CityCameraMode.OVERVIEW) {
                        renderer.renderOverview(buffer, scene, h, ts, seconds)
                    } else {
                        val (tx, ty) = target(mode, selectedSlot, h, r.state == ReplayState.PLAYING)
                        val k = if (reduceMotion) 1f else 1f - exp(-step * if (h.walking) 3.2f else 2.2f)
                        cam[0] += (tx - cam[0]) * k; cam[1] += (ty - cam[1]) * k
                        cam[0] = cam[0].coerceIn(viewW / 2f, CityTown.WW - viewW / 2f); cam[1] = cam[1].coerceIn(viewH / 2f, CityTown.WH - viewH / 2f)
                        val left = (cam[0] - viewW / 2f).roundToInt(); val top = (cam[1] - viewH / 2f).roundToInt()
                        renderer.render(buffer, scene, ambient, h, left, top, seconds, minute, selectedSlot, reduceMotion)
                    }
                    bitmap.setPixels(buffer.pixels, 0, viewW, 0, 0, viewW, viewH)
                    frame++
                }
            }
        }

        Canvas(Modifier.fillMaxSize().pointerInput(scene, viewW, viewH, mode) {
            detectTapGestures { offset ->
                if (mode == CityCameraMode.OVERVIEW) {
                    val s = minOf(viewW.toFloat() / CityTown.WW, viewH.toFloat() / CityTown.WH)
                    val ox = (viewW - CityTown.WW * s) / 2; val oy = (viewH - CityTown.WH * s) / 2
                    hit((offset.x / scale - ox) / s, (offset.y / scale - oy) / s)
                } else hit(cam[0] - viewW / 2f + offset.x / scale, cam[1] - viewH / 2f + offset.y / scale)
                    ?.let { slot ->
                        mode = CityCameraMode.PLACE; selectedSlot = slot.id
                        val ts = replayState.currentTimestamp ?: plan.startAt
                        val stays = plan.staysAt(slot.id)
                        (stays.lastOrNull { it.arrivalAt <= ts } ?: stays.firstOrNull())?.let { onOpenNode(it.nodeId) }
                    }
            }
        }) {
            frame // redesenha a cada quadro novo
            drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(viewW, viewH), dstSize = IntSize(viewW * scale, viewH * scale), filterQuality = FilterQuality.None)
        }

        CityHud(hoodie, plan.stays, replay, zone, Modifier.align(Alignment.TopStart).padding(8.dp))
        cardStay?.let { CityPlaceCard(it, zone, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) }
        CityChip(
            if (mode == CityCameraMode.FOLLOW) stringResource(R.string.city_overview) else stringResource(R.string.city_follow),
            Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("journey_city_mode"),
        ) { mode = if (mode == CityCameraMode.FOLLOW) CityCameraMode.OVERVIEW else CityCameraMode.FOLLOW; selectedSlot = null }
    }
}

private val CITY_HEIGHT = 264.dp
private const val LOGICAL_WIDTH = 240

private fun hit(wx: Float, wy: Float): CityTown.Slot? = CityTown.slots.firstOrNull { wx >= it.px && wx <= it.px + it.pw && wy >= it.py && wy <= it.py + it.ph }

private fun target(mode: CityCameraMode, selected: String?, h: CityHoodieState, playing: Boolean): Pair<Float, Float> {
    if (mode == CityCameraMode.PLACE && selected != null) CityTown.slotById[selected]?.let { return it.px + it.pw / 2f to it.py + it.ph * 0.62f }
    (h.inside ?: h.stay?.takeIf { !h.walking || h.atDoor })?.let { st -> val s = CityTown.slotById.getValue(st.slotId); if (h.trip == null) return s.px + s.pw / 2f to s.py + s.ph * 0.66f }
    val look = if (playing) 24f else 8f
    val (dx, dy) = when (h.dir) { CityTown.Dir.UP -> 0f to -1f; CityTown.Dir.DOWN -> 0f to 1f; CityTown.Dir.LEFT -> -1f to 0f; CityTown.Dir.RIGHT -> 1f to 0f }
    return h.x + dx * look to h.y - 10 + dy * look * 0.6f
}

@Composable
private fun CityHud(h: CityHoodieState, stays: List<CityStay>, replay: ReplayUiState, zone: ZoneId, modifier: Modifier) {
    val ts = replay.currentTimestamp ?: stays.firstOrNull()?.arrivalAt ?: return
    val trip = h.trip
    val status = when {
        trip != null -> stringResource(when (trip.vehicle) { CityTrip.Vehicle.BUS -> R.string.city_hud_bus; CityTrip.Vehicle.CAR -> R.string.city_hud_car; else -> R.string.city_hud_walking }, trip.to.placeName)
        h.inside != null -> stringResource(R.string.city_hud_at, h.inside.placeName)
        h.stay != null && h.dir == CityTown.Dir.UP -> stringResource(R.string.city_hud_arriving, h.stay.placeName)
        h.stay != null -> stringResource(R.string.city_hud_leaving, h.stay.placeName)
        else -> ""
    }
    val next = trip?.to ?: h.stay?.let { stays.getOrNull(it.index + 1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.background(Color(0xDB12142A), RoundedCornerShape(6.dp)).border(2.dp, HoodieColors.PanelLight, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("${formatClock(ts, zone)}  $status", style = MaterialTheme.typography.labelLarge, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.background(Color(0xB812142A), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
            Text(next?.let { stringResource(R.string.city_hud_next, it.placeName, formatClock(it.arrivalAt, zone)) } ?: stringResource(R.string.city_hud_end),
                style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun CityPlaceCard(stay: CityStay, zone: ZoneId, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFFBF6E6), RoundedCornerShape(3.dp)).border(3.dp, Color(0xFF1A1C33), RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp).testTag("journey_city_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stay.placeName.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C33), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val length = stay.departureAt?.let { formatDuration(it - stay.arrivalAt) } ?: stringResource(R.string.city_card_until_now)
        Text(stringResource(R.string.city_card_arrival, formatClock(stay.arrivalAt, zone), length), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3A3F6A))
        val tag = when {
            stay.sourceCount > 1 -> stringResource(R.string.city_card_grouped, stay.sourceCount)
            stay.visitNumber > 1 -> stringResource(R.string.city_card_return, stay.visitNumber)
            else -> null
        }
        tag?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp).background(Color(0xFF1A1C33), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun CityChip(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(Color(0xE612142A), RoundedCornerShape(7.dp)).border(2.dp, HoodieColors.Hood, RoundedCornerShape(7.dp))
            .pointerInput(onClick) { detectTapGestures { onClick() } }.padding(horizontal = 9.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = HoodieColors.Ink) }
}

/** "Remover animações" do sistema: câmera sem suavização e sem balanço nos balões. */
private fun isReduceMotion(context: android.content.Context): Boolean = try {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) { false }
