package com.hoodie.app.presentation.screens.demo

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.engine.demo.*
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.screens.diary.ReplayState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DemoReplayUiState(val presentation: DemoReplayPresentation, val previewEnabled: Boolean = false, val visible: Boolean = false)

/** Owns only the fictitious session. No repository, DAO, settings, location, phone or notifier. */
@HiltViewModel
class DemoReplayViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    val zone: ZoneId = runCatching { ZoneId.of(saved.get<String>(ZONE) ?: ZoneId.systemDefault().id) }.getOrElse { ZoneId.systemDefault() }
    val date: LocalDate = runCatching { LocalDate.parse(saved.get<String>(DATE) ?: LocalDate.now(zone).toString()) }.getOrElse { LocalDate.now(zone) }
    val timeline = DemoReplayTimeline(DemoDayFactory.create(date, zone))
    val view = DemoDiaryAssembler.assemble(timeline.day)
    val model = JourneyOverworldModel.build(view.journey, zone)
    private val restored = DemoReplaySnapshot(saved[TIMESTAMP] ?: timeline.startAt,
        readEnum(STATE, ReplayState.IDLE), readEnum(MODE, DemoReplayMode.QUICK_TOUR),
        readEnum(SPEED, ReplaySpeed.FAST), saved[TOUR_ELAPSED] ?: 0)
    private val controller = DemoReplayController(timeline, restored)
    private var resumeWhenVisible = saved.get<Boolean>(RESUME) == true || restored.state == ReplayState.PLAYING
    private var preview = saved.get<Boolean>(PREVIEW) ?: false
    private var visible = false
    private var cleared = false
    private var playbackJob: Job? = null
    private var generation = 0L
    /** Replaced by a controlled monotonic clock in tests; never a wall-clock source. */
    internal var monotonicMillis: () -> Long = SystemClock::elapsedRealtime
    internal val hasPlaybackJob: Boolean get() = playbackJob?.isActive == true
    private val _state = MutableStateFlow(DemoReplayUiState(DemoReplayPresentation.assemble(timeline, view, controller.snapshot), preview))
    val state = _state.asStateFlow()

    init { saved[DATE] = date.toString(); saved[ZONE] = zone.id; publish() }

    fun onVisible() {
        if (cleared || visible) return
        visible = true
        if (resumeWhenVisible && controller.snapshot.state != ReplayState.FINISHED) controller.play(monotonicMillis())
        resumeWhenVisible = false
        publish(); synchronizeDriver()
    }

    fun onHidden() {
        if (!visible || cleared) return
        resumeWhenVisible = controller.snapshot.state == ReplayState.PLAYING
        visible = false
        controller.pause(monotonicMillis())
        cancelDriver(); publish()
    }

    fun exit() {
        visible = false; resumeWhenVisible = false
        controller.pause(monotonicMillis())
        cancelDriver(); publish()
    }

    fun togglePlayback() {
        if (cleared) return
        if (!visible) {
            resumeWhenVisible = !resumeWhenVisible
            controller.pause(monotonicMillis())
        } else {
            resumeWhenVisible = false
            if (controller.snapshot.state == ReplayState.PLAYING) controller.pause(monotonicMillis()) else controller.play(monotonicMillis())
        }
        publish(); synchronizeDriver()
    }

    fun restart() { resumeWhenVisible = false; controller.restart(); publish(); synchronizeDriver() }
    fun seek(timestamp: Long) { controller.seek(timestamp, monotonicMillis()); afterNavigation() }
    fun next() { controller.next(monotonicMillis()); afterNavigation() }
    fun previous() { controller.previous(monotonicMillis()); afterNavigation() }
    fun setSpeed(speed: ReplaySpeed) { controller.setSpeed(speed, monotonicMillis()); publish(); synchronizeDriver() }
    fun setMode(mode: DemoReplayMode) {
        if (mode != controller.snapshot.mode) resumeWhenVisible = false
        controller.setMode(mode, monotonicMillis()); publish(); synchronizeDriver()
    }
    fun setPreview(enabled: Boolean) { preview = enabled; publish() }

    private fun afterNavigation() {
        if (controller.snapshot.state == ReplayState.FINISHED) resumeWhenVisible = false
        publish(); synchronizeDriver()
    }

    private fun synchronizeDriver() {
        if (cleared || !visible || controller.snapshot.state != ReplayState.PLAYING) { cancelDriver(); return }
        if (playbackJob?.isActive == true) return
        val ownGeneration = ++generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (isActive && visible && generation == ownGeneration) {
                    controller.tick(monotonicMillis())
                    if (controller.snapshot.state == ReplayState.FINISHED) resumeWhenVisible = false
                    publish()
                    if (controller.snapshot.state != ReplayState.PLAYING) break
                    delay(33) // Refresh frequency only: elapsed monotonic time is the playback clock.
                }
            } finally { if (generation == ownGeneration) playbackJob = null }
        }
        playbackJob = job
        job.start()
    }

    private fun cancelDriver() { generation++; playbackJob?.cancel(); playbackJob = null }

    private fun publish() {
        val s = controller.snapshot
        _state.value = DemoReplayUiState(DemoReplayPresentation.assemble(timeline, view, s), preview, visible)
        saved[TIMESTAMP] = s.timestamp; saved[STATE] = s.state.name; saved[MODE] = s.mode.name
        saved[SPEED] = s.speed.name; saved[TOUR_ELAPSED] = s.tourElapsedMs
        saved[RESUME] = resumeWhenVisible; saved[PREVIEW] = preview
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T =
        saved.get<String>(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    override fun onCleared() {
        cleared = true; visible = false; resumeWhenVisible = false
        controller.pause(monotonicMillis()); cancelDriver(); publish()
        super.onCleared()
    }

    companion object {
        internal const val DATE = "demo_v02_date"
        internal const val ZONE = "demo_v02_zone"
        private const val TIMESTAMP = "demo_v02_timestamp"
        private const val STATE = "demo_v02_state"
        private const val MODE = "demo_v02_mode"
        private const val SPEED = "demo_v02_speed"
        private const val TOUR_ELAPSED = "demo_v02_tour_elapsed"
        private const val RESUME = "demo_v02_resume"
        private const val PREVIEW = "demo_v02_preview"
    }
}
