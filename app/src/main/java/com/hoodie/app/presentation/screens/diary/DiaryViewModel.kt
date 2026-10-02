package com.hoodie.app.presentation.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.currentDateFlow
import com.hoodie.app.domain.diary.usecase.LoadDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val loadDiary: LoadDiaryUseCase,
    private val clock: ClockProvider,
) : ViewModel() {
    val zone get() = clock.zone()
    private var observedToday = clock.today()
    private val _state = MutableStateFlow(DiaryUiState(clock.today(), today = clock.today(), isLoading = true))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()
    private var replayJob: Job? = null
    private var loadJob: Job? = null

    init {
        load(_state.value.selectedDate)
        viewModelScope.launch {
            currentDateFlow(clock).collect { date ->
                if (date != observedToday) {
                    val wasToday = _state.value.selectedDate == observedToday
                    observedToday = date
                    _state.value = _state.value.copy(today = date)
                    if (wasToday) selectDate(date)
                }
            }
        }
    }

    fun selectDate(date: java.time.LocalDate) {
        if (date.isAfter(clock.today())) return
        stopReplay()
        _state.value = _state.value.copy(selectedDate = date, diary = null, isLoading = true, error = null, replay = ReplayUiState())
        load(date)
    }

    fun play() {
        val diary = _state.value.diary ?: return
        if (diary.replay.endAt <= diary.replay.startAt) return
        val resume = _state.value.replay.state == ReplayState.PAUSED
        val start = if (resume) _state.value.replay.currentTimestamp ?: diary.replay.startAt else diary.replay.startAt
        _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.PLAYING), diary, start, zone))
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            var timestamp = start
            var last = android.os.SystemClock.elapsedRealtime()
            while (timestamp < diary.replay.endAt) {
                delay(80)
                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                timestamp = advanceReplay(timestamp, nowElapsed - last, _state.value.replay.speed, diary.replay.endAt)
                last = nowElapsed
                _state.value = _state.value.copy(replay = replayAt(_state.value.replay, diary, timestamp, zone))
            }
            _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.FINISHED), diary, diary.replay.endAt, zone))
        }
    }

    fun pause() { replayJob?.cancel(); replayJob = null; _state.value = _state.value.copy(replay = _state.value.replay.copy(state = ReplayState.PAUSED)) }
    fun reset() { stopReplay(); _state.value = _state.value.copy(replay = ReplayUiState()) }
    fun setSpeed(speed: ReplaySpeed) { _state.value = _state.value.copy(replay = _state.value.replay.copy(speed = speed)) }
    private fun stopReplay() { replayJob?.cancel(); replayJob = null }

    private fun load(date: java.time.LocalDate) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val diary = loadDiary(date)
                if (_state.value.selectedDate == date) _state.value = _state.value.copy(diary = diary, isLoading = false, error = null, replay = ReplayUiState())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_state.value.selectedDate == date) _state.value = _state.value.copy(isLoading = false, error = "Não foi possível carregar este dia.")
            }
        }
    }

    override fun onCleared() { stopReplay(); super.onCleared() }
}

/** Avança o relógio do replay: [elapsedMs] reais × minutos por segundo da velocidade. */
internal fun advanceReplay(timestamp: Long, elapsedMs: Long, speed: ReplaySpeed, endAt: Long): Long =
    (timestamp + elapsedMs.coerceAtLeast(1) * speed.minutesPerSecond * 60L).coerceAtMost(endAt)

/**
 * Estado do replay em [timestamp]: mapa (nó/trecho/progresso), contexto, atividade do
 * Hoodie, app ativo e período do dia, todos de [ReplayHudAssembler].
 */
internal fun replayAt(base: ReplayUiState, diary: com.hoodie.app.domain.diary.model.DailyDiary, timestamp: Long, zone: java.time.ZoneId): ReplayUiState {
    val visual = com.hoodie.app.engine.diary.ReplayHudAssembler.assemble(diary.replay, diary.phoneInsights, timestamp, zone)
    val span = (diary.replay.endAt - diary.replay.startAt).coerceAtLeast(1)
    return base.withVisual(visual, diary.replay.frameAt(timestamp).highlightedTimelineItemIds)
        .copy(progress = ((timestamp - diary.replay.startAt).toFloat() / span).coerceIn(0f, 1f))
}
