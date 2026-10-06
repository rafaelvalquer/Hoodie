package com.hoodie.app.presentation.screens.diary.clock

import com.hoodie.app.domain.diary.clock.ClockSegment
import com.hoodie.app.domain.diary.clock.DayClockData
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState

/**
 * Estado da aba RELÓGIO. Em [Mode.REPLAY] o "agora" do mostrador é o tempo do replay,
 * para relógio e Jornada contarem a mesma história.
 */
data class DayClockUiState(
    val data: DayClockData?,
    val selectedId: String?,
    val mode: Mode,
    /** Minuto do replay no mostrador (só em REPLAY). */
    val replayMinute: Int? = null,
) {
    enum class Mode { LIVE, REPLAY }

    /** O "agora" exibido: replay, minuto atual (hoje) ou null (dia passado). */
    val nowMinute: Int? get() = if (mode == Mode.REPLAY) replayMinute else data?.nowMinute

    val selected: ClockSegment? get() = data?.segment(selectedId)

    /** Trecho onde o Hoodie está no "agora" exibido. */
    val current: ClockSegment? get() = nowMinute?.let { m -> data?.segmentAt((m - 0.01f).coerceAtLeast(0f)) }

    companion object {
        fun of(data: DayClockData?, selectedId: String?, replay: ReplayUiState): DayClockUiState {
            val ts = replay.currentTimestamp
            val replaying = replay.state != ReplayState.IDLE && ts != null && data != null
            val minute = if (replaying) ((ts!! - data!!.dayStart.toEpochMilli()) / 60_000L).toInt().coerceIn(0, data.dayLengthMinutes) else null
            return DayClockUiState(data, selectedId, if (replaying) Mode.REPLAY else Mode.LIVE, minute)
        }
    }
}
