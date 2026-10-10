package com.hoodie.app.engine.mobility

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.model.QuestionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityModeHysteresisPolicyTest {
    @Test fun `isolated walking event does not replace vehicle mode`() {
        assertFalse(MobilityModeHysteresisPolicy.confirmVehicleToWalking(
            MovementMode.VEHICLE_UNKNOWN, MovementMode.WALKING, null, null, 100_000L,
        ))
    }

    @Test fun `walking needs a repeated observation after the hysteresis window`() {
        assertFalse(MobilityModeHysteresisPolicy.confirmVehicleToWalking(
            MovementMode.CAR, MovementMode.WALKING, MovementMode.WALKING, 10_000L, 54_999L,
        ))
        assertTrue(MobilityModeHysteresisPolicy.confirmVehicleToWalking(
            MovementMode.CAR, MovementMode.WALKING, MovementMode.WALKING, 10_000L, 55_000L,
        ))
    }

    @Test fun `vehicle observation supersedes pending walking`() {
        assertFalse(MobilityModeHysteresisPolicy.confirmVehicleToWalking(
            MovementMode.CAR, MovementMode.VEHICLE_UNKNOWN, MovementMode.WALKING, 10_000L, 100_000L,
        ))
    }

    @Test fun `speed sampling enforces permission interval and hard attempt cap`() {
        assertTrue(TransportSpeedSamplingPolicy.canAttempt(0, null, 90_000L, LocationPermissionState.BACKGROUND, false))
        assertFalse(TransportSpeedSamplingPolicy.canAttempt(0, null, 90_000L, LocationPermissionState.FOREGROUND, false))
        assertFalse(TransportSpeedSamplingPolicy.canAttempt(1, 50_000L, 90_000L, LocationPermissionState.BACKGROUND, false))
        assertTrue(TransportSpeedSamplingPolicy.canAttempt(1, 30_000L, 90_000L, LocationPermissionState.BACKGROUND, false))
        assertFalse(TransportSpeedSamplingPolicy.canAttempt(5, null, 90_000L, LocationPermissionState.BACKGROUND, false))
    }

    @Test fun `no mobility prompt is allowed while vehicle may be moving even in foreground`() {
        val verdict = MobilityConfirmationPolicy.evaluate(
            QuestionKind.SELECT_TRANSPORT_MODE, 0, vehicleMoving = true, appInForeground = true,
            now = 90_000L, zone = java.time.ZoneId.of("America/Sao_Paulo"), candidate = null, recent = emptyList(),
        )
        org.junit.Assert.assertEquals(MobilityConfirmationPolicy.Verdict.DEFER, verdict)
    }
}
