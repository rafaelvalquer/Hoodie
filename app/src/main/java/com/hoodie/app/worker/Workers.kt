package com.hoodie.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoodie.app.core.database.LocationEventDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.HoodieNotifier
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.atZone
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
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
    private val contextEngine: ContextEngine,
    private val hoodie: HoodieEngine,
    private val memory: MemoryEngine,
    private val settings: SettingsRepository,
    private val notifier: HoodieNotifier,
    private val geofences: GeofenceManager,
    private val locationEvents: LocationEventDao,
    private val clock: ClockProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.onboardingDone) return Result.success()
        contextEngine.applyRoutineFallbackIfNeeded()
        val snapshot = hoodie.resolve()
        memory.checkCalendar()
        maybeNotifyAutonomy(snapshot.started.map { it.activity to it.userContext })

        val now = clock.nowMillis()
        locationEvents.deleteOlderThan(now - 30 * DAY_MS)
        // Uma vez por dia, garante que os geofences continuam registrados.
        if (now.atZone(clock.zone()).hour == 4) geofences.registerAll()
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

    companion object { const val NAME = "hoodie_reconcile" }
}

/** Checagens pontuais agendadas pelo ContextEngine (almoço provável / deslocamento longo). */
@HiltWorker
class CheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contextEngine: ContextEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
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
