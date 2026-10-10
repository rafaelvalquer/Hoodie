package com.hoodie.app.engine.mobility

import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.mobility.MovementMode

/** Avoids a one-off walking transition replacing an established vehicle segment. */
object MobilityModeHysteresisPolicy {
    const val WALKING_CONFIRMATION_MS = 45_000L

    fun confirmVehicleToWalking(
        currentMode: MovementMode,
        observedMode: MovementMode,
        pendingMode: MovementMode?,
        pendingAt: Long?,
        now: Long,
    ): Boolean = currentMode.isVehicle && observedMode in setOf(MovementMode.WALKING, MovementMode.RUNNING) &&
        pendingMode == observedMode && pendingAt != null && now - pendingAt >= WALKING_CONFIRMATION_MS
}

/** The only operation allowed to request a point speed sample. */
object TransportSpeedSamplingPolicy {
    const val INTERVAL_MS = 60_000L
    const val MAX_AGE_MS = 120_000L
    const val MAX_ATTEMPTS = 5

    fun canAttempt(attempts: Int, lastAttemptAt: Long?, now: Long, permission: LocationPermissionState, foreground: Boolean): Boolean {
        if (attempts >= MAX_ATTEMPTS || !permission.canReadPosition) return false
        if (!foreground && permission != LocationPermissionState.BACKGROUND) return false
        return lastAttemptAt == null || now - lastAttemptAt >= INTERVAL_MS
    }
}
