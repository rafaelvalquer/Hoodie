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
    private val database: dagger.Lazy<com.hoodie.app.core.database.HoodieDatabase>? = null,
    private val corrections: dagger.Lazy<com.hoodie.app.engine.correction.DiaryCorrectionService>? = null,
) : ViewModel() {
    private val _editState = MutableStateFlow<DiaryEditState?>(null)
    val editState = _editState.asStateFlow()

    fun dismissCorrection() { if (_editState.value?.saving != true) _editState.value = null }

    fun beginCorrection(item: com.hoodie.app.domain.diary.model.DiaryTimelineItem) {
        if (!com.hoodie.app.core.config.HoodieConfig.SMART_DIARY_CORRECTIONS) return
        viewModelScope.launch {
            val edit = withContext(Dispatchers.IO) {
                val db = database?.get() ?: return@withContext null
                val movementId = item.id.removePrefix("mobility-").takeIf { item.id.startsWith("mobility-") }?.toLongOrNull()
                val context = db.contextEventDao().overlapping(item.timestamp, item.timestamp + 1).lastOrNull()
                val segment = movementId?.let { db.mobilitySegmentDao().getById(it) } ?: if (item.id.startsWith("edit@") && context?.type == com.hoodie.app.core.model.UserContextType.COMMUTING) {
                    val sessions = db.mobilitySessionDao().overlapping(item.timestamp, item.timestamp + 1)
                    if (sessions.isEmpty()) null else db.mobilitySegmentDao().forSessions(sessions.map { it.id }).lastOrNull { it.startedAt <= item.timestamp && (it.endedAt == null || it.endedAt > item.timestamp) }
                } else null
                val request = if (segment != null) {
                    val session = db.mobilitySessionDao().getById(segment.sessionId)
                    com.hoodie.app.domain.correction.DiaryCorrection(com.hoodie.app.domain.correction.CorrectionTargetType.MOBILITY_SEGMENT,
                        segment.id, com.hoodie.app.core.model.UserContextType.COMMUTING, session?.destinationPlaceId, segment.startedAt, segment.endedAt, segment.mode)
                } else context?.let {
                    com.hoodie.app.domain.correction.DiaryCorrection(com.hoodie.app.domain.correction.CorrectionTargetType.CONTEXT, it.id, it.type, it.placeId, it.startedAt, it.endedAt)
                }
                DiaryEditState(request, db.placeDao().getAll(), error = if (request == null) "Não há um evento de contexto neste horário." else null)
            }
            _editState.value = edit
        }
    }

    fun beginCorrectionAt(timestamp: Long) = beginCorrection(com.hoodie.app.domain.diary.model.DiaryTimelineItem(
        id = "edit@$timestamp", timestamp = timestamp, type = com.hoodie.app.domain.diary.model.DiaryTimelineType.CONTEXT_CHANGE,
        actor = com.hoodie.app.domain.diary.model.DiaryActor.USER, title = "",
    ))

    fun saveCorrection(request: com.hoodie.app.domain.correction.DiaryCorrection) {
        val before = _editState.value ?: return
        if (before.saving) return
        _editState.value = before.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requireNotNull(corrections?.get()).save(request) }
                _editState.value = null
                dayCache.clear()
                load(_state.value.selectedDate)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _editState.value = before.copy(error = failure.message ?: "Não foi possível salvar a correção.") }
        }
    }
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
        if (com.hoodie.app.core.config.HoodieConfig.SMART_DIARY_CORRECTIONS && database != null) viewModelScope.launch {
            database.get().invalidationTracker.createFlow(
                "context_events", "mobility_sessions", "mobility_segments", "diary_corrections", "places", "hoodie_activities",
                "daily_device_usage", "daily_app_usage", "daily_context_app_usage", "daily_screen_hourly", "daily_context_usage",
                "daily_phone_timeline", "learned_routine_slots", emitInitialState = false,
            ).collect {
                dayCache.clear()
                load(_state.value.selectedDate)
            }
        }
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
        val showWake = !resume && start == diary.activityWindow.activeStartAt && diary.activityWindow.sleepBeforeStart != null
        _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.PLAYING, wakeTransition = showWake), diary, start, zone), manualChapter = null)
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            var timestamp = start
            var last = android.os.SystemClock.elapsedRealtime()
            if (showWake) {
                delay(com.hoodie.app.core.config.HoodieConfig.WAKE_TRANSITION_REAL_MS)
                _state.value = _state.value.copy(replay = _state.value.replay.copy(wakeTransition = false))
                last = android.os.SystemClock.elapsedRealtime()
            }
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

    fun pause() { replayJob?.cancel(); replayJob = null; _state.value = _state.value.copy(replay = _state.value.replay.copy(state = ReplayState.PAUSED, wakeTransition = false)) }

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
        _state.value = _state.value.copy(replay = replayAt(_state.value.replay.copy(state = ReplayState.PAUSED, wakeTransition = false), diary, at, zone))
        if (wasPlaying && at < diary.replay.endAt) play()
    }
    fun reset() {
        stopReplay()
        val current = _state.value
        val diary = current.diary
        val replay = if (diary == null) {
            ReplayUiState(speed = current.replay.speed)
        } else {
            resetReplayAt(diary, current.replay, zone)
        }
        _state.value = current.copy(replay = replay, manualChapter = null)
    }
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
    val wakingVisual = if (base.wakeTransition) visual.copy(hoodieActivity = com.hoodie.app.core.model.HoodieActivity.WAKING_UP) else visual
    return base.withVisual(wakingVisual, diary.replay.frameAt(timestamp).highlightedTimelineItemIds)
        .copy(progress = ((timestamp - diary.replay.startAt).toFloat() / span).coerceIn(0f, 1f))
}

/** Reset posiciona a interface no primeiro instante da Jornada sem iniciar a reprodução. */
internal fun resetReplayAt(
    diary: com.hoodie.app.domain.diary.model.DailyDiary,
    current: ReplayUiState,
    zone: java.time.ZoneId,
): ReplayUiState = replayAt(
    current.copy(state = ReplayState.IDLE, wakeTransition = false),
    diary,
    diary.replay.startAt,
    zone,
)
