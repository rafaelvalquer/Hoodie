package com.hoodie.app.presentation.screens.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineEvent
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.data.repository.HistoryRepository
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.timeline.ActivitySpan
import com.hoodie.app.engine.timeline.DailySummary
import com.hoodie.app.engine.timeline.DailySummaryCalculator
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class HistoryRange(val label: String) { TODAY("Hoje"), YESTERDAY("Ontem"), WEEK("7 dias") }

data class DayHistory(val date: LocalDate, val events: List<TimelineEvent>, val summary: DailySummary)

data class TimelineUiState(val range: HistoryRange = HistoryRange.TODAY, val days: List<DayHistory> = emptyList(), val catName: String = "Hoodie")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val hoodie: HoodieEngine,
    private val clock: ClockProvider,
    settings: SettingsRepository,
) : ViewModel() {
    val range = MutableStateFlow(HistoryRange.TODAY)

    val state = range.flatMapLatest { r ->
        val zone = clock.zone()
        val today = clock.today()
        val dates = when (r) {
            HistoryRange.TODAY -> listOf(today)
            HistoryRange.YESTERDAY -> listOf(today.minusDays(1))
            HistoryRange.WEEK -> (0L..6L).map { today.minusDays(it) }
        }
        val from = startOfDay(dates.min(), zone)
        val to = startOfDay(dates.max().plusDays(1), zone)
        // Garante que o histórico do gato esteja reconciliado antes de exibir.
        val current = flow { emit(hoodie.resolve().state) }
        combine(history.timeline(from, to), history.contextSpans(from, to), history.activitySpans(from, to), current, settings.settings) { events, ctx, acts, st, s ->
            val now = clock.nowMillis()
            val days = dates.map { d ->
                val ds = startOfDay(d, zone); val de = startOfDay(d.plusDays(1), zone)
                DayHistory(
                    d,
                    events.filter { it.timestamp in ds until de },
                    DailySummaryCalculator.compute(ctx, acts, ActivitySpan(st.activity, st.startedAt, now), ds, de, now),
                )
            }
            TimelineUiState(r, days, s.catName)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())
}

@Composable
fun TimelineScreen(vm: TimelineViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("HISTÓRICO", style = MaterialTheme.typography.headlineSmall)
        ChipRow(HistoryRange.entries.map { it.label }, range.ordinal, { vm.range.value = HistoryRange.entries[it] })
        if (state.days.all { it.events.isEmpty() && it.summary.isEmpty }) {
            PixelPanel(Modifier.fillMaxWidth()) { Text("Nada registrado ainda. O dia de vocês vai aparecer aqui.", color = HoodieColors.Muted) }
        }
        state.days.forEach { day ->
            if (state.range == HistoryRange.WEEK) {
                Text(day.date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("pt-BR"))).uppercase(), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                SummaryCard(day.summary, state.catName)
            } else {
                SummaryCard(day.summary, state.catName)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimelineColumn("Você", day.events.filter { it.actor == TimelineActor.USER }, zone, Modifier.weight(1f))
                    TimelineColumn(state.catName, day.events.filter { it.actor == TimelineActor.HOODIE }, zone, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TimelineColumn(title: String, events: List<TimelineEvent>, zone: ZoneId, modifier: Modifier) {
    PixelPanel(modifier) {
        SectionLabel(title)
        Spacer(Modifier.padding(4.dp))
        if (events.isEmpty()) Text("—", color = HoodieColors.Muted)
        events.forEach { e ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(formatClock(e.timestamp, zone), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold, modifier = Modifier.width(44.dp))
                Text("${e.emoji} ${e.text}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DailySummary, catName: String) {
    PixelPanel(Modifier.fillMaxWidth()) {
        Row {
            Column(Modifier.weight(1f)) {
                SectionLabel("Seu dia")
                if (summary.userTotals.isEmpty()) Text("—", color = HoodieColors.Muted)
                summary.userTotals.take(6).forEach { (type, ms) -> Text("${type.emoji} ${type.label}  ${formatDuration(ms)}", style = MaterialTheme.typography.bodySmall) }
            }
            Column(Modifier.weight(1f)) {
                SectionLabel(catName)
                if (summary.hoodieTotals.isEmpty()) Text("—", color = HoodieColors.Muted)
                summary.hoodieTotals.filter { it.first != HoodieActivity.COFFEE }.take(5).forEach { (a, ms) ->
                    Text("${a.emoji} ${a.label.replaceFirstChar { it.uppercase() }}  ${formatDuration(ms)}", style = MaterialTheme.typography.bodySmall)
                }
                if (summary.coffees > 0) Text("☕ ${summary.coffees} ${if (summary.coffees == 1) "café" else "cafés"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
