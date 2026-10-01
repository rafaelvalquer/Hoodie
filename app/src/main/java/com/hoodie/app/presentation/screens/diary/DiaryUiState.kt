package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.domain.diary.model.DailyDiary
import java.time.LocalDate

enum class ReplayState { IDLE, PLAYING, PAUSED, FINISHED }
enum class ReplaySpeed(val multiplier: Int) { X1(1), X5(5), X10(10) }

data class ReplayUiState(
    val state: ReplayState = ReplayState.IDLE,
    val currentTimestamp: Long? = null,
    val speed: ReplaySpeed = ReplaySpeed.X1,
    val activeNodeId: String? = null,
    val activeEdgeId: String? = null,
    val markerX: Float? = null,
    val markerY: Float? = null,
    val highlightedTimelineItemIds: Set<String> = emptySet(),
    val progress: Float = 0f,
)

data class DiaryUiState(
    val selectedDate: LocalDate,
    val today: LocalDate = selectedDate,
    val diary: DailyDiary? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val replay: ReplayUiState = ReplayUiState(),
)
