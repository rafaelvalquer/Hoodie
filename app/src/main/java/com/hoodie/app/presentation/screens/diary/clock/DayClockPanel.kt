package com.hoodie.app.presentation.screens.diary.clock

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.clock.ClockCategory
import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.pixel.diary.clock.ClockHit
import com.hoodie.app.pixel.diary.clock.DayClockGeometry
import com.hoodie.app.pixel.diary.clock.DayClockPalette
import com.hoodie.app.pixel.diary.clock.DayClockRenderer
import com.hoodie.app.pixel.diary.clock.DayClockScene
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor

/** ~8 FPS para o Hoodie do mostrador. */
private const val FRAME_MS = 125L

/**
 * Aba RELÓGIO (Relógio do Dia 2.0): mostrador pixel art, barra "Tempo por lugar" e
 * lista "Para onde o Hoodie foi". Mostrador e lista compartilham [DayClockUiState.selectedId].
 * [onMinuteTick] é chamado a cada minuto enquanto a tela está visível (só faz sentido hoje).
 */
@Composable
fun DayClockPanel(
    state: DayClockUiState,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onMinuteTick: () -> Unit = {},
    animate: Boolean = true,
) {
    val data = state.data ?: return
    MinuteTicker(enabled = data.nowMinute != null && state.mode == DayClockUiState.Mode.LIVE, onTick = onMinuteTick)
    PixelPanel(modifier.fillMaxWidth().testTag("day_clock")) {
        SectionLabel(stringResource(R.string.clock2_title))
        DayClockDial(state, onSelect, Modifier.padding(top = 8.dp), animate)
        if (data.isEmpty) {
            Text(stringResource(R.string.clock2_empty), color = HoodieColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            return@PixelPanel
        }
        TimeByPlaceBar(data, Modifier.padding(top = 12.dp))
        ClockSegmentList(state, onSelect, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun MinuteTicker(enabled: Boolean, onTick: () -> Unit) {
    if (!enabled) return
    val tick by rememberUpdatedState(onTick)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                tick()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
}

/** Relógio de animação do mostrador: ~8 FPS, só com a tela RESUMED. */
@Composable
private fun rememberClockFrameTime(animate: Boolean): Long {
    LocalPixelRenderFrame.current?.let { return it.animationMillis }
    if (!animate) return 0L
    var time by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(FRAME_MS)
                androidx.compose.runtime.withFrameNanos { time = it / 1_000_000L }
            }
        }
    }
    return time
}

@Composable
private fun DayClockDial(state: DayClockUiState, onSelect: (String?) -> Unit, modifier: Modifier, animate: Boolean) {
    val data = state.data ?: return
    val nowMinute = state.nowMinute
    val time = rememberClockFrameTime(animate)
    // Camada estática: fora da main thread, só quando muda minuto, seleção ou dia (dois buffers alternados).
    val fixedFrame = LocalPixelRenderFrame.current
    val buffers = remember { arrayOf(PixelBuffer(DayClockGeometry.SIZE, DayClockGeometry.SIZE), PixelBuffer(DayClockGeometry.SIZE, DayClockGeometry.SIZE)) }
    var asyncStatic by remember { mutableStateOf<PixelBuffer?>(null) }
    // Capturas com quadro fixo (goldens de tela) desenham na hora; o app renderiza em Default.
    val syncStatic = if (fixedFrame != null) remember(data, nowMinute, state.selectedId) { DayClockRenderer.renderStatic(data, nowMinute, state.selectedId) } else null
    if (fixedFrame == null) {
        LaunchedEffect(data, nowMinute, state.selectedId) {
            val target = if (asyncStatic === buffers[0]) buffers[1] else buffers[0]
            asyncStatic = withContext(Dispatchers.Default) { DayClockRenderer.renderStatic(data, nowMinute, state.selectedId, target) }
        }
    }
    val static = syncStatic ?: asyncStatic
    val out = remember { PixelBuffer(DayClockGeometry.SIZE, DayClockGeometry.SIZE) }
    val bitmap = remember { Bitmap.createBitmap(DayClockGeometry.SIZE, DayClockGeometry.SIZE, Bitmap.Config.ARGB_8888) }
    val summary = dialDescription(state)
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = floor(widthPx / DayClockGeometry.SIZE).toInt().coerceAtLeast(1)
        val dialPx = DayClockGeometry.SIZE * scale
        val offX = ((widthPx - dialPx) / 2f).toInt()
        Box(Modifier.fillMaxWidth().height(with(density) { dialPx.toDp() })) {
            Canvas(
                Modifier.fillMaxSize().testTag("day_clock_dial")
                    .semantics { contentDescription = summary }
                    .pointerInput(data, nowMinute, scale, offX) {
                        detectTapGestures { p ->
                            when (val hit = DayClockGeometry.hitTest((p.x - offX) / scale, p.y / scale, data, nowMinute)) {
                                ClockHit.Center -> select(null)
                                is ClockHit.Segment -> select(hit.id)
                                ClockHit.FutureArea, ClockHit.None -> Unit
                            }
                        }
                    },
            ) {
                val s = static ?: return@Canvas
                DayClockRenderer.render(DayClockScene(data, nowMinute, state.selectedId, time), s, out)
                bitmap.setPixels(out.pixels, 0, out.width, 0, 0, out.width, out.height)
                drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(offX, 0), dstSize = IntSize(dialPx, dialPx), filterQuality = FilterQuality.None)
            }
            // Texto do centro, limitado ao diâmetro da placa.
            val plate = (DayClockGeometry.CENTER_PLATE.endInclusive * 2 * scale * 0.80f).toInt()
            with(density) {
                Box(
                    Modifier.offset { IntOffset(offX + dialPx / 2 - plate / 2, dialPx / 2 - plate / 2) }.size(plate.toDp()),
                    contentAlignment = Alignment.Center,
                ) { ClockCenter(state, plate) }
            }
            if (state.selectedId != null) {
                Text(
                    stringResource(R.string.clock2_back_now),
                    style = MaterialTheme.typography.labelSmall, color = HoodieColors.Outline,
                    modifier = Modifier.align(Alignment.TopEnd).heightIn(min = 48.dp).width(72.dp)
                        .semantics { role = Role.Button }
                        .clickable(onClickLabel = stringResource(R.string.clock2_back_now_description)) { onSelect(null) }
                        .padding(4.dp).background(HoodieColors.Gold).border(2.dp, HoodieColors.Outline).padding(6.dp)
                        .testTag("clock_now"),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ClockCenter(state: DayClockUiState, widthPx: Int) {
    val data = state.data ?: return
    val focus = state.selected ?: state.current
    val zone = data.zone
    val label = when {
        state.selected is ClockSegment.Stay -> stringResource(R.string.clock2_label_stop, (focus as ClockSegment.Stay).stopIndex, data.stopCount)
        state.selected is ClockSegment.Move -> stringResource(R.string.clock2_label_move)
        state.selected is ClockSegment.Unknown -> stringResource(R.string.clock2_label_unknown)
        state.mode == DayClockUiState.Mode.REPLAY -> stringResource(R.string.clock2_label_replay)
        state.nowMinute != null -> stringResource(R.string.clock2_label_now)
        else -> stringResource(R.string.clock2_label_day)
    }
    val timeText = when {
        state.selected != null -> "${formatClock(data.instantOf(focus!!.startMinute), zone)}–${formatClock(data.instantOf(focus.endMinute), zone)}"
        state.nowMinute != null -> formatClock(data.instantOf(state.nowMinute!!), zone)
        else -> formatClock(data.instantOf(data.endMinute.coerceAtMost(data.dayLengthMinutes - 1)), zone)
    }
    val small = MaterialTheme.typography.labelSmall
    val maxChars = with(LocalDensity.current) { (widthPx / small.fontSize.toPx()).toInt().coerceAtLeast(4) }
    val (place, activity, duration) = centerLines(state, focus, maxChars)
    Column(Modifier.fillMaxWidth().testTag("clock_center"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CenterLine(label, small, HoodieColors.Panel)
        CenterLine(timeText, MaterialTheme.typography.titleMedium, HoodieColors.Outline)
        place?.let { CenterLine(it, small, HoodieColors.Outline) }
        activity?.let { CenterLine(it, small, HoodieColors.Panel) }
        duration?.let { CenterLine(it, small, HoodieColors.Panel) }
    }
}

@Composable
private fun CenterLine(text: String, style: TextStyle, color: Color) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** Lugar (troca pelo nome da categoria se não couber), atividade e duração. */
@Composable
private fun centerLines(state: DayClockUiState, focus: ClockSegment?, maxChars: Int): Triple<String?, String?, String?> {
    val data = state.data ?: return Triple(null, null, null)
    return when (focus) {
        is ClockSegment.Stay -> {
            val name = if (focus.placeName.length + 2 > maxChars) categoryLabel(focus.category) else focus.placeName
            val ongoing = state.selected == null && state.nowMinute != null
            val minutes = (if (ongoing) state.nowMinute!! else focus.endMinute) - focus.startMinute
            Triple(
                "${focus.placeType.emoji} $name",
                focus.hoodieActivity?.let { "${it.emoji} ${it.label}" },
                if (ongoing) stringResource(R.string.clock2_since, formatDuration(minutes * 60_000L)) else formatDuration(minutes * 60_000L),
            )
        }
        is ClockSegment.Move -> {
            val to = data.segment(focus.toStopId) as? ClockSegment.Stay
            Triple(
                focus.mode?.let { "${it.emoji} ${it.label}" } ?: stringResource(R.string.clock2_move_generic),
                to?.let { stringResource(R.string.clock2_move_to, it.placeName) },
                formatDuration(focus.minutes * 60_000L),
            )
        }
        is ClockSegment.Unknown -> Triple(stringResource(R.string.clock2_unknown), null, formatDuration(focus.minutes * 60_000L))
        null -> {
            val top = data.totals.maxByOrNull { it.value }
            Triple(top?.let { "${categoryEmoji(it.key)} ${categoryLabel(it.key)}" }, top?.let { stringResource(R.string.clock2_most) }, top?.let { formatDuration(it.value * 60_000L) })
        }
    }
}

@Composable
internal fun categoryLabel(c: ClockCategory): String = stringResource(
    when (c) {
        ClockCategory.HOME -> R.string.ui_extra_diary_screen_3
        ClockCategory.WORK -> R.string.ui_extra_diary_screen_4
        ClockCategory.COMMUTE -> R.string.ui_extra_diary_screen_5
        ClockCategory.MEAL -> R.string.ui_extra_diary_screen_6
        ClockCategory.GYM -> R.string.ui_extra_diary_screen_8
        ClockCategory.LEISURE -> R.string.ui_extra_diary_screen_10
        ClockCategory.OTHER -> R.string.ui_extra_diary_screen_11
    },
)

internal fun categoryEmoji(c: ClockCategory): String = when (c) {
    ClockCategory.HOME -> "🏠"
    ClockCategory.WORK -> "🏢"
    ClockCategory.COMMUTE -> "🚶"
    ClockCategory.MEAL -> "🍽️"
    ClockCategory.GYM -> "🏋️"
    ClockCategory.LEISURE -> "🎉"
    ClockCategory.OTHER -> "📍"
}

internal fun categoryColor(c: ClockCategory) = Color(DayClockPalette.category(c).fill)

@Composable
private fun dialDescription(state: DayClockUiState): String {
    val data = state.data ?: return ""
    val parts = data.totals.entries.sortedByDescending { it.value }.take(3).map { (c, m) ->
        stringResource(R.string.clock2_description_part, formatDuration(m * 60_000L), categoryLabel(c))
    }.toMutableList()
    val current = state.current as? ClockSegment.Stay
    if (current != null && state.nowMinute != null) {
        parts += stringResource(R.string.clock2_description_now, current.placeName, formatDuration((state.nowMinute!! - current.startMinute) * 60_000L))
    }
    return stringResource(R.string.clock2_description, parts.joinToString(", "))
}

/** Barra "Tempo por lugar" (pesos) + legenda com as cores do anel. */
@Composable
private fun TimeByPlaceBar(data: DayClockData, modifier: Modifier) {
    val totals = ClockCategory.entries.mapNotNull { c -> data.totals[c]?.takeIf { it > 0 }?.let { c to it } }
    if (totals.isEmpty()) return
    val description = stringResource(R.string.clock2_bar_description, totals.map { (c, m) -> "${categoryLabel(c)} ${formatDuration(m * 60_000L)}" }.joinToString(", "))
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }.testTag("clock_time_by_place")) {
        SectionLabel(stringResource(R.string.clock2_time_by_place))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(14.dp).border(2.dp, HoodieColors.Outline)) {
            totals.forEach { (c, m) -> Box(Modifier.weight(m.toFloat()).fillMaxSize().background(categoryColor(c))) }
        }
        totals.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                row.forEach { (c, m) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(categoryColor(c)).border(1.dp, HoodieColors.Outline))
                        Text(" ${categoryLabel(c)} ${formatDuration(m * 60_000L)}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Lista "Para onde o Hoodie foi": rolagem própria até o item selecionado, alvos ≥ 48 dp. */
@Composable
private fun ClockSegmentList(state: DayClockUiState, onSelect: (String?) -> Unit, modifier: Modifier) {
    val data = state.data ?: return
    val scroll = rememberScrollState()
    val positions = remember(data) { mutableStateMapOf<String, Int>() }
    val highlighted = state.selectedId ?: state.current?.id
    LaunchedEffect(highlighted, positions[highlighted]) {
        positions[highlighted]?.let { scroll.animateScrollTo((it - 24).coerceAtLeast(0)) }
    }
    val zone = data.zone
    Column(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.clock2_where))
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 280.dp).verticalScroll(scroll).testTag("clock_list")) {
            data.segments.forEach { s ->
                val from = formatClock(data.instantOf(s.startMinute), zone)
                val to = formatClock(data.instantOf(s.endMinute), zone)
                val isSelected = s.id == highlighted
                val description = when (s) {
                    is ClockSegment.Stay -> stringResource(R.string.clock2_item_stay, from, to, s.placeName, s.hoodieActivity?.label.orEmpty(), formatDuration(s.minutes * 60_000L))
                    is ClockSegment.Move -> stringResource(R.string.clock2_item_move, from, to, s.mode?.label ?: stringResource(R.string.clock2_move_generic), formatDuration(s.minutes * 60_000L))
                    is ClockSegment.Unknown -> stringResource(R.string.clock2_item_unknown, from, to)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .onGloballyPositioned { positions[s.id] = it.positionInParent().y.toInt() }
                        .background(if (isSelected) HoodieColors.PanelLight else Color.Transparent)
                        .semantics(mergeDescendants = true) { contentDescription = description; selected = isSelected; role = Role.Button }
                        .clickable { onSelect(s.id) }
                        .padding(horizontal = 6.dp, vertical = if (s is ClockSegment.Stay) 8.dp else 4.dp)
                        .testTag("clock_item_${s.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val swatch = when (s) {
                        is ClockSegment.Stay -> categoryColor(s.category)
                        is ClockSegment.Move -> Color(DayClockPalette.mode(s.mode))
                        is ClockSegment.Unknown -> Color(DayClockPalette.TRACK)
                    }
                    Box(Modifier.width(14.dp), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.size(if (s is ClockSegment.Stay) 12.dp else 8.dp).background(swatch).border(1.dp, HoodieColors.Outline))
                    }
                    Text(" $from", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.width(64.dp))
                    when (s) {
                        is ClockSegment.Stay -> Column(Modifier.weight(1f)) {
                            Text("${s.placeType.emoji} ${s.placeName}", style = MaterialTheme.typography.labelLarge, color = if (isSelected) HoodieColors.Gold else HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val extra = listOfNotNull(
                                s.hoodieActivity?.let { "${it.emoji} ${it.label}" },
                                formatDuration(s.minutes * 60_000L),
                                if (s.visitNumber > 1) stringResource(R.string.clock2_return, s.visitNumber) else null,
                            ).joinToString(" · ")
                            Text(extra, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        is ClockSegment.Move -> Text(
                            "${s.mode?.emoji ?: "↝"} ${s.mode?.label ?: stringResource(R.string.clock2_move_generic)} · ${formatDuration(s.minutes * 60_000L)}",
                            style = MaterialTheme.typography.bodySmall, color = if (isSelected) HoodieColors.Gold else HoodieColors.Muted, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        is ClockSegment.Unknown -> Text(
                            "${stringResource(R.string.clock2_unknown)} · ${formatDuration(s.minutes * 60_000L)}",
                            style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
