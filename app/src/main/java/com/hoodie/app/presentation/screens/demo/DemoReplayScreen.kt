package com.hoodie.app.presentation.screens.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.engine.demo.DemoReplayMode
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.screens.diary.DiaryReplayHud
import com.hoodie.app.presentation.screens.diary.JourneyChaptersView
import com.hoodie.app.presentation.screens.diary.JourneyOverworldMapView
import com.hoodie.app.presentation.screens.diary.JourneyReplayControls
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.Instant

@Composable
fun DemoReplayEntry(onExit: () -> Unit, modifier: Modifier = Modifier, viewModel: DemoReplayViewModel = hiltViewModel()) {
    DemoReplayScreen(viewModel, onExit, modifier)
}

/** Stateful replay UI; all visible data is projected from the same fictitious timestamp. */
@Composable
fun DemoReplayScreen(viewModel: DemoReplayViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val presentation = state.presentation
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onHidden()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.onVisible()
        onDispose { lifecycle.removeObserver(observer); viewModel.onHidden() }
    }
    var manualChapter by remember { mutableStateOf<com.hoodie.app.domain.diary.journey.DayChapter?>(null) }

    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("demo_day")) {
        Row(
            Modifier.fillMaxWidth().background(HoodieColors.Gold).padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("demo_banner"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.demo_banner), Modifier.weight(1f), color = HoodieColors.Outline,
                style = MaterialTheme.typography.labelLarge)
            PixelButton(stringResource(R.string.demo_exit), { viewModel.exit(); onExit() },
                Modifier.testTag("demo_exit"), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelPanel(Modifier.fillMaxWidth().testTag("demo_replay_story")) {
                Text(presentation.title, style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
                Text(presentation.narrative, color = HoodieColors.Ink, modifier = Modifier.padding(top = 4.dp))
                DiaryReplayHud(presentation.replay.visual, viewModel.zone, Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Tour · 50 s", { viewModel.setMode(DemoReplayMode.QUICK_TOUR); manualChapter = null },
                    Modifier.weight(1f).testTag("demo_mode_tour"),
                    color = if (presentation.source.mode == DemoReplayMode.QUICK_TOUR) HoodieColors.Mint else HoodieColors.Panel)
                PixelButton("Dia completo", { viewModel.setMode(DemoReplayMode.FULL_DAY); manualChapter = null },
                    Modifier.weight(1f).testTag("demo_mode_full_day"),
                    color = if (presentation.source.mode == DemoReplayMode.FULL_DAY) HoodieColors.Mint else HoodieColors.Panel)
            }
            JourneyReplayControls(
                replay = presentation.replay,
                data = viewModel.view.journey,
                zone = viewModel.zone,
                onToggle = viewModel::togglePlayback,
                onSeek = viewModel::seek,
                onReset = viewModel::restart,
                onSpeed = viewModel::setSpeed,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                showSpeedSelector = presentation.source.mode == DemoReplayMode.FULL_DAY,
                modifier = Modifier.fillMaxWidth().testTag("demo_replay_controls"),
            )
            if (presentation.source.mode == DemoReplayMode.FULL_DAY) {
                Text("Velocidade: ${presentation.source.speed.minutesPerSecond} min/s",
                    style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            }
            if (viewModel.model.isChapters) {
                JourneyChaptersView(viewModel.model, presentation.replay, viewModel.zone, manualChapter,
                    isToday = false, nowMillis = presentation.source.timestamp, selectedStopId = null,
                    onOpenChapter = { manualChapter = it }, onStop = {}, modifier = Modifier.testTag("demo_replay_map"))
            } else viewModel.model.single?.let { single ->
                JourneyOverworldMapView(viewModel.model, single, presentation.replay, viewModel.zone,
                    null, {}, modifier = Modifier.testTag("demo_replay_map"))
            }
            PixelPanel(Modifier.fillMaxWidth().testTag("demo_contextual_preview_toggle")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prévia da cena", style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                        Text("Cena fictícia sincronizada com ${presentation.clockLabel}",
                            style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
                    }
                    Switch(checked = state.previewEnabled, onCheckedChange = viewModel::setPreview,
                        modifier = Modifier.testTag("demo_preview_switch"))
                }
            }
            if (state.previewEnabled) {
                val instant = Instant.ofEpochMilli(presentation.source.timestamp).atZone(viewModel.zone)
                val animationMillis = ((presentation.source.timestamp - viewModel.timeline.startAt) / 300L).coerceAtLeast(1L)
                val frame = remember(animationMillis, instant.hour, instant.minute, presentation.replay.visual.dayPeriod) {
                    com.hoodie.app.presentation.components.PixelRenderFrame(animationMillis,
                        instant.hour * 60 + instant.minute, presentation.replay.visual.dayPeriod)
                }
                PixelPanel(Modifier.fillMaxWidth().testTag("demo_contextual_preview")) {
                    Text("${presentation.context.emoji} ${presentation.activePlaceName ?: presentation.context.label} · ${presentation.activity.label}",
                        style = MaterialTheme.typography.labelMedium, color = HoodieColors.Gold)
                    androidx.compose.runtime.CompositionLocalProvider(LocalPixelRenderFrame provides frame) {
                        HoodieSceneView(visual = remember(presentation) { presentation.contextualVisual() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), greet = false,
                            periodOverride = presentation.replay.visual.dayPeriod)
                    }
                }
            }
            Text("${viewModel.date} · ${formatClock(viewModel.timeline.startAt, viewModel.zone)}–${formatClock(viewModel.timeline.endAt, viewModel.zone)}",
                style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            PixelButton(stringResource(R.string.demo_exit), { viewModel.exit(); onExit() }, Modifier.fillMaxWidth(),
                color = HoodieColors.Panel, textColor = HoodieColors.Ink)
        }
    }
}
