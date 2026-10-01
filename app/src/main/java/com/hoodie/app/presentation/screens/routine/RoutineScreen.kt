package com.hoodie.app.presentation.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.TimeField
import com.hoodie.app.presentation.screens.onboarding.DayToggles
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val hoodie: HoodieEngine,
    private val clock: ClockProvider,
) : ViewModel() {
    suspend fun load(): Pair<Routine, SleepSchedule> = routines.get() to settings.current().sleep

    fun save(r: Routine, sleep: SleepSchedule, done: () -> Unit) = viewModelScope.launch {
        routines.save(r, clock.nowMillis())
        settings.setSleep(sleep)
        hoodie.resolve()
        done()
    }
}

@Composable
fun RoutineScreen(onBack: () -> Unit, vm: RoutineViewModel = hiltViewModel()) {
    var routine by remember { mutableStateOf<Routine?>(null) }
    var sleep by remember { mutableStateOf(SleepSchedule()) }
    LaunchedEffect(Unit) { vm.load().let { routine = it.first; sleep = it.second } }
    val r = routine ?: return
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ROTINA", style = MaterialTheme.typography.headlineSmall)
        SectionLabel("Trabalho")
        ChipRow(WorkMode.entries.map { it.label }, r.workMode.ordinal, { routine = r.copy(workMode = WorkMode.entries[it]) })
        if (r.hasWork) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Entrada", r.startMinute, { routine = r.copy(startMinute = it) }, Modifier.weight(1f))
                TimeField("Saída", r.endMinute, { routine = r.copy(endMinute = it) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Almoço", r.lunchStartMinute, { routine = r.copy(lunchStartMinute = it) }, Modifier.weight(1f))
                TimeField("Volta", r.lunchEndMinute, { routine = r.copy(lunchEndMinute = it) }, Modifier.weight(1f))
            }
            SectionLabel("Dias")
            DayToggles(r.days, { d -> routine = r.copy(days = if (d in r.days) r.days - d else r.days + d) })
        }
        SectionLabel("Sono (o Hoodie acompanha)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Acordar", sleep.wakeMinute, { sleep = sleep.copy(wakeMinute = it) }, Modifier.weight(1f))
            TimeField("Dormir", sleep.sleepMinute, { sleep = sleep.copy(sleepMinute = it) }, Modifier.weight(1f))
        }
        Text("Feriado ou folga? Use \"Hoje não vou trabalhar\" na tela inicial.", color = HoodieColors.Muted)
        PixelButton("Salvar", { vm.save(r, sleep, onBack) }, Modifier.fillMaxWidth())
        PixelButton("Voltar", onBack, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
    }
}
