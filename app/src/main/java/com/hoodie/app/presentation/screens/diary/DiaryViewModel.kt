package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.error.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.lifecycle.ViewModel
import android.util.Log
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val loadDiary: LoadDiaryUseCase,
    private val clock: ClockProvider,
    private val settings: com.hoodie.app.core.datastore.SettingsRepository,
) : ViewModel() {
    val nowMillis: Long get() = clock.nowMillis()
    val zone get() = clock.zone()
    private var observedToday = clock.today()
    private val _state = MutableStateFlow(DiaryUiState(clock.today(), today = clock.today(), isLoading = true))
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()
    private val _events = Channel<DiaryUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun openPlace(nodeId: String) {
        _events.trySend(DiaryUiEvent.ShowPlaceDetails(_state.value.selectedDate, nodeId))
    }

    private var replayJob: Job? = null
    private var loadJob: Job? = null
    // Cache limitado à sessão da tela. Hoje permanece atualizável; dias passados
    // são reutilizados ao alternar datas sem reconstruir mapa e replay.
    private val dayCache = linkedMapOf<java.time.LocalDate, com.hoodie.app.domain.diary.model.DailyDiary>()

    init {
        load(_state.value.selectedDate)
        viewModelScope.launch {
            settings.settings.collect { s ->
                val mode = com.hoodie.app.domain.diary.journey.DiaryMapMode.parse(s.diaryMapMode)
                if (mode != _state.value.mapMode) _state.value = _state.value.copy(mapMode = mode)
            }
        }
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
        _state.value = _state.value.copy(selectedDate = date, diary = null, isLoading = true, error = null, replay = ReplayUiState(), manualChapter = null, dayClock = null, clockSelectedId = null)
        load(date)
    }

    fun retry() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        dayCache.remove(_state.value.selectedDate)
        load(_state.value.selectedDate)
    }

    fun play() {
        val diary = _state.value.diary ?: return
        if (diary.replay.endAt <= diary.replay.startAt) return
        val resume = _state.value.replay.state == ReplayState.PAUSED
        val start = if (resume) _state.value.replay.currentTimestamp ?: diary.replay.startAt else diary.replay.startAt
        _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.PLAYING), diary, start, zone), manualChapter = null)
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

    /**
     * Pula o replay para [timestamp] (⏮ / ⏭ / barra temporal). Tocando, continua
     * tocando de lá; parado, fica pausado naquele instante.
     */
    fun seekTo(timestamp: Long) {
        val diary = _state.value.diary ?: return
        if (diary.replay.endAt <= diary.replay.startAt) return
        val wasPlaying = _state.value.replay.state == ReplayState.PLAYING
        stopReplay()
        val at = timestamp.coerceIn(diary.replay.startAt, diary.replay.endAt)
        _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.PAUSED), diary, at, zone))
        if (wasPlaying && at < diary.replay.endAt) play()
    }
    fun reset() { stopReplay(); _state.value = _state.value.copy(replay = ReplayUiState(speed = _state.value.replay.speed)) }
    /** Troca a visualização sem mexer no replay (hora, velocidade e play/pausa continuam). */
    fun setMapMode(mode: com.hoodie.app.domain.diary.journey.DiaryMapMode) {
        _state.value = _state.value.copy(mapMode = mode)
        viewModelScope.launch { settings.setDiaryMapMode(mode.name) }
    }

    /** Usuário abriu um capítulo (com o replay pausado ou parado). */
    fun openChapter(chapter: com.hoodie.app.domain.diary.journey.DayChapter?) { _state.value = _state.value.copy(manualChapter = chapter) }

    /** Trecho escolhido no relógio ou na lista; null volta ao "agora". */
    fun selectClockSegment(id: String?) { _state.value = _state.value.copy(clockSelectedId = id) }

    /** Chamado a cada minuto pela aba RELÓGIO visível: refaz o dia de hoje até o novo "agora". */
    fun refreshClock() {
        val s = _state.value
        val diary = s.diary ?: return
        if (s.selectedDate != clock.today()) return
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { assembleClock(diary, s.selectedDate) }
            if (_state.value.diary === diary) _state.value = _state.value.copy(dayClock = data)
        }
    }

    private fun assembleClock(diary: com.hoodie.app.domain.diary.model.DailyDiary, date: java.time.LocalDate) =
        if (com.hoodie.app.core.config.HoodieConfig.DIARY_DAY_CLOCK_V2) com.hoodie.app.engine.diary.DayClockAssembler.build(diary, date, zone, clock.nowMillis()) else null

    fun setSpeed(speed: ReplaySpeed) { _state.value = _state.value.copy(replay = _state.value.replay.copy(speed = speed)) }
    private fun stopReplay() { replayJob?.cancel(); replayJob = null }

    private fun load(date: java.time.LocalDate) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val diary = dayCache[date]?.takeIf { date.isBefore(clock.today()) } ?: loadDiary(date)
                if (date.isBefore(clock.today())) {
                    dayCache[date] = diary
                    if (dayCache.size > 7) dayCache.remove(dayCache.keys.first())
                }
                val dayClock = withContext(Dispatchers.Default) { assembleClock(diary, date) }
                if (_state.value.selectedDate == date) _state.value = _state.value.copy(diary = diary, isLoading = false, error = null, replay = ReplayUiState(), dayClock = dayClock)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("DiaryViewModel", "Failed to load diary", error)
                if (_state.value.selectedDate == date) _state.value = _state.value.copy(isLoading = false, error = DatabaseError.ReadFailed)
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
