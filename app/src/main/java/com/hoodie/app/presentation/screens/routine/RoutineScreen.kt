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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.currentDateFlow
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.domain.routine.EffectiveDayRoutine
import com.hoodie.app.domain.routine.RoutineEventType
import com.hoodie.app.domain.routine.RoutineValueSource
import com.hoodie.app.core.time.formatHm
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import com.hoodie.app.presentation.components.PixelPanel
import kotlinx.coroutines.flow.stateIn
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.TimeField
import com.hoodie.app.presentation.screens.onboarding.DayToggles
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val hoodie: HoodieEngine,
    private val clock: ClockProvider,
    private val contextEvents: ContextEventDao,
    effectiveRoutines: com.hoodie.app.data.repository.EffectiveRoutineRepository,
) : ViewModel() {
    /** Rotina efetiva de hoje: mostra quais horários o aprendizado está ajustando. */
    val effective = effectiveRoutines.observeToday()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    fun setLearnedRoutineEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setLearnedRoutineEnabled(enabled)
        // O Hoodie e o contexto passam a seguir a nova rotina na hora.
        hoodie.resolve()
    }
    private data class DayOffObservation(
        val date: LocalDate,
        val isDayOff: Boolean? = null,
        val error: RoutineDayExceptionError? = null,
    )

    private val _dayExceptionState = MutableStateFlow(RoutineDayExceptionUiState(clock.today()))
    val dayExceptionState = _dayExceptionState.asStateFlow()
    private val dayOffMutation = Mutex()
    private var pendingDayOff: Pair<LocalDate, Boolean>? = null

    init {
        viewModelScope.launch {
            currentDateFlow(clock)
                .distinctUntilChanged()
                .onEach { date ->
                    if (pendingDayOff?.first != date) pendingDayOff = null
                    _dayExceptionState.update { current ->
                        if (current.date == date) current
                        else current.copy(
                            date = date,
                            isDayOff = false,
                            loading = true,
                            error = null,
                            confirmationRequired = false,
                        )
                    }
                }
                .flatMapLatest { date ->
                    routines.observeDayOff(date)
                        .map { DayOffObservation(date, isDayOff = it) }
                        .catch { emit(DayOffObservation(date, error = RoutineDayExceptionError.LOAD)) }
                }
                .collect { observation ->
                    _dayExceptionState.update { current ->
                        if (current.date != observation.date) current
                        else current.copy(
                            isDayOff = observation.isDayOff ?: current.isDayOff,
                            loading = false,
                            error = observation.error,
                        )
                    }
                }
        }
    }

    suspend fun load(): Pair<Routine, SleepSchedule> = routines.get() to settings.current().sleep

    fun save(r: Routine, sleep: SleepSchedule, done: () -> Unit) = viewModelScope.launch {
        routines.save(r, clock.nowMillis())
        settings.setSleep(sleep)
        hoodie.resolve()
        done()
    }

    /** Salva a exceção explicitamente; a data é resolvida pelo relógio no momento do toque. */
    fun setTodayDayOff(enabled: Boolean) {
        val date = clock.today()
        val current = _dayExceptionState.value
        if (current.date != date || current.loading || current.error == RoutineDayExceptionError.LOAD ||
            current.isDayOff == enabled || !dayOffMutation.tryLock()) return

        _dayExceptionState.update { it.copy(saving = true, error = null, confirmationRequired = false) }
        viewModelScope.launch {
            var failure = RoutineDayExceptionError.WORK_CHECK
            try {
                if (enabled) {
                    val start = startOfDay(date, clock.zone())
                    val end = startOfDay(date.plusDays(1), clock.zone())
                    val workAlreadyRecorded = withContext(Dispatchers.IO) {
                        contextEvents.overlapping(start, end).any { it.type == UserContextType.WORK }
                    }
                    if (workAlreadyRecorded) {
                        pendingDayOff = date to enabled
                        _dayExceptionState.update {
                            if (it.date == date) it.copy(saving = false, confirmationRequired = true) else it
                        }
                        return@launch
                    }
                }
                failure = RoutineDayExceptionError.SAVE
                saveDayOff(date, enabled)
            } catch (_: TimeoutCancellationException) {
                showDayOffFailure(date, RoutineDayExceptionError.SAVE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showDayOffFailure(date, failure)
            } finally {
                dayOffMutation.unlock()
            }
        }
    }

    fun confirmDayOffAfterWork() {
        val pending = pendingDayOff ?: return
        if (pending.first != clock.today() || !dayOffMutation.tryLock()) {
            pendingDayOff = null
            _dayExceptionState.update { it.copy(confirmationRequired = false) }
            return
        }
        pendingDayOff = null
        _dayExceptionState.update { it.copy(saving = true, error = null, confirmationRequired = false) }
        viewModelScope.launch {
            try {
                saveDayOff(pending.first, pending.second)
            } catch (_: TimeoutCancellationException) {
                showDayOffFailure(pending.first, RoutineDayExceptionError.SAVE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showDayOffFailure(pending.first, RoutineDayExceptionError.SAVE)
            } finally {
                dayOffMutation.unlock()
            }
        }
    }

    fun cancelDayOffAfterWork() {
        pendingDayOff = null
        _dayExceptionState.update { it.copy(confirmationRequired = false) }
    }

    private suspend fun saveDayOff(date: LocalDate, enabled: Boolean) {
        routines.setDayOff(date, enabled, clock.nowMillis())
        withTimeout(8_000) {
            routines.observeDayOff(date).first { observed -> observed == enabled }
        }
        _dayExceptionState.update { current ->
            if (current.date == date) current.copy(isDayOff = enabled, saving = false, error = null)
            else current.copy(saving = false)
        }
    }

    private suspend fun showDayOffFailure(date: LocalDate, error: RoutineDayExceptionError) {
        val persisted = try { withContext(Dispatchers.IO) { routines.isDayOff(date) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        _dayExceptionState.update { current ->
            if (current.date == date) current.copy(
                isDayOff = persisted ?: current.isDayOff,
                saving = false,
                error = error,
                confirmationRequired = false,
            ) else current.copy(saving = false)
        }
    }
}

@Composable
fun RoutineScreen(onBack: () -> Unit, vm: RoutineViewModel = hiltViewModel()) {
    var routine by remember { mutableStateOf<Routine?>(null) }
    var sleep by remember { mutableStateOf(SleepSchedule()) }
    val dayException by vm.dayExceptionState.collectAsStateWithLifecycle()
    val effective by vm.effective.collectAsStateWithLifecycle()
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
            LearnedHints(effective, listOf(RoutineEventType.WORK_START to "Entrada", RoutineEventType.WORK_END to "Saída"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Almoço", r.lunchStartMinute, { routine = r.copy(lunchStartMinute = it) }, Modifier.weight(1f))
                TimeField("Volta", r.lunchEndMinute, { routine = r.copy(lunchEndMinute = it) }, Modifier.weight(1f))
            }
            LearnedHints(effective, listOf(RoutineEventType.LUNCH_START to "Almoço", RoutineEventType.LUNCH_END to "Volta"))
            SectionLabel("Dias")
            DayToggles(r.days, { d -> routine = r.copy(days = if (d in r.days) r.days - d else r.days + d) })
            RoutineDayExceptionSection(
                state = dayException,
                onDayOffChanged = vm::setTodayDayOff,
                onConfirmWorkWarning = vm::confirmDayOffAfterWork,
                onCancelWorkWarning = vm::cancelDayOffAfterWork,
            )
        }
        SectionLabel("Sono (o Hoodie acompanha)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Acordar", sleep.wakeMinute, { sleep = sleep.copy(wakeMinute = it) }, Modifier.weight(1f))
            TimeField("Dormir", sleep.sleepMinute, { sleep = sleep.copy(sleepMinute = it) }, Modifier.weight(1f))
        }
        LearnedHints(effective, listOf(RoutineEventType.WAKE to "Acordar", RoutineEventType.SLEEP to "Dormir"))
        LearnedRoutineSection(effective, onToggle = vm::setLearnedRoutineEnabled)
        PixelButton("Salvar", { vm.save(r, sleep, onBack) }, Modifier.fillMaxWidth())
        PixelButton("Voltar", onBack, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
    }
}

/** Texto puro da dica de um horário (testável): null quando o horário segue o configurado. */
internal fun learnedHintText(label: String, effective: EffectiveDayRoutine?, type: RoutineEventType): String? {
    val o = effective?.takeIf { it.learningEnabled }?.origin(type)?.takeIf { it.learned } ?: return null
    val manual = o.manualMinute?.let { " (configurado ${formatHm(it)})" }.orEmpty()
    return when (o.source) {
        RoutineValueSource.LEARNED -> "✨ $label aprendido: ${formatHm(o.effectiveMinute)}$manual"
        RoutineValueSource.LEARNED_LIMITED ->
            "✨ $label ajustado até o limite: ${formatHm(o.effectiveMinute)}$manual · seu padrão é ${o.learnedMinute?.let(::formatHm)}"
        RoutineValueSource.MANUAL -> null
    }
}

/** Grupo de dias do aprendizado, para a pessoa saber a que dias a dica se refere. */
internal fun dayGroupLabel(group: String): String = when (group) {
    "SATURDAY" -> "sábados"
    "SUNDAY" -> "domingos"
    else -> "dias úteis"
}

@Composable
private fun LearnedHints(effective: EffectiveDayRoutine?, fields: List<Pair<RoutineEventType, String>>) {
    fields.forEach { (type, label) ->
        learnedHintText(label, effective, type)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Mint)
        }
    }
}

/** Interruptor do ajuste aprendido + marcos que só o aprendizado conhece (sair de casa, academia, voltar). */
@Composable
private fun LearnedRoutineSection(effective: EffectiveDayRoutine?, onToggle: (Boolean) -> Unit) {
    val e = effective ?: return
    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Rotina aprendida")
                Text("Ajustar a rotina pelo que o Hoodie aprendeu", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Usa só padrões confiáveis dos ${dayGroupLabel(e.dayGroup)} e muda cada horário no máximo 1h30 em relação ao configurado.",
                    style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted,
                )
            }
            Switch(e.learningEnabled, onToggle)
        }
        if (e.learningEnabled) {
            val extras = listOf(
                RoutineEventType.LEAVE_HOME to "🚪 Sair de casa",
                RoutineEventType.GYM_START to "🏋 Academia",
                RoutineEventType.HOME_RETURN to "🏠 Volta para casa",
            ).mapNotNull { (type, label) -> e.origin(type)?.let { "$label ~${formatHm(it.effectiveMinute)}" } }
            when {
                e.learnedCount == 0 -> Text("Ainda sem padrões confiáveis: o Hoodie segue os horários configurados.", style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
                extras.isNotEmpty() -> Text("Também percebi: " + extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink)
            }
        }
    }
}