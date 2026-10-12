package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.location.LocationSource
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.SpeedSampleReservation
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.MobilityRepository
import com.hoodie.app.worker.MobilityScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Performs one reserved point speed lookup without holding MobilityEngine's mutex. */
@Singleton
class TransportSpeedSampler @Inject constructor(
    private val repository: MobilityRepository,
    private val location: LocationSource,
    private val features: TransportFeatureBuilder,
    private val settings: SettingsRepository,
    private val scheduler: MobilityScheduler,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun request(reservation: SpeedSampleReservation) {
        synchronized(lock) {
            if (jobs.containsKey(reservation.sessionId)) return
            jobs[reservation.sessionId] = scope.launch {
                try {
                    if (!settings.current().mobility.detectionEnabled) return@launch
                    val sample = location.currentSpeed() ?: run {
                        log.log(DebugEventLogger.Category.MOBILITY, "Amostra sem leitura de velocidade")
                        return@launch
                    }
                    if (!HoodieConfig.TRANSPORT_CLASSIFIER_V2) return@launch
                    val applied = repository.applySpeedSample(reservation, sample, clock.nowMillis(), TransportSpeedSamplingPolicy.MAX_AGE_MS)
                    if (applied == null) {
                        log.log(DebugEventLogger.Category.MOBILITY, "Amostra descartada: sessão/reserva ficou obsoleta")
                        return@launch
                    }
                    features.beginSession(applied.id, applied.startedAt)
                    features.restoreSpeedAggregates(applied.id, applied.speedSampleCount, applied.meanSpeedKmh, applied.maxSpeedKmh, applied.speedVariation)
                    if (applied.currentMode.isVehicle) features.recordMovement(DetectedMovement.IN_VEHICLE, applied.lastVehicleAt ?: applied.startedAt)
                    log.log(DebugEventLogger.Category.MOBILITY, "Amostra aplicada ${applied.speedSampleCount}/${TransportSpeedSamplingPolicy.MAX_ATTEMPTS}")
                    scheduler.scheduleMobilityCheck(0L)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.log(DebugEventLogger.Category.MOBILITY, "Amostra de velocidade falhou (${error.javaClass.simpleName})")
                } finally {
                    synchronized(lock) { jobs.remove(reservation.sessionId) }
                }
            }
        }
    }

    fun cancelSession(sessionId: Long) {
        synchronized(lock) { jobs.remove(sessionId)?.cancel() }
    }
}
