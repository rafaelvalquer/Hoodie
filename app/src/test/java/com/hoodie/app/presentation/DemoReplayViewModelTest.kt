package com.hoodie.app.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import android.app.Application
import com.hoodie.app.engine.demo.DemoReplayMode
import com.hoodie.app.presentation.screens.demo.DemoReplayViewModel
import com.hoodie.app.presentation.screens.diary.ReplaySpeed
import com.hoodie.app.presentation.screens.diary.ReplayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DemoReplayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun handle() = SavedStateHandle(mapOf(DemoReplayViewModel.DATE to "2026-10-15", DemoReplayViewModel.ZONE to "America/Sao_Paulo"))
    private fun vm(handle: SavedStateHandle = handle()): DemoReplayViewModel = DemoReplayViewModel(handle).also { vm ->
        vm.monotonicMillis = { dispatcher.scheduler.currentTime }
        stores += ViewModelStore().apply { put("demo", vm) }
    }

    @Test fun `user pause survives leaving and returning without an active background job`() = runTest(dispatcher) {
        val vm = vm()
        vm.onVisible(); assertFalse(vm.hasPlaybackJob)
        vm.togglePlayback(); runCurrent(); advanceTimeBy(1_000)
        vm.togglePlayback()
        val paused = vm.state.value.presentation.source
        assertEquals(ReplayState.PAUSED, paused.state)
        assertEquals(vm.timeline.timestampAtTourElapsed(1_000), paused.timestamp)
        vm.onHidden(); assertFalse(vm.hasPlaybackJob)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(paused, vm.state.value.presentation.source)
        vm.onVisible(); runCurrent()
        assertEquals(ReplayState.PAUSED, vm.state.value.presentation.source.state)
        assertFalse(vm.hasPlaybackJob)
    }

    @Test fun `lifecycle pause excludes hidden elapsed time and resumes only a previously playing session`() = runTest(dispatcher) {
        val vm = vm()
        vm.onVisible(); vm.togglePlayback(); runCurrent(); advanceTimeBy(1_000)
        vm.onHidden()
        val at = vm.state.value.presentation.source.timestamp
        assertFalse(vm.hasPlaybackJob)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(at, vm.state.value.presentation.source.timestamp)
        vm.onVisible(); runCurrent()
        assertEquals(at, vm.state.value.presentation.source.timestamp)
        assertTrue(vm.hasPlaybackJob)
        advanceTimeBy(1_000); vm.togglePlayback()
        assertEquals(vm.timeline.timestampAtTourElapsed(2_000), vm.state.value.presentation.source.timestamp)
    }

    @Test fun `saved primitives restore date mode speed preview and a user paused position`() = runTest(dispatcher) {
        val saved = handle(); val original = vm(saved)
        original.onVisible(); original.setMode(DemoReplayMode.FULL_DAY)
        original.setSpeed(ReplaySpeed.VERY_FAST); original.setPreview(true)
        val at = original.timeline.day.activities[4].startedAt
        original.seek(at); original.onHidden()
        val values = saved.keys().associateWith { saved.get<Any?>(it) }
        assertTrue(values.values.all { it is String || it is Long || it is Boolean })
        val restored = vm(SavedStateHandle(values))
        assertEquals(original.date, restored.date); assertEquals(original.zone, restored.zone)
        assertEquals(at, restored.state.value.presentation.source.timestamp)
        assertEquals(DemoReplayMode.FULL_DAY, restored.state.value.presentation.source.mode)
        assertEquals(ReplaySpeed.VERY_FAST, restored.state.value.presentation.source.speed)
        assertTrue(restored.state.value.previewEnabled)
        restored.onVisible(); runCurrent()
        assertEquals("10:30", restored.state.value.presentation.clockLabel)
        assertEquals(ReplayState.PAUSED, restored.state.value.presentation.source.state)
        assertFalse(restored.hasPlaybackJob)
    }

    @Test fun `exit and finish cancel playback and cannot auto restart on reentry`() = runTest(dispatcher) {
        val vm = vm()
        vm.onVisible(); vm.togglePlayback(); runCurrent(); advanceTimeBy(50_100); runCurrent()
        assertEquals(ReplayState.FINISHED, vm.state.value.presentation.source.state)
        assertEquals("20:00", vm.state.value.presentation.clockLabel)
        assertFalse(vm.hasPlaybackJob)
        vm.togglePlayback(); runCurrent(); assertTrue(vm.hasPlaybackJob)
        vm.exit(); assertFalse(vm.hasPlaybackJob)
        val at = vm.state.value.presentation.source.timestamp
        advanceTimeBy(60_000); vm.onVisible(); runCurrent()
        assertEquals(at, vm.state.value.presentation.source.timestamp)
        assertEquals(ReplayState.PAUSED, vm.state.value.presentation.source.state)
        assertFalse(vm.hasPlaybackJob)
    }
}
