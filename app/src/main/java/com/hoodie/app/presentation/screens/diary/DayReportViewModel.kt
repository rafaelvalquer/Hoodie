package com.hoodie.app.presentation.screens.diary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.IntelligenceDao
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.dayreport.DailyReport
import com.hoodie.app.domain.dayreport.GetDayReportUseCase
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.routine.LearnedRoutineSlot
import com.hoodie.app.domain.routine.RoutineEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DayReportUiState(
    val report: DailyReport? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val date: java.time.LocalDate? = null,
    val loadKey: DiaryLoadKey? = null,
)

/** Own report lifecycle; consumes DiaryViewModel's canonical diary without loading it twice. */
@HiltViewModel
class DayReportViewModel @Inject constructor(
    private val clock: ClockProvider,
    private val intelligence: IntelligenceDao,
    private val database: HoodieDatabase,
    private val getReport: GetDayReportUseCase,
    private val performance: com.hoodie.app.engine.performance.DiaryPerformanceMonitor? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(DayReportUiState())
    val state: StateFlow<DayReportUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var source: DailyDiary? = null
    private var sourceKey: DiaryLoadKey? = null
    private var today = clock.today()

    init {
        viewModelScope.launch {
            database.invalidationTracker.createFlow("learned_routine_slots", emitInitialState = false).collect {
                source?.let { load(it, today, force = true, requestKey = sourceKey) }
            }
        }
    }

    fun load(
        diary: DailyDiary,
        currentDate: java.time.LocalDate = clock.today(),
        force: Boolean = false,
        requestKey: DiaryLoadKey? = null,
    ) {
        if (!force && source === diary && sourceKey == requestKey && today == currentDate &&
            (_state.value.loading || _state.value.report?.date == diary.summary.date)) return
        source = diary
        sourceKey = requestKey
        today = currentDate
        job?.cancel()
        _state.value = DayReportUiState(loading = true, date = diary.summary.date, loadKey = requestKey)
        job = viewModelScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                val slots = withContext(Dispatchers.IO) {
                    intelligence.routineSlots().mapNotNull { row -> runCatching {
                        LearnedRoutineSlot(row.dayGroup, RoutineEventType.valueOf(row.type), row.medianMinute,
                            row.deviationMinutes, row.sampleCount, ConfidenceScore(row.confidence))
                    }.getOrNull() }
                }
                val report = withContext(Dispatchers.Default) { getReport(diary, slots, currentDate, clock.nowMillis(), clock.zone()) }
                if (source === diary && sourceKey == requestKey) {
                    performance?.record(
                        com.hoodie.app.engine.performance.DiaryPerformanceMonitor.Event.DIARY_REPORT_READY,
                        diary.summary.date,
                        requestKey?.requestId ?: 0,
                        android.os.SystemClock.elapsedRealtime() - started,
                    )
                    _state.value = DayReportUiState(report = report, date = diary.summary.date, loadKey = requestKey)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("DayReportViewModel", "Failed to assemble report", error)
                if (source === diary && sourceKey == requestKey) _state.value = DayReportUiState(error = true, date = diary.summary.date, loadKey = requestKey)
            }
        }
    }

    fun clear() {
        job?.cancel()
        source = null
        sourceKey = null
        _state.value = DayReportUiState()
    }
}
