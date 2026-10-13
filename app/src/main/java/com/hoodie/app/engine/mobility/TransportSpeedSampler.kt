package com.hoodie.app.engine.mobility

import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.location.ProcessForegroundState
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.worker.MobilityScheduler
import com.hoodie.app.worker.SpeedSampleScheduler
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import javax.inject.Inject
import javax.inject.Singleton

/** One suspended lookup. Its coroutine is owned and awaited by WorkManager. */
@Singleton
class TransportSpeedSampler @Inject constructor(
    private val repository: MobilityRepository,
    private val location: LocationSource,
    private val settings: SettingsRepository,
    private val scheduler: MobilityScheduler,
    private val speedScheduler: SpeedSampleScheduler,
    private val foreground: ProcessForegroundState,
    private val clock: ClockProvider,
) {
    private suspend fun allowed(): Boolean = com.hoodie.app.core.config.HoodieConfig.TRANSPORT_CLASSIFIER_V2 && settings.current().mobility.detectionEnabled &&
        location.permissionState().let { it.canReadPosition && (it == LocationPermissionState.BACKGROUND || foreground.isVisible) }
    fun enqueue(sessionId: Long, evidenceId: Long) = speedScheduler.enqueue(sessionId, evidenceId)
    fun cancelSession(sessionId: Long) = speedScheduler.cancelSession(sessionId)
    fun cancelAll() = speedScheduler.cancelSpeedSamples()
    fun cancelEvidence(evidenceId: Long) = speedScheduler.cancelEvidence(evidenceId)
    suspend fun sample(sessionId: Long, evidenceId: Long, workRequestId: String) {
        if (!allowed()) return
        val reservation = repository.reserveEvidenceSample(sessionId, evidenceId, workRequestId, clock.nowMillis()) ?: return
        try {
            val sample = withTimeoutOrNull(20_000L) { location.currentSpeed() } ?: return
            if (allowed() && repository.applyEvidenceSample(reservation, sample, clock.nowMillis())) scheduler.scheduleMobilityCheck(0)
        } finally { withContext(NonCancellable) { repository.releaseSpeedReservation(reservation) } }
    }
}
