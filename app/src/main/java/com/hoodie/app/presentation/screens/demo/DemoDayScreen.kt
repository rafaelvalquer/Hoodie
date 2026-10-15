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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.engine.demo.DemoDayFactory
import com.hoodie.app.engine.demo.DemoDiaryAssembler
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.screens.diary.JourneyChaptersView
import com.hoodie.app.presentation.screens.diary.JourneyOverworldMapView
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.screens.diary.SummarySection
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dia de Demonstração: um dia fictício montado em memória com os montadores reais do Diário.
 * Somente leitura (nada vai ao banco, a geofences, ao aprendizado ou a notificações) e com aviso fixo no topo.
 */
@Composable
fun DemoDayScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    if (com.hoodie.app.core.config.HoodieConfig.HOODIE_DEMO_REPLAY_V1) {
        DemoReplayEntry(onExit, modifier)
        return
    }
    LegacyDemoDayScreen(onExit, modifier)
}

@Composable
private fun LegacyDemoDayScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val view = remember { DemoDiaryAssembler.assemble(DemoDayFactory.create(LocalDate.now(zone), zone, includeReplayActivities = false)) }
    val model = remember(view) { JourneyOverworldModel.build(view.journey, zone) }
    var manualChapter by remember { mutableStateOf<DayChapter?>(null) }
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("demo_day")) {
        Row(
            Modifier.fillMaxWidth().background(HoodieColors.Gold).padding(horizontal = 16.dp, vertical = 8.dp).testTag("demo_banner"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.demo_banner), Modifier.weight(1f), color = HoodieColors.Outline, style = MaterialTheme.typography.labelLarge)
            PixelButton(stringResource(R.string.demo_exit), onExit, Modifier.testTag("demo_exit"), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
            Text(stringResource(R.string.demo_intro), color = HoodieColors.Muted)
            PixelPanel(Modifier.fillMaxWidth().testTag("demo_steps")) {
                DemoDayFactory.steps.forEach { step ->
                    Text("${step.timeLabel} — ${step.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            SummarySection(view.diary.summary)
            if (model.isChapters) {
                JourneyChaptersView(model, ReplayUiState(), zone, manualChapter, isToday = false, nowMillis = view.journey.endAt, selectedStopId = null, onOpenChapter = { manualChapter = it }, onStop = {})
            } else model.single?.let { single ->
                PixelPanel(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.journey_title))
                    JourneyOverworldMapView(model, single, ReplayUiState(), zone, null, {}, modifier = Modifier.padding(top = 8.dp))
                }
            }
            PixelButton(stringResource(R.string.demo_exit), onExit, Modifier.fillMaxWidth(), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
        }
    }
}
