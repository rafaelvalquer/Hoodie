package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.core.time.SystemClockProvider
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/**
 * A tela que importa: CENÁRIO + GATO + AÇÃO. Todo o resto é secundário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var manualOpen by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Cabeçalho: hora + período + atalhos.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.now > 0) formatClock(state.now, zone) else "--:--", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Text(periodEmoji(state.period) + " " + state.period.label, color = HoodieColors.Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            HeaderIcon("✨") { onOpen(Routes.MEMORIES) }
            HeaderIcon("🐱") { onOpen(Routes.PROFILE) }
        }

        // Cena viva.
        Box(Modifier.fillMaxWidth().aspectRatio(240f / 320f).padding(horizontal = 8.dp), contentAlignment = Alignment.TopCenter) {
            HoodieSceneView(state.visual, Modifier.fillMaxSize(), reactions = vm.reactions)
            state.dialogue?.let { SpeechBubble(it, Modifier.padding(top = 10.dp)) }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.snapshot?.let { snap ->
                val a = snap.state.activity
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.catName} está ${a.label}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    val since = state.now - snap.state.startedAt
                    Text(
                        "desde ${formatClock(snap.state.startedAt, zone)} · há ${formatDuration(since)}",
                        color = HoodieColors.Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.question?.let { q -> QuestionCard(q, vm) }

            if (state.probableMode || state.locationStatus != LocationStatus.OK) {
                PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                    Text(
                        when (state.locationStatus) {
                            LocationStatus.NO_PERMISSION, LocationStatus.DISABLED -> "Não consegui descobrir onde você está."
                            LocationStatus.NO_BACKGROUND -> "Sem localização em segundo plano, só percebo mudanças com o app aberto."
                            LocationStatus.OK -> "Ainda não sei onde você está."
                        },
                    )
                    Text("🐱 ${state.catName} está seguindo a rotina provável.", color = HoodieColors.Muted)
                }
            }

            state.suggestSavePlace?.let { type ->
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(if (type == PlaceType.WORK) "🏢 Chegou ao trabalho?" else "🏠 Está em casa agora?", style = MaterialTheme.typography.titleMedium)
                    Text("Salve este local para o ${state.catName} perceber suas chegadas sozinho.", color = HoodieColors.Muted)
                    Spacer(Modifier.padding(4.dp))
                    PixelButton(if (busy) "Localizando..." else "Salvar este local como ${type.label}", { vm.savePlaceHere(type) }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }

            // Você.
            PixelPanel(Modifier.fillMaxWidth()) {
                SectionLabel("Você")
                val ctx = state.context
                val type = ctx?.type ?: UserContextType.UNKNOWN
                Text("${type.emoji} ${type.label}" + if (state.probableMode && ctx != null) " (provável)" else "", style = MaterialTheme.typography.titleMedium)
                ctx?.let { Text("desde ${formatClock(it.startedAt, zone)}", color = HoodieColors.Muted) }
                state.next?.let {
                    Spacer(Modifier.padding(4.dp))
                    SectionLabel("Próximo evento")
                    Text(it.display)
                }
            }

            PixelButton("O que estou fazendo?", { manualOpen = true }, Modifier.fillMaxWidth(), color = HoodieColors.Gold)
            if (state.isWorkDay) {
                PixelButton(
                    if (state.isDayOff) "Hoje é dia normal de trabalho" else "Hoje não vou trabalhar",
                    vm::toggleDayOff, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink,
                )
            }
        }
    }

    if (manualOpen) {
        ModalBottomSheet(onDismissRequest = { manualOpen = false }, containerColor = HoodieColors.Panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("O QUE ESTOU FAZENDO?", style = MaterialTheme.typography.titleMedium)
                Text("Isso também ensina o ${state.catName} sobre a sua rotina.", color = HoodieColors.Muted)
                UserContextType.manualOptions.forEach { t ->
                    PixelButton("${t.emoji} ${if (t == UserContextType.UNKNOWN) "Outro" else t.label}", {
                        vm.setManual(t); manualOpen = false
                    }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
                Spacer(Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun HeaderIcon(emoji: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .background(HoodieColors.Panel)
            .border(2.dp, HoodieColors.Outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(emoji) }
}

private fun periodEmoji(p: com.hoodie.app.core.time.DayPeriod) = when (p) {
    com.hoodie.app.core.time.DayPeriod.MORNING -> "🌅"
    com.hoodie.app.core.time.DayPeriod.DAY -> "☀️"
    com.hoodie.app.core.time.DayPeriod.EVENING -> "🌇"
    com.hoodie.app.core.time.DayPeriod.NIGHT -> "🌙"
}

@Composable
private fun QuestionCard(q: ContextQuestion, vm: HomeViewModel) {
    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Text(q.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        when (q.kind) {
            QuestionKind.CONFIRM_CONTEXT -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton("Sim", { vm.answerYesNo(q.id, true) }, Modifier.weight(1f))
                PixelButton("Não", { vm.answerYesNo(q.id, false) }, Modifier.weight(1f), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
            QuestionKind.NEW_PLACE -> {
                val options = PlaceType.newPlaceOptions
                ChipRow(options.map { "${it.emoji} ${it.label}" }, null, { vm.answerNewPlace(q.id, options[it]) })
                Spacer(Modifier.padding(4.dp))
                Text("Agora não", color = HoodieColors.Muted, modifier = Modifier.clickable { vm.dismissQuestion(q.id) }.padding(4.dp))
            }
            QuestionKind.SAVE_PLACE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton("Sim", { vm.answerSavePlace(q.id, true) }, Modifier.weight(1f))
                PixelButton("Só hoje", { vm.answerSavePlace(q.id, false) }, Modifier.weight(1f), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
        }
    }
}
