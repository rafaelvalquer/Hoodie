package com.hoodie.app.engine.deviceusage

import android.os.SystemClock
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.UsageAccessChecker
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.DeviceUsageRepository
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.engine.performance.DiaryPerformanceMonitor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates explicit/worker refreshes; opening the Diary always reads persisted data only. */
@Singleton
class DeviceUsageRefreshCoordinator @Inject constructor(
    private val repository: DeviceUsageRepository,
    private val settings: SettingsRepository,
    private val access: UsageAccessChecker,
    private val clock: ClockProvider,
    private val performance: DiaryPerformanceMonitor? = null,
) {
    private val locks = ConcurrentHashMap<LocalDate, Mutex>()
    private val lastAutomaticRefresh = ConcurrentHashMap<LocalDate, Long>()
    private val requestSequence = AtomicLong()

    suspend fun refresh(date: LocalDate, automatic: Boolean = true): DailyPhoneInsights? {
        if (date.isAfter(clock.today())) return null
        if (!HoodieConfig.DIARY_LOAD_COORDINATOR_V2) return repository.refreshDay(date)

        val lock = locks.computeIfAbsent(date) { Mutex() }
        return lock.withLock {
            val digital = settings.current().digital
            if (!digital.analysisEnabled || !digital.saveHistory || !access.isGranted()) {
                return@withLock repository.loadDay(date)
            }

            val elapsed = SystemClock.elapsedRealtime()
            val last = lastAutomaticRefresh[date]
            val intervalMs = HoodieConfig.PHONE_INSIGHTS_REFRESH_HOURS * 60L * 60L * 1_000L
            if (automatic && last != null && elapsed - last in 0 until intervalMs) {
                return@withLock repository.loadDay(date)
            }

            val requestId = requestSequence.incrementAndGet()
            val started = SystemClock.elapsedRealtime()
            performance?.record(DiaryPerformanceMonitor.Event.DIGITAL_REFRESH_STARTED, date, requestId)
            try {
                repository.refreshDay(date).also {
                    if (automatic) lastAutomaticRefresh[date] = elapsed
                    performance?.record(
                        DiaryPerformanceMonitor.Event.DIGITAL_REFRESH_FINISHED,
                        date,
                        requestId,
                        SystemClock.elapsedRealtime() - started,
                    )
                }
            } catch (failure: Exception) {
                performance?.record(
                    DiaryPerformanceMonitor.Event.DIGITAL_REFRESH_FINISHED,
                    date,
                    requestId,
                    SystemClock.elapsedRealtime() - started,
                )
                throw failure
            }
        }
    }
}
