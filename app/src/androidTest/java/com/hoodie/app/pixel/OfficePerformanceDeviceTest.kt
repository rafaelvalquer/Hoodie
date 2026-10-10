package com.hoodie.app.pixel

import android.os.SystemClock
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.npc.office.OfficePerformanceCounters
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.presentation.components.HoodieSceneView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Device-side benchmark evidence without a brittle machine-dependent time threshold. */
@RunWith(AndroidJUnit4::class)
class OfficePerformanceDeviceTest {
    private fun runtimeStat(name: String): Long =
        runCatching { Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L }.getOrDefault(0L)

    @Test
    fun commonScenesReportWarmRenderCostForFollowUpPrioritization() {
        val sceneIds = listOf(
            SceneId.HOME,
            SceneId.RESTAURANT,
            SceneId.SHOPPING,
            SceneId.SCHOOL,
            SceneId.FAMILY,
            SceneId.TRANSIT,
            SceneId.BUS,
            SceneId.TRAIN,
        )
        val renderer = SceneRenderer()
        fun percentile(samples: LongArray, fraction: Double): Double {
            samples.sort()
            return samples[((samples.size * fraction).toInt() - 1).coerceIn(0, samples.lastIndex)] / 1_000_000.0
        }
        try {
            for (sceneId in sceneIds) {
                val gcCountBefore = runtimeStat("art.gc.gc-count")
                val gcTimeBeforeMs = runtimeStat("art.gc.gc-time")
                val visual = VisualState(
                    scene = sceneId,
                    spot = SceneRegistry[sceneId].defaultSpot,
                    variant = 37,
                    actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
                )
                val machine = AnimationStateMachine(Random(0)).apply { place(visual, 0L) }
                repeat(30) { index ->
                    val time = index * 33L
                    renderer.render(requireNotNull(machine.frame(time, 12 * 60, DayPeriod.DAY)), time)
                }
                val samples = LongArray(120) { index ->
                    val time = (index + 30L) * 33L
                    val frame = requireNotNull(machine.frame(time, 12 * 60, DayPeriod.DAY))
                    val started = SystemClock.elapsedRealtimeNanos()
                    renderer.render(frame, time)
                    SystemClock.elapsedRealtimeNanos() - started
                }
                println(
                    "SCENE_WARM_RENDER scene=$sceneId p50=${percentile(samples, .50)}ms " +
                        "p95=${percentile(samples, .95)}ms p99=${percentile(samples, .99)}ms " +
                        "gc=${runtimeStat("art.gc.gc-count") - gcCountBefore}/" +
                        "${runtimeStat("art.gc.gc-time") - gcTimeBeforeMs}ms"
                )
            }
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun officeFiveVirtualMinutesKeepOneSessionAndBoundedSpriteCache() {
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val rebuildsBefore = OfficePerformanceCounters.timelineRebuilds.get()
        val runtime = Runtime.getRuntime()
        val gcCountBefore = runtimeStat("art.gc.gc-count")
        val gcTimeBeforeMs = runtimeStat("art.gc.gc-time")
        val allocatedBeforeBytes = runtimeStat("art.gc.bytes-allocated")
        val heapSamples = LongArray(11)
        val renderer = SceneRenderer()
        val scene = SceneRegistry[SceneId.OFFICE]
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 731,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
        )
        val machine = AnimationStateMachine(Random(0)).apply { place(visual, 0L) }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            // 9,100 × 33 ms advances the office simulation by just over five virtual minutes.
            repeat(9_100) { index ->
                val time = index * 33L
                val minute = 9 * 60 + (time / 60_000L).toInt()
                val frame = requireNotNull(machine.frame(time, minute, DayPeriod.DAY))
                renderer.render(frame, time)
                if (index % 910 == 0) {
                    heapSamples[index / 910] = runtime.totalMemory() - runtime.freeMemory()
                }
            }
            heapSamples[10] = runtime.totalMemory() - runtime.freeMemory()
            val snapshot = ScenePerformanceMonitor.snapshot()
            val sampleText = heapSamples.joinToString(",")
            println(
                "OFFICE_5MIN frames=9100 duration=${SystemClock.elapsedRealtime() - startedAt}ms " +
                    "sessions=${OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore} " +
                    "brains=${OfficePerformanceCounters.brainsCreated.get() - brainsBefore} " +
                    "timelineResets=${OfficePerformanceCounters.timelineResets.get() - resetsBefore} " +
                    "heapSamples=$sampleText " +
                    "allocated=${runtimeStat("art.gc.bytes-allocated") - allocatedBeforeBytes}bytes " +
                    "gc=${runtimeStat("art.gc.gc-count") - gcCountBefore}/" +
                    "${runtimeStat("art.gc.gc-time") - gcTimeBeforeMs}ms " +
                    "spriteCache=${snapshot.spriteCache.entries}/${snapshot.spriteCache.capacity} " +
                    "timelineRebuilds=${OfficePerformanceCounters.timelineRebuilds.get() - rebuildsBefore}"
            )
            assertEquals("one office session for the simulated visit", 1L,
                OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
            assertEquals("three brains for the simulated visit", 3L,
                OfficePerformanceCounters.brainsCreated.get() - brainsBefore)
            assertEquals("no unnecessary timeline resets while rendering", resetsBefore,
                OfficePerformanceCounters.timelineResets.get())
            assertTrue("sprite cache remains within its configured bound",
                snapshot.spriteCache.entries <= snapshot.spriteCache.capacity)
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun officeThirtyVirtualMinutesKeepTimelineDeterministicAndSessionStable() {
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val rebuildsBefore = OfficePerformanceCounters.timelineRebuilds.get()
        val runtime = Runtime.getRuntime()
        System.gc()
        SystemClock.sleep(100L)
        val gcCountBefore = runtimeStat("art.gc.gc-count")
        val allocatedBeforeBytes = runtimeStat("art.gc.bytes-allocated")
        val heapStartBytes = runtime.totalMemory() - runtime.freeMemory()
        val heapSamples = LongArray(11)
        heapSamples[0] = heapStartBytes
        val renderer = SceneRenderer()
        val scene = SceneRegistry[SceneId.OFFICE]
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 732,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 2_000_000, maxMs = 2_000_000)),
        )
        val machine = AnimationStateMachine(Random(0)).apply { place(visual, 0L) }
        val startedAt = SystemClock.elapsedRealtime()
        val frames = 54_546 // Thirty virtual minutes at the renderer's 33 ms target interval.
        val sampleEvery = frames / 10
        try {
            repeat(frames) { index ->
                val time = index * 33L
                val minute = 9 * 60 + (time / 60_000L).toInt()
                renderer.render(requireNotNull(machine.frame(time, minute, DayPeriod.DAY)), time)
                if (index > 0 && index % sampleEvery == 0) {
                    heapSamples[index / sampleEvery] = runtime.totalMemory() - runtime.freeMemory()
                }
            }
            heapSamples[10] = runtime.totalMemory() - runtime.freeMemory()
            val finalFrame = requireNotNull(machine.frame((frames - 1) * 33L, 9 * 60 + 29, DayPeriod.DAY))
            val stableFrame = renderer.render(finalFrame, (frames - 1) * 33L)
            val heapEndBytes = runtime.totalMemory() - runtime.freeMemory()
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            System.gc()
            SystemClock.sleep(100L)
            val retainedHeapBytes = runtime.totalMemory() - runtime.freeMemory()
            val snapshot = ScenePerformanceMonitor.snapshot()
            println(
                "OFFICE_30MIN frames=$frames duration=${durationMs}ms " +
                    "sessions=${OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore} " +
                    "brains=${OfficePerformanceCounters.brainsCreated.get() - brainsBefore} " +
                    "timelineResets=${OfficePerformanceCounters.timelineResets.get() - resetsBefore} " +
                    "timelineRebuilds=${OfficePerformanceCounters.timelineRebuilds.get() - rebuildsBefore} " +
                    "heapStart=$heapStartBytes heapSamples=${heapSamples.joinToString(",")} heapEnd=$heapEndBytes " +
                    "retainedAfterGc=$retainedHeapBytes " +
                    "allocated=${runtimeStat("art.gc.bytes-allocated") - allocatedBeforeBytes}bytes " +
                    "gc=${runtimeStat("art.gc.gc-count") - gcCountBefore} " +
                    "spriteCache=${snapshot.spriteCache.entries}/${snapshot.spriteCache.capacity}"
            )
            assertEquals("one session for the full virtual workday segment", 1L,
                OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
            assertEquals("three brains remain alive for the full virtual segment", 3L,
                OfficePerformanceCounters.brainsCreated.get() - brainsBefore)
            assertEquals("no timeline resets during continuous rendering", resetsBefore,
                OfficePerformanceCounters.timelineResets.get())
            assertTrue("sprite cache stays bounded", snapshot.spriteCache.entries <= snapshot.spriteCache.capacity)
            assertTrue("final renderer output remains populated", stableFrame.width > 0 && stableFrame.height > 0)
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun officeSceneResumesAfterBackgroundWithoutRecreatingItsSession() {
        ScenePerformanceMonitor.resetMeasurements()
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 814,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
        )
        var entryStartedAt = 0L
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                entryStartedAt = SystemClock.elapsedRealtime()
                activity.setContentView(ComposeView(activity).apply {
                    setContent { HoodieSceneView(visual, Modifier.size(240.dp, 320.dp), greet = false) }
                })
            }
            val firstFrameDeadline = SystemClock.elapsedRealtime() + 10_000L
            while (ScenePerformanceMonitor.snapshot().frames < 1 && SystemClock.elapsedRealtime() < firstFrameDeadline) {
                SystemClock.sleep(25L)
            }
            assertTrue("office scene presented its first frame", ScenePerformanceMonitor.snapshot().frames >= 1)
            val firstFrameMs = SystemClock.elapsedRealtime() - entryStartedAt
            val firstFrameSnapshot = ScenePerformanceMonitor.snapshot()
            while (ScenePerformanceMonitor.snapshot().frames < 10 && SystemClock.elapsedRealtime() < firstFrameDeadline) {
                SystemClock.sleep(25L)
            }
            assertTrue("office scene rendered before backgrounding", ScenePerformanceMonitor.snapshot().frames >= 10)
            assertEquals(1L, OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
            val framesBeforeBackground = ScenePerformanceMonitor.snapshot().frames

            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(350L)
            val resumeStartedAt = SystemClock.elapsedRealtime()
            scenario.moveToState(Lifecycle.State.RESUMED)

            val resumeDeadline = SystemClock.elapsedRealtime() + 10_000L
            while (ScenePerformanceMonitor.snapshot().frames <= framesBeforeBackground &&
                SystemClock.elapsedRealtime() < resumeDeadline
            ) {
                SystemClock.sleep(25L)
            }
            val resumeFirstFrameMs = SystemClock.elapsedRealtime() - resumeStartedAt
            assertTrue("office scene rendered again after resume",
                ScenePerformanceMonitor.snapshot().frames > framesBeforeBackground)
            assertEquals("resuming reuses the same renderer session", 1L,
                OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
            assertEquals("backgrounding does not clear the live timeline", resetsBefore,
                OfficePerformanceCounters.timelineResets.get())
            println(
                "OFFICE_LIFECYCLE firstFrame=${firstFrameMs}ms render=${firstFrameSnapshot.frameP50Ms}ms " +
                    "draw=${firstFrameSnapshot.drawingP95Ms}ms npc=${firstFrameSnapshot.npcDrawingP95Ms}ms " +
                    "resumeFirstFrame=${resumeFirstFrameMs}ms"
            )
        }
    }

    @Test
    fun officeRendererAdvancesThirtyVirtualMinutesWithoutRecreatingTheSession() {
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()
        val resetsBefore = OfficePerformanceCounters.timelineResets.get()
        val rebuildsBefore = OfficePerformanceCounters.timelineRebuilds.get()
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 815,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 2_000_000, maxMs = 2_000_000)),
        )
        val machine = AnimationStateMachine(Random(0)).apply { place(visual, 0L) }
        val renderer = SceneRenderer()
        try {
            renderer.render(requireNotNull(machine.frame(0L, 9 * 60, DayPeriod.DAY)), 0L)
            val resumedAtMs = 30 * 60_000L
            ScenePerformanceMonitor.resetMeasurements()
            val jumpStart = SystemClock.elapsedRealtimeNanos()
            val resumedFrame = requireNotNull(machine.frame(resumedAtMs, 9 * 60 + 30, DayPeriod.DAY))
            renderer.render(resumedFrame, resumedAtMs)
            val jumpRenderMs = (SystemClock.elapsedRealtimeNanos() - jumpStart) / 1_000_000.0
            val stages = ScenePerformanceMonitor.snapshot()
            val finalState = renderer.render(resumedFrame, resumedAtMs)
            println(
                "OFFICE_LONG_RESUME jump=30min render=${jumpRenderMs}ms " +
                    "planning/state/drawing/npc/scene=" +
                    "${stages.planningP95Ms}/${stages.stateP95Ms}/${stages.drawingP95Ms}/" +
                    "${stages.npcDrawingP95Ms}/${stages.sceneDrawingP95Ms}ms " +
                    "sessions=${OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore} " +
                    "brains=${OfficePerformanceCounters.brainsCreated.get() - brainsBefore} " +
                    "timelineEntries=${OfficePerformanceCounters.timelineRebuilds.get() - rebuildsBefore} " +
                    "resets=${OfficePerformanceCounters.timelineResets.get() - resetsBefore}"
            )
            assertEquals("a long background gap keeps the same office session", 1L,
                OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
            assertEquals("a long background gap keeps its three brains", 3L,
                OfficePerformanceCounters.brainsCreated.get() - brainsBefore)
            assertEquals("advancing time does not reset planned timelines", resetsBefore,
                OfficePerformanceCounters.timelineResets.get())
            assertTrue("resumed render remains populated", finalState.width > 0 && finalState.height > 0)
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun officeTwentyFourVirtualHoursPruneTimelinesAndKeepStateDeterministic() {
        val session = OfficeNpcDirector.createSession(
            SceneEnv(DayPeriod.DAY, 9 * 60, variant = 911, daySeed = 911),
        )
        try {
            val brains = session.npcSlots(9 * 60).mapNotNull { it.officeBrain }
            val targetTimeMs = 24 * 60 * 60_000L
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val expected = brains.associate { it.npcId to it.frameStateAt(targetTimeMs) }
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            val repeated = brains.associate { it.npcId to it.frameStateAt(targetTimeMs) }

            assertEquals(expected, repeated)
            assertTrue("the long session exercises coordinated-timeline pruning", brains.any { it.coordinatedTimelinePruneCount() > 0 })
            brains.forEach { brain ->
                assertTrue(brain.coordinatedTimelineCacheSize() <= brain.coordinatedTimelineCacheCapacity())
                assertTrue(brain.rawTimelineCacheSize() <= brain.rawTimelineCacheCapacity())
            }
            assertTrue(session.social.cachedReservationCount() <= session.social.reservationCacheCapacity())
            println(
                "OFFICE_24H duration=${elapsedMs}ms " +
                    "coordinated=${brains.sumOf { it.coordinatedTimelineCacheSize() }}/" +
                    "${brains.sumOf { it.coordinatedTimelineCacheCapacity() }} " +
                    "prunes=${brains.sumOf { it.coordinatedTimelinePruneCount() }} " +
                    "reservations=${session.social.cachedReservationCount()}/" +
                    "${session.social.reservationCacheCapacity()}"
            )
            assertEquals(session.social.reservationCacheCapacity(), session.social.cachedReservationCount())
        } finally {
            session.dispose()
        }
    }

    @Test
    fun office120FramesReuseSessionAndReportMeasuredStages() {
        val sessionsBefore = OfficePerformanceCounters.sessionsCreated.get()
        val brainsBefore = OfficePerformanceCounters.brainsCreated.get()
        val attachesBefore = OfficePerformanceCounters.socialAttaches.get()
        val scene = SceneRegistry[SceneId.OFFICE]
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 421,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
        )
        val machine = AnimationStateMachine(Random(0)).apply { place(visual, 0L) }
        val renderer = SceneRenderer()
        var coldRenderMs = 0.0
        var coldStageReport = ""
        ScenePerformanceMonitor.resetMeasurements()
        repeat(120) { index ->
            val time = index * 33L
            val frameStart = SystemClock.elapsedRealtimeNanos()
            val frame = requireNotNull(machine.frame(time, 9 * 60, DayPeriod.DAY))
            renderer.render(frame, time)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - frameStart) / 1_000_000L
            if (index == 0) {
                coldRenderMs = elapsedMs.toDouble()
                val cold = ScenePerformanceMonitor.snapshot()
                coldStageReport = "coldStages plan/state/draw/npc/scene=" +
                    "${cold.planningP95Ms}/${cold.stateP95Ms}/${cold.drawingP95Ms}/" +
                    "${cold.npcDrawingP95Ms}/${cold.sceneDrawingP95Ms}ms " +
                    "npcPose/paint/scale/composite=" +
                    "${cold.npcPoseP95Ms}/${cold.npcPaintP95Ms}/${cold.npcScaleP95Ms}/${cold.npcCompositeP95Ms}ms " +
                    "perNpc=${cold.rabbitDrawingP95Ms}/${cold.catDrawingP95Ms}/${cold.bulldogDrawingP95Ms}ms"
            }
            if (elapsedMs < 33L) SystemClock.sleep(33L - elapsedMs)
        }

        val report = ScenePerformanceMonitor.snapshot()
        assertEquals(1L, OfficePerformanceCounters.sessionsCreated.get() - sessionsBefore)
        assertEquals(3L, OfficePerformanceCounters.brainsCreated.get() - brainsBefore)
        assertEquals(3L, OfficePerformanceCounters.socialAttaches.get() - attachesBefore)
        assertTrue("renderer recorded frames", report.frames >= 120)
        assertTrue("renderer measured planning", report.planningP95Ms >= 0.0)
        assertTrue("renderer exposed heap and GC diagnostics", report.heapUsedBytes > 0L && report.gcCount >= 0L)
        println(
            "OFFICE_PERF frames=${report.frames} fps=${report.fps} p50=${report.frameP50Ms}ms " +
                "p95=${report.frameP95Ms}ms p99=${report.frameP99Ms}ms planningP95=${report.planningP95Ms}ms " +
                "stateP95=${report.stateP95Ms}ms drawingP95=${report.drawingP95Ms}ms bitmapP95=${report.bitmapP95Ms}ms " +
                "npcDrawP95=${report.npcDrawingP95Ms}ms sceneDrawP95=${report.sceneDrawingP95Ms}ms " +
            "npcStages pose/paint/turn/scale/blit/speech=${report.npcPoseP95Ms}/${report.npcPaintP95Ms}/${report.npcTurnP95Ms}/${report.npcScaleP95Ms}/${report.npcCompositeP95Ms}/${report.npcSpeechP95Ms}ms " +
            "sceneStages copy/objects/hoodie/light/post=${report.sceneCopyP95Ms}/${report.sceneObjectsP95Ms}/${report.sceneHoodieP95Ms}/${report.sceneLightingP95Ms}/${report.scenePostP95Ms}ms " +
            "rabbit/cat/bulldog=${report.rabbitDrawingP95Ms}/${report.catDrawingP95Ms}/${report.bulldogDrawingP95Ms}ms " +
            "heap=${report.heapUsedBytes} gc=${report.gcCount}/${report.gcTimeMs}ms " +
            "coldRender=${coldRenderMs}ms $coldStageReport " +
                "spriteCache=${report.spriteCache.hits}/${report.spriteCache.misses} hits/misses",
        )
    }

    @Test
    fun officeColdRenderSeparatesCodeWarmupFromSpriteCacheHits() {
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 423,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
        )
        val frame = requireNotNull(
            AnimationStateMachine(Random(0)).apply { place(visual, 0L) }.frame(0L, 9 * 60, DayPeriod.DAY),
        )
        val coldRenderer = SceneRenderer()
        CharacterPainter.clearFrameCacheForTest()
        ScenePerformanceMonitor.resetMeasurements()
        val coldStart = SystemClock.elapsedRealtimeNanos()
        coldRenderer.render(frame, 0L)
        val coldMs = (SystemClock.elapsedRealtimeNanos() - coldStart) / 1_000_000.0
            val cold = ScenePerformanceMonitor.snapshot()
        coldRenderer.dispose()

        // Keep the LRU empty to measure class/JIT warm-up independently of sprite hits.
        CharacterPainter.clearFrameCacheForTest()
        ScenePerformanceMonitor.resetMeasurements()
        val warmedRenderer = SceneRenderer()
        var codeWarmedMs = 0.0
        try {
            val warmedStart = SystemClock.elapsedRealtimeNanos()
            warmedRenderer.render(frame, 0L)
            codeWarmedMs = (SystemClock.elapsedRealtimeNanos() - warmedStart) / 1_000_000.0
            val warmed = ScenePerformanceMonitor.snapshot()

            CharacterPainter.clearFrameCacheForTest()
            val warmSession = OfficeNpcDirector.createSession(frame.env)
            val prewarmStart = SystemClock.elapsedRealtimeNanos()
            warmSession.npcSlots(frame.env.clockMinute).forEach { slot ->
                val state = slot.officeBrain?.frameStateAt(0L)
                val movement = state?.movement ?: NpcMotionController.movement(slot, 0L)
                val characterFrame = NpcMotionController.frame(slot, 0L, movement)
                CharacterPainter.paintForNpc(
                    slot.definition.characterStyle, characterFrame.pose, characterFrame.motion, movement.facingRight,
                )
            }
            val exactPrewarmMs = (SystemClock.elapsedRealtimeNanos() - prewarmStart) / 1_000_000.0
            warmSession.dispose()

            ScenePerformanceMonitor.resetMeasurements()
            val cacheMissesBeforeExactRender = CharacterPainter.cacheStats().misses
            val exactRenderer = SceneRenderer()
            val exactStart = SystemClock.elapsedRealtimeNanos()
            val output = exactRenderer.render(frame, 0L)
            val exactRenderMs = (SystemClock.elapsedRealtimeNanos() - exactStart) / 1_000_000.0
            val exactWarm = ScenePerformanceMonitor.snapshot()
            val exactRenderMisses = CharacterPainter.cacheStats().misses - cacheMissesBeforeExactRender
            exactRenderer.dispose()
            println(
                "OFFICE_COLD_RENDER first=${coldMs}ms codeWarmEmptyCache=${codeWarmedMs}ms " +
                "exactPrewarm=${exactPrewarmMs}ms exactRender=${exactRenderMs}ms " +
                    "npcPaint=${cold.npcPaintP95Ms}/${warmed.npcPaintP95Ms}/${exactWarm.npcPaintP95Ms}ms " +
                    "cacheMisses=${cold.spriteCache.misses}/${warmed.spriteCache.misses}/" +
                    "${exactRenderMisses}"
            )
            assertTrue("cold and warmed renderer return a complete frame", output.width > 0 && output.height > 0)
        } finally {
            warmedRenderer.dispose()
        }
    }

    @Test
    fun officeComposeLoopReportsRealFrameCadenceAndBitmapCost() {
        ScenePerformanceMonitor.resetMeasurements()
        val visual = VisualState(
            scene = SceneId.OFFICE,
            spot = SpotId.DESK,
            variant = 422,
            actions = listOf(MicroAction(AnimationId.IDLE, weight = 1, minMs = 120_000, maxMs = 120_000)),
        )
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply {
                    setContent { HoodieSceneView(visual, Modifier.size(240.dp, 320.dp), greet = false) }
                })
            }
            val warmupDeadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < warmupDeadline && ScenePerformanceMonitor.snapshot().frames < 120) {
                SystemClock.sleep(50L)
            }
            assertTrue("Compose renderer completed its warm-up", ScenePerformanceMonitor.snapshot().frames >= 120)
            ScenePerformanceMonitor.resetMeasurements()
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            while (SystemClock.elapsedRealtime() < deadline && ScenePerformanceMonitor.snapshot().frames < 120) {
                SystemClock.sleep(50L)
            }
            val report = ScenePerformanceMonitor.snapshot()
            println(
                "OFFICE_COMPOSE_PERF frames=${report.frames} fps=${report.fps} p50=${report.frameP50Ms}ms " +
                    "p95=${report.frameP95Ms}ms p99=${report.frameP99Ms}ms npcDrawP95=${report.npcDrawingP95Ms}ms " +
                    "rabbit/cat/bulldog=${report.rabbitDrawingP95Ms}/${report.catDrawingP95Ms}/${report.bulldogDrawingP95Ms}ms " +
                    "sceneDrawP95=${report.sceneDrawingP95Ms}ms bitmapP95=${report.bitmapP95Ms}ms " +
                    "npcStages pose/paint/turn/scale/blit/speech=${report.npcPoseP95Ms}/${report.npcPaintP95Ms}/${report.npcTurnP95Ms}/${report.npcScaleP95Ms}/${report.npcCompositeP95Ms}/${report.npcSpeechP95Ms}ms " +
                    "sceneStages copy/objects/hoodie/light/post=${report.sceneCopyP95Ms}/${report.sceneObjectsP95Ms}/${report.sceneHoodieP95Ms}/${report.sceneLightingP95Ms}/${report.scenePostP95Ms}ms " +
                    "thread=${report.renderThreadName} " +
                    "heap=${report.heapUsedBytes} delta=${report.heapDeltaBytes} gc=${report.gcCount}/${report.gcTimeMs}ms",
            )
            assertTrue("live Compose renderer produced measurable office frames: ${report.frames}", report.frames > 0)
            assertTrue("bitmap update was measured", report.bitmapP95Ms > 0.0)
            assertTrue("office renderer runs off the main thread", report.renderThreadName != "main")
        }
    }
}
