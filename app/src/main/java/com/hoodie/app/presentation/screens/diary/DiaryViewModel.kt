package com.hoodie.app.presentation.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.diary.usecase.LoadDiaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val loadDiary: LoadDiaryUseCase,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryUiState(clock.today(), isLoading = true))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()
    private var replayJob: Job? = null
    private var loadJob: Job? = null

    init { load(_state.value.selectedDate) }

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
        _state.value = _state.value.copy(replay = _state.value.replay.copy(state = ReplayState.PLAYING, currentTimestamp = start))
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            var timestamp = start
            var last = android.os.SystemClock.elapsedRealtime()
            while (timestamp < diary.replay.endAt) {
                delay(80)
                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                val speed = _state.value.replay.speed.multiplier
                timestamp = (timestamp + (nowElapsed - last).coerceAtLeast(1) * speed * 60_000L).coerceAtMost(diary.replay.endAt)
                last = nowElapsed
                val frame = diary.replay.frameAt(timestamp)
                val span = (diary.replay.endAt - diary.replay.startAt).coerceAtLeast(1)
                _state.value = _state.value.copy(replay = _state.value.replay.copy(currentTimestamp = timestamp, activeNodeId = frame.activeNodeId, activeEdgeId = frame.activeEdgeId, progress = ((timestamp - diary.replay.startAt).toFloat() / span).coerceIn(0f, 1f)))
            }
            _state.value = _state.value.copy(replay = _state.value.replay.copy(state = ReplayState.FINISHED, currentTimestamp = diary.replay.endAt, progress = 1f, activeNodeId = diary.map.nodes.lastOrNull()?.id, activeEdgeId = null))
        }
    }

    fun pause() { replayJob?.cancel(); replayJob = null; _state.value = _state.value.copy(replay = _state.value.replay.copy(state = ReplayState.PAUSED)) }
    fun reset() { stopReplay(); _state.value = _state.value.copy(replay = ReplayUiState()) }
    fun setSpeed(speed: ReplaySpeed) { _state.value = _state.value.copy(replay = _state.value.replay.copy(speed = speed)) }
    private fun stopReplay() { replayJob?.cancel(); replayJob = null }

    private fun load(date: java.time.LocalDate) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { loadDiary(date) }
                .onSuccess { _state.value = _state.value.copy(diary = it, isLoading = false, error = null) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = "Não foi possível carregar este dia.") }
        }
    }

    override fun onCleared() { stopReplay(); super.onCleared() }
}
