package com.hoodie.app.pixel.performance

import android.os.Debug
import com.hoodie.app.BuildConfig
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.pixel.scene.SceneId
import kotlin.math.ceil

/** Bounded, in-memory diagnostic data. No frame logging, file IO, database or worker service.
 * Rendering and presentation are separate: only bitmap publication establishes paced FPS. */
object SceneDiagnostics {
    private const val CAPACITY = 180
    @Volatile private var labUsers = 0
    val enabled: Boolean get() = BuildConfig.DEBUG && (HoodieConfig.HOODIE_SCENE_PERF_DIAGNOSTICS || labUsers > 0)

    @Synchronized fun acquire(): AutoCloseable {
        labUsers++
        var closed = false
        return AutoCloseable { synchronized(this) { if (!closed) { labUsers--; closed = true } } }
    }

    private class Samples {
        val stages = Array(8) { LongArray(CAPACITY) }
        val bitmap = LongArray(CAPACITY)
        val intervals = LongArray(CAPACITY)
        var index = 0
        var count = 0
        var presentations = 0
        var lastPresentation = 0L
        var active = 0
        var visible = 0
        var sessions = 0L
        var rebuilds = 0L
        var snapshots = 0L
        var thread = ""
        var heapBaseline = -1L
        var allocatedBaseline: Long? = null
        var gcBaseline: Long? = null
        var gcTimeBaseline: Long? = null
    }
    private val scenes = arrayOfNulls<Samples>(SceneId.entries.size)
    private fun samples(scene: SceneId) = scenes[scene.ordinal] ?: Samples().also { scenes[scene.ordinal] = it }

    @Synchronized fun reset() { scenes.fill(null) }

    fun sessionCreated(scene: SceneId) { if (enabled) synchronized(this) { samples(scene).sessions++ } }
    fun timelineRebuilt(scene: SceneId) { if (enabled) synchronized(this) { samples(scene).rebuilds++ } }
    fun snapshotBuilt(scene: SceneId) { if (enabled) synchronized(this) { samples(scene).snapshots++ } }

    @Synchronized fun render(scene: SceneId, total: Long, planning: Long, state: Long, npcs: Long,
        objects: Long, composition: Long, lighting: Long, post: Long, active: Int, visible: Int) {
        if (!enabled) return
        val s = samples(scene)
        s.stages[0][s.index] = total.coerceAtLeast(0)
        s.stages[1][s.index] = planning.coerceAtLeast(0)
        s.stages[2][s.index] = state.coerceAtLeast(0)
        s.stages[3][s.index] = npcs.coerceAtLeast(0)
        s.stages[4][s.index] = objects.coerceAtLeast(0)
        s.stages[5][s.index] = composition.coerceAtLeast(0)
        s.stages[6][s.index] = lighting.coerceAtLeast(0)
        s.stages[7][s.index] = post.coerceAtLeast(0)
        s.active = active; s.visible = visible; s.thread = Thread.currentThread().name
        s.index = (s.index + 1) % CAPACITY; s.count = (s.count + 1).coerceAtMost(CAPACITY)
    }

    @Synchronized fun presented(scene: SceneId, duration: Long, at: Long = ScenePerformanceMonitor.nowNanos()) {
        if (!enabled) return
        val s = samples(scene)
        val i = s.presentations % CAPACITY
        s.bitmap[i] = duration.coerceAtLeast(0)
        s.intervals[i] = if (s.lastPresentation > 0) (at - s.lastPresentation).coerceAtLeast(0) else 0
        s.lastPresentation = at; s.presentations++
    }

    /** Breaks the FPS interval on navigation/stop rather than counting time offscreen as jank. */
    @Synchronized fun stopPresentation(scene: SceneId) { scenes[scene.ordinal]?.lastPresentation = 0 }

    @Synchronized fun snapshot(scene: SceneId): SceneDiagnosticSnapshot {
        val s = samples(scene)
        fun percentile(a: LongArray, count: Int, q: Double): Double {
            if (count == 0) return 0.0
            val sorted = a.copyOf(count).also { it.sort() }
            return sorted[(ceil(count * q).toInt() - 1).coerceAtLeast(0)] / 1_000_000.0
        }
        val intervalCount = s.presentations.coerceAtMost(CAPACITY)
        val nonzeroIntervals = s.intervals.take(intervalCount).filter { it > 0 }.toLongArray()
        val interval = percentile(nonzeroIntervals, nonzeroIntervals.size, .5)
        val heap = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val allocated = stat("art.gc.bytes-allocated")
        val gc = stat("art.gc.gc-count")
        val gcTime = stat("art.gc.gc-time")
        if (s.heapBaseline < 0) s.heapBaseline = heap
        if (s.allocatedBaseline == null) s.allocatedBaseline = allocated
        if (s.gcBaseline == null) s.gcBaseline = gc
        if (s.gcTimeBaseline == null) s.gcTimeBaseline = gcTime
        val totalInterval = nonzeroIntervals.sum()
        return SceneDiagnosticSnapshot(scene, s.count, s.presentations, if (totalInterval > 0) nonzeroIntervals.size * 1_000_000_000.0 / totalInterval else null,
            percentile(s.stages[0], s.count, .5), percentile(s.stages[0], s.count, .95), percentile(s.stages[0], s.count, .99),
            percentile(nonzeroIntervals, nonzeroIntervals.size, .95),
            s.stages.map { percentile(it, s.count, .95) }, percentile(s.bitmap, intervalCount, .95),
            s.active, s.visible, s.sessions, s.rebuilds, s.snapshots, heap, heap - s.heapBaseline,
            delta(allocated, s.allocatedBaseline), delta(gc, s.gcBaseline), delta(gcTime, s.gcTimeBaseline), s.thread,
            if (interval > 0) 1000.0 / interval else null)
    }

    @Synchronized fun snapshots(): List<SceneDiagnosticSnapshot> = SceneId.entries.filter { scenes[it.ordinal] != null }.map(::snapshot)
    private fun delta(value: Long?, baseline: Long?) = if (value == null || baseline == null) null else (value - baseline).coerceAtLeast(0)
    private fun stat(key: String): Long? = try { Debug.getRuntimeStat(key)?.toLongOrNull() } catch (_: Throwable) { null }
}

data class SceneDiagnosticSnapshot(
    val scene: SceneId, val frames: Int, val presentations: Int, val fps: Double?,
    val renderP50Ms: Double, val renderP95Ms: Double, val renderP99Ms: Double, val intervalP95Ms: Double,
    /** Total, planning, state, NPCs, objects, composition, lighting, post. */
    val stageP95Ms: List<Double>, val bitmapP95Ms: Double,
    val activeNpcs: Int, val visibleNpcs: Int, val sessionsCreated: Long, val timelineRebuilds: Long, val snapshotsBuilt: Long,
    val heapBytes: Long, val heapDeltaBytes: Long,
    /** Android ART process counters since first diagnostic snapshot; null means unavailable. */
    val processAllocatedBytes: Long?, val processGcCount: Long?, val processGcTimeMs: Long?, val renderThread: String,
    val medianFps: Double? = null,
)
