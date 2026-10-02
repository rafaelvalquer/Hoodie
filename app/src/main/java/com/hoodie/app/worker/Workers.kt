package com.hoodie.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.UsageAccessManager
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.atZone
import com.hoodie.app.data.repository.DeviceUsageRepository
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.core.database.DatabaseGate
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Reconciliação periódica (~15 min): fallback de rotina, avanço do Hoodie,
 * memórias por calendário, manutenção. Barato: não liga GPS.
 */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    // Lazy: com o banco indisponível o worker não instancia nada que dependa dele.
    private val contextEngineLazy: Lazy<ContextEngine>,
    private val hoodieLazy: Lazy<HoodieEngine>,
    private val memoryLazy: Lazy<MemoryEngine>,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val geofencesLazy: Lazy<GeofenceManager>,
    private val locationEventsLazy: Lazy<LocationEventDao>,
    private val gate: DatabaseGate,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.onboardingDone) return Result.success()
        if (!gate.isReady()) return Result.retry()
        val geofences = geofencesLazy.get()
        val locationEvents = locationEventsLazy.get()
        log.log(DebugEventLogger.Category.WORKER, "reconcile")
        contextEngineLazy.get().applyRoutineFallbackIfNeeded()
        val snapshot = hoodieLazy.get().resolve()
        memoryLazy.get().checkCalendar()
        maybeNotifyAutonomy(snapshot.started.map { it.activity to it.userContext })

        val now = clock.nowMillis()
        locationEvents.deleteOlderThan(now - HoodieConfig.LOCATION_EVENT_RETENTION_MS)
        // Geofences podem sumir (Play Services reiniciado, localização religada):
        // re-registra uma vez por dia e sempre que o último registro falhou.
        val last = geofences.lastResult.value
        if (now.atZone(clock.zone()).hour == DAILY_REREGISTER_HOUR || last == null || !last.ok) geofences.registerAll()
        return Result.success()
    }

    /** "🐱 Hoodie decidiu jogar um pouco." — no máximo uma vez por dia, à noite. */
    private suspend fun maybeNotifyAutonomy(started: List<Pair<HoodieActivity, UserContextType>>) {
        val now = clock.now()
        if (now.hour !in 18..21) return
        val s = settings.current()
        val today = now.toLocalDate().toEpochDay()
        if (s.lastAutonomyNotifyDay == today) return
        val text = started.firstOrNull { it.second == UserContextType.HOME }?.first?.let {
            when (it) {
                HoodieActivity.GAMING -> "🎮 ${s.catName} decidiu jogar um pouco."
                HoodieActivity.READING -> "📖 ${s.catName} pegou um livro."
                HoodieActivity.COOKING -> "🍳 ${s.catName} está cozinhando algo."
                HoodieActivity.WATCHING_TV -> "📺 ${s.catName} ligou a TV."
                else -> null
            }
        } ?: return
        notifier.event(text)
        settings.setAutonomyNotified(today)
    }

    companion object {
        const val NAME = "hoodie_reconcile"
        private const val DAILY_REREGISTER_HOUR = 4
    }
}

/** Checagens pontuais agendadas pelo ContextEngine (almoço provável / deslocamento longo). */
@HiltWorker
class CheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contextEngineLazy: Lazy<ContextEngine>,
    private val gate: DatabaseGate,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!gate.isReady()) return Result.retry()
        val contextEngine = contextEngineLazy.get()
        when (inputData.getString(KIND)) {
            LUNCH -> contextEngine.onLunchCheck(inputData.getLong(AT, 0), inputData.getLong(PLACE, 0))
            COMMUTE -> contextEngine.onCommuteCheck(inputData.getLong(EVENT, 0))
        }
        return Result.success()
    }

    companion object {
        const val KIND = "kind"
        const val AT = "at"
        const val PLACE = "place"
        const val EVENT = "event"
        const val LUNCH = "hoodie_lunch_check"
        const val COMMUTE = "hoodie_commute_check"
    }
}

/**
 * Diário Digital: recalcula hoje e ontem a cada poucas horas. Ontem garante o
 * "fim do dia" fechado mesmo que o app não seja aberto depois da meia-noite.
 * Não monitora nada em tempo real: só agrega os eventos que o Android já guardou.
 */
@HiltWorker
class PhoneInsightsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceUsageLazy: Lazy<DeviceUsageRepository>,
    private val access: UsageAccessManager,
    private val gate: DatabaseGate,
    private val settings: SettingsRepository,
    private val clock: ClockProvider,
    private val log: DebugEventLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val digital = settings.current().digital
        if (!digital.analysisEnabled || !digital.saveHistory || !access.isGranted()) return Result.success()
        if (!gate.isReady()) return Result.retry()
        val deviceUsage = deviceUsageLazy.get()
        log.log(DebugEventLogger.Category.WORKER, "phone insights")
        val today = clock.today()
        runCatching {
            deviceUsage.refreshDay(today.minusDays(1))
            deviceUsage.refreshDay(today)
        }.onFailure { return Result.retry() }
        return Result.success()
    }

    companion object {
        const val NAME = "hoodie_phone_insights"
    }
}
