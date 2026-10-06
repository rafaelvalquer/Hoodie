package com.hoodie.app.presentation.screens.diary.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.engine.diary.DayClockAssembler
import com.hoodie.app.engine.diary.SyntheticClockDays
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.screens.diary.ReplayUiState
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Diary Lab · Relógio do Dia 2.0: dias sintéticos com todos os tipos de trecho e um
 * controle que move o "agora" (24 h = dia passado), para revisar sem esperar um dia real.
 */
@Composable
fun DayClockLab(zone: ZoneId, date: LocalDate, modifier: Modifier = Modifier) {
    var kind by remember { mutableStateOf(SyntheticClockDays.Kind.FULL) }
    var nowHours by remember { mutableFloatStateOf(21.67f) }
    var selected by remember { mutableStateOf<String?>(null) }
    val labZone = if (kind == SyntheticClockDays.Kind.DST) ZoneId.of("Europe/Berlin") else zone
    val labDate = if (kind == SyntheticClockDays.Kind.DST) LocalDate.of(2026, 3, 29) else date
    val minutes = (nowHours * 60).toInt()
    val data = remember(kind, minutes, labZone, labDate) {
        val now = if (minutes >= 24 * 60) null else LocalTime.of(minutes / 60, minutes % 60)
        val day = SyntheticClockDays.build(kind, labDate, labZone, now)
        DayClockAssembler.build(day.diary, labDate, labZone, day.now)
    }
    Column(modifier.fillMaxWidth().testTag("day_clock_lab"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("RELÓGIO DO DIA 2.0 · LAB")
        ChipRow(SyntheticClockDays.Kind.entries.map { it.label }, kind.ordinal, { kind = SyntheticClockDays.Kind.entries[it]; selected = null })
        val nowLabel = if (minutes >= 24 * 60) "dia passado" else "%02d:%02d".format(minutes / 60, minutes % 60)
        Text("Agora: $nowLabel · ${data.segments.size} trechos · ${data.dayLengthMinutes / 60} h", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        Slider(value = nowHours, onValueChange = { nowHours = it }, valueRange = 0f..24f)
        DayClockPanel(DayClockUiState.of(data, selected, ReplayUiState()), onSelect = { selected = it })
    }
}
