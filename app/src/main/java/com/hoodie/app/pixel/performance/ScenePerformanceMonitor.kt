package com.hoodie.app.pixel.performance

import android.os.SystemClock
import android.os.Trace
import android.os.Debug
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.npc.office.OfficePerformanceCounters
import kotlin.math.ceil

/** In-memory rolling diagnostics. It never writes a log or allocates on the frame path. */
object ScenePerformanceMonitor {
    private const val CAPACITY = 120
    private val renderNanos = LongArray(CAPACITY)
    private val frameIntervalsNanos = LongArray(CAPACITY)
    private val planningNanos = LongArray(CAPACITY)
    private val stateNanos = LongArray(CAPACITY)
    private val drawingNanos = LongArray(CAPACITY)
    private val npcDrawingNanos = LongArray(CAPACITY)
    private val sceneDrawingNanos = LongArray(CAPACITY)
    private val rabbitDrawNanos = LongArray(CAPACITY)
    private val catDrawNanos = LongArray(CAPACITY)
    private val bulldogDrawNanos = LongArray(CAPACITY)
    private val npcPoseNanos = LongArray(CAPACITY)
    private val npcPaintNanos = LongArray(CAPACITY)
    private val npcTurnNanos = LongArray(CAPACITY)
    private val npcScaleNanos = LongArray(CAPACITY)
    private val npcCompositeNanos = LongArray(CAPACITY)
    private val npcSpeechNanos = LongArray(CAPACITY)
    private val sceneCopyNanos = LongArray(CAPACITY)
    private val sceneObjectsNanos = LongArray(CAPACITY)
    private val sceneHoodieNanos = LongArray(CAPACITY)
    private val sceneLightingNanos = LongArray(CAPACITY)
    private val scenePostNanos = LongArray(CAPACITY)
    private val bitmapNanos = LongArray(CAPACITY)
    private var index = 0
    private var count = 0
    private var lastFrameStart = 0L
    private var heapBaselineBytes = -1L
    private var heapPeakBytes = 0L
    private var lastRenderThreadName = ""

    fun nowNanos(): Long = try {
        SystemClock.elapsedRealtimeNanos()
    } catch (_: Throwable) {
        // Local JVM renderer tests use the monotonic JVM clock; Android uses elapsedRealtimeNanos.
        System.nanoTime()
    }

    fun traceBegin(section: String) {
        try { Trace.beginSection(section) } catch (_: Throwable) { }
    }

    fun traceEnd() {
        try { Trace.endSection() } catch (_: Throwable) { }
    }

    @Synchronized
    fun beginOfficeFrame() {
        rabbitDrawNanos[index] = 0L
        catDrawNanos[index] = 0L
        bulldogDrawNanos[index] = 0L
        npcPoseNanos[index] = 0L
        npcPaintNanos[index] = 0L
        npcTurnNanos[index] = 0L
        npcScaleNanos[index] = 0L
        npcCompositeNanos[index] = 0L
        npcSpeechNanos[index] = 0L
        sceneCopyNanos[index] = 0L
        sceneObjectsNanos[index] = 0L
        sceneHoodieNanos[index] = 0L
        sceneLightingNanos[index] = 0L
        scenePostNanos[index] = 0L
    }

    @Synchronized
    internal fun recordNpcStages(pose: Long, paint: Long, turn: Long, scale: Long, composite: Long, speech: Long) {
        npcPoseNanos[index] += pose.coerceAtLeast(0L)
        npcPaintNanos[index] += paint.coerceAtLeast(0L)
        npcTurnNanos[index] += turn.coerceAtLeast(0L)
        npcScaleNanos[index] += scale.coerceAtLeast(0L)
        npcCompositeNanos[index] += composite.coerceAtLeast(0L)
        npcSpeechNanos[index] += speech.coerceAtLeast(0L)
    }

    @Synchronized
    fun recordNpcDrawing(npcId: String, durationNanos: Long) {
        when (npcId) {
            "rabbit_analyst" -> rabbitDrawNanos[index] += durationNanos.coerceAtLeast(0L)
            "cat_colleague" -> catDrawNanos[index] += durationNanos.coerceAtLeast(0L)
            "bulldog_exec" -> bulldogDrawNanos[index] += durationNanos.coerceAtLeast(0L)
        }
    }

    @Synchronized
    fun recordRender(
        startedAt: Long, planning: Long, stateUpdate: Long, drawing: Long, npcDrawing: Long, finishedAt: Long,
        sceneCopy: Long = 0L, sceneObjects: Long = 0L, sceneHoodie: Long = 0L,
        sceneLighting: Long = 0L, scenePost: Long = 0L,
    ) {
        lastRenderThreadName = Thread.currentThread().name
        val interval = if (lastFrameStart == 0L) 0L else (startedAt - lastFrameStart).coerceAtLeast(0L)
        lastFrameStart = startedAt
        renderNanos[index] = (finishedAt - startedAt).coerceAtLeast(0L)
        frameIntervalsNanos[index] = interval
        planningNanos[index] = planning.coerceAtLeast(0L)
        stateNanos[index] = stateUpdate.coerceAtLeast(0L)
        drawingNanos[index] = drawing.coerceAtLeast(0L)
        npcDrawingNanos[index] = npcDrawing.coerceAtLeast(0L)
        sceneDrawingNanos[index] = (drawing - npcDrawing).coerceAtLeast(0L)
        sceneCopyNanos[index] = sceneCopy.coerceAtLeast(0L)
        sceneObjectsNanos[index] = sceneObjects.coerceAtLeast(0L)
        sceneHoodieNanos[index] = sceneHoodie.coerceAtLeast(0L)
        sceneLightingNanos[index] = sceneLighting.coerceAtLeast(0L)
        scenePostNanos[index] = scenePost.coerceAtLeast(0L)
        index = (index + 1) % CAPACITY
        count = (count + 1).coerceAtMost(CAPACITY)
    }

    @Synchronized
    fun recordBitmapUpdate(durationNanos: Long) {
        if (count == 0) return
        bitmapNanos[(index - 1 + CAPACITY) % CAPACITY] = durationNanos.coerceAtLeast(0L)
    }

    @Synchronized
    internal fun resetMeasurements() {
        renderNanos.fill(0L)
        frameIntervalsNanos.fill(0L)
        planningNanos.fill(0L)
        stateNanos.fill(0L)
        drawingNanos.fill(0L)
        npcDrawingNanos.fill(0L)
        sceneDrawingNanos.fill(0L)
        rabbitDrawNanos.fill(0L)
        catDrawNanos.fill(0L)
        bulldogDrawNanos.fill(0L)
        npcPoseNanos.fill(0L)
        npcPaintNanos.fill(0L)
        npcTurnNanos.fill(0L)
        npcScaleNanos.fill(0L)
        npcCompositeNanos.fill(0L)
        npcSpeechNanos.fill(0L)
        sceneCopyNanos.fill(0L)
        sceneObjectsNanos.fill(0L)
        sceneHoodieNanos.fill(0L)
        sceneLightingNanos.fill(0L)
        scenePostNanos.fill(0L)
        bitmapNanos.fill(0L)
        index = 0
        count = 0
        lastFrameStart = 0L
        heapBaselineBytes = -1L
        heapPeakBytes = 0L
        lastRenderThreadName = ""
    }

    @Synchronized
    internal fun snapshot(): ScenePerformanceSnapshot {
        fun percentile(values: LongArray, percentile: Double): Long {
            if (count == 0) return 0L
            val sorted = LongArray(count) { values[it] }.also { it.sort() }
            return sorted[(ceil(percentile * count).toInt() - 1).coerceIn(0, count - 1)]
        }
        val interval = percentile(frameIntervalsNanos, .50)
        val heapUsedBytes = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()).coerceAtLeast(0L)
        if (heapBaselineBytes < 0L) heapBaselineBytes = heapUsedBytes
        heapPeakBytes = maxOf(heapPeakBytes, heapUsedBytes)
        return ScenePerformanceSnapshot(
            fps = if (interval > 0L) 1_000_000_000.0 / interval else 0.0,
            frameP50Ms = percentile(renderNanos, .50) / 1_000_000.0,
            frameP95Ms = percentile(renderNanos, .95) / 1_000_000.0,
            frameP99Ms = percentile(renderNanos, .99) / 1_000_000.0,
            planningP95Ms = percentile(planningNanos, .95) / 1_000_000.0,
            stateP95Ms = percentile(stateNanos, .95) / 1_000_000.0,
            drawingP95Ms = percentile(drawingNanos, .95) / 1_000_000.0,
            npcDrawingP95Ms = percentile(npcDrawingNanos, .95) / 1_000_000.0,
            sceneDrawingP95Ms = percentile(sceneDrawingNanos, .95) / 1_000_000.0,
            rabbitDrawingP95Ms = percentile(rabbitDrawNanos, .95) / 1_000_000.0,
            catDrawingP95Ms = percentile(catDrawNanos, .95) / 1_000_000.0,
            bulldogDrawingP95Ms = percentile(bulldogDrawNanos, .95) / 1_000_000.0,
            npcPoseP95Ms = percentile(npcPoseNanos, .95) / 1_000_000.0,
            npcPaintP95Ms = percentile(npcPaintNanos, .95) / 1_000_000.0,
            npcTurnP95Ms = percentile(npcTurnNanos, .95) / 1_000_000.0,
            npcScaleP95Ms = percentile(npcScaleNanos, .95) / 1_000_000.0,
            npcCompositeP95Ms = percentile(npcCompositeNanos, .95) / 1_000_000.0,
            npcSpeechP95Ms = percentile(npcSpeechNanos, .95) / 1_000_000.0,
            sceneCopyP95Ms = percentile(sceneCopyNanos, .95) / 1_000_000.0,
            sceneObjectsP95Ms = percentile(sceneObjectsNanos, .95) / 1_000_000.0,
            sceneHoodieP95Ms = percentile(sceneHoodieNanos, .95) / 1_000_000.0,
            sceneLightingP95Ms = percentile(sceneLightingNanos, .95) / 1_000_000.0,
            scenePostP95Ms = percentile(scenePostNanos, .95) / 1_000_000.0,
            bitmapP95Ms = percentile(bitmapNanos, .95) / 1_000_000.0,
            slowFrames = renderNanos.take(count).count { it > 33_000_000L },
            sessionsCreated = OfficePerformanceCounters.sessionsCreated.get(),
            brainsCreated = OfficePerformanceCounters.brainsCreated.get(),
            socialAttaches = OfficePerformanceCounters.socialAttaches.get(),
            timelineRebuilds = OfficePerformanceCounters.timelineRebuilds.get(),
            timelinePrunes = OfficePerformanceCounters.timelinePrunes.get(),
            timelineResets = OfficePerformanceCounters.timelineResets.get(),
            spriteCache = CharacterPainter.cacheStats(),
            heapUsedBytes = heapUsedBytes,
            heapDeltaBytes = heapUsedBytes - heapBaselineBytes,
            heapPeakBytes = heapPeakBytes,
            gcCount = runtimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L,
            gcTimeMs = runtimeStat("art.gc.gc-time")?.toLongOrNull() ?: 0L,
            renderThreadName = lastRenderThreadName,
            frames = count,
        )
    }

    private fun runtimeStat(name: String): String? = try { Debug.getRuntimeStat(name) } catch (_: Throwable) { null }
}

internal data class ScenePerformanceSnapshot(
    val fps: Double,
    val frameP50Ms: Double,
    val frameP95Ms: Double,
    val frameP99Ms: Double,
    val planningP95Ms: Double,
    val stateP95Ms: Double,
    val drawingP95Ms: Double,
    val npcDrawingP95Ms: Double,
    val sceneDrawingP95Ms: Double,
    val rabbitDrawingP95Ms: Double,
    val catDrawingP95Ms: Double,
    val bulldogDrawingP95Ms: Double,
    val npcPoseP95Ms: Double,
    val npcPaintP95Ms: Double,
    val npcTurnP95Ms: Double,
    val npcScaleP95Ms: Double,
    val npcCompositeP95Ms: Double,
    val npcSpeechP95Ms: Double,
    val sceneCopyP95Ms: Double,
    val sceneObjectsP95Ms: Double,
    val sceneHoodieP95Ms: Double,
    val sceneLightingP95Ms: Double,
    val scenePostP95Ms: Double,
    val bitmapP95Ms: Double,
    val slowFrames: Int,
    val sessionsCreated: Long,
    val brainsCreated: Long,
    val socialAttaches: Long,
    val timelineRebuilds: Long,
    val timelinePrunes: Long,
    val timelineResets: Long,
    val spriteCache: com.hoodie.app.pixel.character.CharacterFrameCacheStats,
    val heapUsedBytes: Long,
    val heapDeltaBytes: Long,
    val heapPeakBytes: Long,
    val gcCount: Long,
    val gcTimeMs: Long,
    val renderThreadName: String,
    val frames: Int,
)
