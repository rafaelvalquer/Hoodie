package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.JourneyEventKind
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Fração 0..1 do dia de replay → instante, e de volta. */
object JourneyTimeline {
    fun fraction(data: JourneyMapData, timestamp: Long?): Float {
        val span = (data.endAt - data.startAt).coerceAtLeast(1)
        return ((timestamp ?: data.startAt) - data.startAt).toFloat().div(span).coerceIn(0f, 1f)
    }

    fun timestamp(data: JourneyMapData, fraction: Float): Long =
        data.startAt + ((data.endAt - data.startAt) * fraction.coerceIn(0f, 1f)).toLong()
}

/**
 *     [⏮] [▶ REPRODUZIR JORNADA] [⏭] [↺]
 *     06:00 ──●──|───|──|────|── 23:00   (marcas = chegadas e saídas)
 *     VELOCIDADE  1 min/s · 5 min/s · 10 min/s
 */
@Composable
fun JourneyReplayControls(
    replay: ReplayUiState,
    data: JourneyMapData,
    zone: ZoneId,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onReset: () -> Unit,
    onSpeed: (ReplaySpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateLabel = stringResource(
        when (replay.state) {
            ReplayState.IDLE -> R.string.replay_idle
            ReplayState.PLAYING -> R.string.replay_playing
            ReplayState.PAUSED -> R.string.replay_paused
            ReplayState.FINISHED -> R.string.replay_finished
        },
    )
    val enabled = !data.isEmpty && data.endAt > data.startAt
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox("⏮", stringResource(R.string.journey_replay_previous), enabled) { onSeek(JourneyReplayAssembler.previous(data, replay.currentTimestamp)) }
            PixelButton(
                stringResource(
                    when (replay.state) {
                        ReplayState.PLAYING -> R.string.journey_replay_pause
                        ReplayState.FINISHED -> R.string.journey_replay_again
                        else -> R.string.journey_replay_play
                    },
                ),
                onToggle,
                modifier = Modifier.weight(1f).testTag("journey_play").semantics { stateDescription = stateLabel },
                color = HoodieColors.Mint,
                enabled = enabled,
            )
            IconBox("⏭", stringResource(R.string.journey_replay_next), enabled) { onSeek(JourneyReplayAssembler.next(data, replay.currentTimestamp)) }
            IconBox("↺", stringResource(R.string.journey_replay_reset), enabled, onReset)
        }
        if (enabled) {
            val events = JourneyReplayAssembler.events(data)
            val timelineLabel = stringResource(R.string.journey_replay_timeline)
            Box(Modifier.fillMaxWidth()) {
                // Marcas dos eventos atrás do slider: chegada = alta, saída = baixa.
                Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                    events.forEach { e ->
                        val x = size.width * JourneyTimeline.fraction(data, e.timestamp)
                        val h = if (e.kind == JourneyEventKind.ARRIVE) 10.dp.toPx() else 6.dp.toPx()
                        drawRect(HoodieColors.Hood, Offset(x - 1.dp.toPx(), size.height / 2 - h - 6.dp.toPx()), Size(2.dp.toPx(), h))
                    }
                }
                Slider(
                    value = JourneyTimeline.fraction(data, replay.currentTimestamp),
                    onValueChange = { onSeek(JourneyTimeline.timestamp(data, it)) },
                    modifier = Modifier.fillMaxWidth().testTag("journey_timeline").semantics {
                        contentDescription = timelineLabel
                        stateDescription = replay.currentTimestamp?.let { formatClock(it, zone) } ?: formatClock(data.startAt, zone)
                    },
                    colors = SliderDefaults.colors(thumbColor = HoodieColors.Gold, activeTrackColor = HoodieColors.Mint, inactiveTrackColor = HoodieColors.PanelLight),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(data.startAt, zone), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
                replay.currentTimestamp?.let { Text("▶ ${formatClock(it, zone)}", style = MaterialTheme.typography.labelLarge, color = HoodieColors.Gold) }
                Text(formatClock(data.endAt, zone), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.journey_replay_speed), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            ReplaySpeed.entries.forEach { speed ->
                FilterChip(
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton; contentDescription = speed.label },
                    selected = replay.speed == speed, onClick = { onSpeed(speed) },
                    label = { Text(speed.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/** Botão quadrado 48 dp com um glifo (⏮ ⏭ ↺). */
@Composable
private fun IconBox(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .background(if (enabled) HoodieColors.PanelLight else HoodieColors.Panel)
            .border(2.dp, HoodieColors.Outline)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = if (enabled) HoodieColors.Ink else HoodieColors.Muted)
    }
}
