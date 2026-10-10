package com.hoodie.app.engine.mobility

import com.hoodie.app.core.mobility.DetectedMovement
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class TransportFeatures(val durationMs: Long, val vehicle: Boolean, val meanSpeedKmh: Float?, val maxSpeedKmh: Float?,
    val speedVariation: Float, val stopCount: Int, val stoppedMs: Long, val meanStopMs: Long, val regularStops: Boolean)

/** Keeps only aggregates. No coordinates or sequence of positions or speeds is stored. */
@Singleton
class TransportFeatureBuilder @Inject constructor() {
    private var sessionId: Long? = null
    private var startedAt = 0L
    private var lastAt = 0L
    private var speedCount = 0
    private var speedSum = 0.0
    private var speedSquareSum = 0.0
    private var speedMax = 0f
    private var stopStart: Long? = null
    private var stopCount = 0
    private var stoppedMs = 0L
    private var stopSquareSum = 0.0
    private var vehicle = false

    @Synchronized fun begin(id: Long, at: Long, newVehicleSegment: Boolean = false) {
        if (sessionId == id && !newVehicleSegment) return
        sessionId = id; startedAt = at; lastAt = at; speedCount = 0; speedSum = 0.0; speedSquareSum = 0.0; speedMax = 0f
        stopStart = null; stopCount = 0; stoppedMs = 0; stopSquareSum = 0.0; vehicle = false
    }
    @Synchronized fun movement(activity: DetectedMovement, at: Long) {
        if (sessionId == null || at < lastAt) return
        lastAt = at
        if (activity == DetectedMovement.IN_VEHICLE) vehicle = true
        if (activity == DetectedMovement.STILL) { if (stopStart == null) stopStart = at }
        else if (activity.isMoving) stopStart?.let {
            val duration = at - it
            if (duration >= 10_000) { stopCount++; stoppedMs += duration; stopSquareSum += duration.toDouble() * duration }
            stopStart = null
        }
    }
    @Synchronized fun speed(metersPerSecond: Float, at: Long) {
        if (sessionId == null || at < startedAt || !metersPerSecond.isFinite() || metersPerSecond < 0 || metersPerSecond > 100) return
        addKmh(metersPerSecond * 3.6f)
    }
    @Synchronized fun speedKmh(kmh: Float, at: Long) {
        if (sessionId == null || at < startedAt || !kmh.isFinite() || kmh !in 0f..360f) return
        addKmh(kmh)
    }
    @Synchronized fun restorePersisted(id: Long, at: Long, count: Int, meanKmh: Float?, maxKmh: Float?, variation: Float) {
        if (sessionId == id && speedCount > 0) return
        begin(id, at)
        val mean = meanKmh?.takeIf { it.isFinite() && it >= 0f } ?: return
        val n = count.coerceAtLeast(0)
        speedCount = n; speedSum = mean.toDouble() * n; speedMax = maxKmh?.takeIf { it.isFinite() } ?: mean
        speedSquareSum = (mean * mean * (1f + variation.coerceAtLeast(0f) * variation.coerceAtLeast(0f))).toDouble() * n
    }
    private fun addKmh(kmh: Float) {
        speedCount++; speedSum += kmh; speedSquareSum += kmh.toDouble() * kmh; speedMax = maxOf(speedMax, kmh)
    }
    @Synchronized fun build(now: Long): TransportFeatures {
        val mean = if (speedCount > 0) speedSum / speedCount else null
        val variation = if (mean != null && mean > 0) sqrt((speedSquareSum / speedCount - mean * mean).coerceAtLeast(0.0)) / mean else 0.0
        val stopMean = if (stopCount > 0) stoppedMs / stopCount else 0
        val stopDeviation = if (stopCount > 0) sqrt((stopSquareSum / stopCount - stopMean.toDouble() * stopMean).coerceAtLeast(0.0)) else 0.0
        return TransportFeatures((now - startedAt).coerceAtLeast(0), vehicle, mean?.toFloat(), speedMax.takeIf { speedCount > 0 }, variation.toFloat(), stopCount,
            stoppedMs + (stopStart?.let { (now - it).coerceAtLeast(0) } ?: 0), stopMean, stopCount >= 3 && stopMean > 0 && stopDeviation / stopMean < .30)
    }
}
