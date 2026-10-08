package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.ReplayPhoneApp
import com.hoodie.app.domain.diary.model.ReplayVisualState
import java.time.LocalDate

enum class ReplayState { IDLE, PLAYING, PAUSED, FINISHED }

/** Quantos minutos do dia passam por segundo de replay. */
enum class ReplaySpeed(val minutesPerSecond: Int, val label: String) {
    NORMAL(1, "1 min/s"),
    FAST(5, "5 min/s"),
    VERY_FAST(10, "10 min/s"),
}

/**
 * Estado do replay na tela. O mapa usa nó/trecho + progresso (não há mais
 * coordenadas de marcador próprias): uma única fonte de verdade.
 */
data class ReplayUiState(
    val state: ReplayState = ReplayState.IDLE,
    val currentTimestamp: Long? = null,
    val speed: ReplaySpeed = ReplaySpeed.NORMAL,
    val activeNodeId: String? = null,
    val activeEdgeId: String? = null,
    /** Quanto do deslocamento atual já foi andado (0..1). */
    val edgeProgress: Float = 0f,
    val currentContext: UserContextType? = null,
    val currentHoodieActivity: HoodieActivity? = null,
    val activePhoneApp: ReplayPhoneApp? = null,
    /** Período pelo horário do replay — vale também depois de FINISHED. */
    val dayPeriod: DayPeriod = DayPeriod.DAY,
    val highlightedTimelineItemIds: Set<String> = emptySet(),
    val progress: Float = 0f,
    /** Animação curta de apresentação; não altera o instante do replay. */
    val wakeTransition: Boolean = false,
) {
    /** PLAYING/PAUSED: o mapa mostra o dia "até agora" (visitado / atual / ainda não visitado). */
    val replaying: Boolean get() = state == ReplayState.PLAYING || state == ReplayState.PAUSED

    /** Há um horário de replay (inclusive FINISHED): é ele que decide a luz do mapa. */
    val hasReplayTimestamp: Boolean get() = currentTimestamp != null

    fun withVisual(v: ReplayVisualState, highlighted: Set<String> = highlightedTimelineItemIds) = copy(
        currentTimestamp = v.timestamp,
        activeNodeId = v.activeNodeId,
        activeEdgeId = v.activeTripId,
        edgeProgress = v.tripProgress,
        currentContext = v.context,
        currentHoodieActivity = v.hoodieActivity,
        activePhoneApp = v.activePhoneApp,
        dayPeriod = v.dayPeriod,
        highlightedTimelineItemIds = highlighted,
    )

    val visual: ReplayVisualState
        get() = ReplayVisualState(currentTimestamp, currentContext, currentHoodieActivity, activePhoneApp, activeNodeId, activeEdgeId, edgeProgress, dayPeriod)
}

data class DiaryUiState(
    val selectedDate: LocalDate,
    val today: LocalDate = selectedDate,
    val diary: DailyDiary? = null,
    val dayReport: com.hoodie.app.domain.dayreport.DailyReport? = null,
    val dayReportError: Boolean = false,
    val isLoading: Boolean = false,
    val error: com.hoodie.app.core.error.AppError? = null,
    val replay: ReplayUiState = ReplayUiState(),
    /** [JORNADA] ou [RELÓGIO] (DataStore); o replay é o mesmo nas duas. */
    val mapMode: com.hoodie.app.domain.diary.journey.DiaryMapMode = com.hoodie.app.domain.diary.journey.DiaryMapMode.JOURNEY,
    /** Capítulo que o usuário abriu (limpo ao trocar de data ou dar play). */
    val manualChapter: com.hoodie.app.domain.diary.journey.DayChapter? = null,
    /** Relógio do Dia 2.0: montado junto com o Diário (Default) e a cada minuto hoje. */
    val dayClock: com.hoodie.app.domain.diary.clock.DayClockData? = null,
    /** Trecho escolhido no mostrador ou na lista (null = acompanha o agora). */
    val clockSelectedId: String? = null,
)
