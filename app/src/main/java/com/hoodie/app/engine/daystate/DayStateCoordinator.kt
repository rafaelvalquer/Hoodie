package com.hoodie.app.engine.daystate

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.daystate.*
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.daycycle.DailyActivityWindowResolver
import com.hoodie.app.engine.daycycle.RealUserActivityBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Observes canonical sources without refreshing Android usage data or writing a second history. */
@Singleton
class DayStateCoordinator @Inject constructor(
    private val db: HoodieDatabase,
    private val settings: SettingsRepository,
    private val clock: ClockProvider,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    val snapshots: Flow<DayStateSnapshot?> = db.intelligenceDao().observeDayState().map { it?.toSnapshot() }.distinctUntilChanged()

    @Synchronized fun start(scope: CoroutineScope): Job? {
        if (!HoodieConfig.DAY_STATE_ENGINE) return null
        if (job?.isActive == true) return job
        return scope.launch {
            val ticks = flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(60_000) } }
            merge(
                db.invalidationTracker.createFlow("context_events", "mobility_sessions", "phone_app_sessions", "timeline_events", "hoodie_activities").map { Unit },
                settings.settings.map { Unit }, ticks,
            ).collect { reconcile() }
        }.also { job = it }
    }

    suspend fun reconcile(): DayStateSnapshot? = mutex.withLock {
        if (!HoodieConfig.DAY_STATE_ENGINE) return@withLock null
        val s = settings.current()
        if (!s.onboardingDone) return@withLock null
        val now = clock.nowMillis()
        val currentDate = clock.today()
        val minute = clock.now().hour * 60 + clock.now().minute
        // Before the wake window, keep the previous evening's inactivity evidence across midnight.
        val date = if (minute < s.sleep.wakeMinute) currentDate.minusDays(1) else currentDate
        val from = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val queryUntil = maxOf(to, now + 1)
        val contexts = db.contextEventDao().overlapping(from, queryUntil)
        val activities = db.hoodieActivityDao().overlapping(from, to)
        val timeline = db.timelineDao().range(from, now + 1)
        val mobility = if (s.mobility.detectionEnabled) db.mobilitySessionDao().overlapping(from, queryUntil) else emptyList()
        val phone = if (s.digital.analysisEnabled) (db.deviceUsageDao().sessions(date.toEpochDay()) +
            if (date != currentDate) db.deviceUsageDao().sessions(currentDate.toEpochDay()) else emptyList()).map { AppSession(it.packageName, it.startedAt, it.endedAt) } else emptyList()
        val window = DailyActivityWindowResolver.resolve(date, from, to, now, clock.zone(), s.sleep, contexts, activities, timeline, phone, mobility,
            analysisEnabled = s.digital.analysisEnabled, mobilityEnabled = s.mobility.detectionEnabled, evidenceEndAt = now + 1)
        val currentContext = contexts.lastOrNull { it.startedAt <= now && (it.endedAt == null || it.endedAt > now) }
        val trip = mobility.lastOrNull { it.startedAt <= now && it.endedAt == null && it.state in com.hoodie.app.data.repository.MobilityRepository.ACTIVE_STATES && it.stillSince == null }
        val real = RealUserActivityBuilder.build(from, now + 1, phone, mobility, contexts, timeline, s.digital.analysisEnabled, s.mobility.detectionEnabled)
        val bedtimeDistance = abs(minute - s.sleep.sleepMinute).let { minOf(it, 1440 - it) }
        val input = DayStateInput(now, window, currentContext?.type,
            ConfidenceScore(currentContext?.confidence?.coerceIn(0f, 1f) ?: 0f),
            ConfidenceScore(trip?.confidence?.coerceIn(0f, 1f) ?: 0f), trip?.confirmed == true,
            phoneActive = phone.any { it.endedAt <= now && now - it.endedAt <= 2 * 60_000L },
            lastActivityAt = real.lastOrNull()?.timestamp,
            nearBedtime = bedtimeDistance <= 90,
        )
        val dao = db.intelligenceDao()
        val previous = dao.dayState()?.toSnapshot()
        var resolved = DayStateEngine.resolve(previous, input)
        // A single observation may establish several stages, but every edge still passes the graph.
        repeat(3) { resolved = DayStateEngine.resolve(resolved, input) }
        if (resolved != previous) dao.saveDayState(resolved.toEntity())
        resolved
    }
}

fun DayStateEntity.toSnapshot() = DayStateSnapshot(DayState.valueOf(state), startedAt, ConfidenceScore(confidence), DayStateReason.valueOf(reason), provisional, updatedAt)
fun DayStateSnapshot.toEntity() = DayStateEntity(state = state.name, startedAt = startedAt, confidence = confidence.value, reason = reason.name, provisional = provisional, updatedAt = updatedAt)
