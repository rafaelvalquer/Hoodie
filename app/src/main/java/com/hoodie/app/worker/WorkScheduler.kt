package com.hoodie.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hoodie.app.core.config.HoodieConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Checagens pontuais pedidas pelo Context Engine (fake nos testes). */
interface CheckScheduler {
    fun scheduleLunchCheck(exitAt: Long, placeId: Long)
    fun scheduleCommuteCheck(eventId: Long)
    fun cancelChecks()
    fun reconcileNow()
}

/**
 * Checagem atrasada da mobilidade: "a caminhada continuou 2 min?", "ficou parado 3 min?".
 * O Activity Recognition só avisa mudanças; a duração é conferida aqui (fake nos testes).
 */
interface MobilityScheduler {
    fun scheduleMobilityCheck(delayMs: Long)
    fun cancelMobilityCheck()
}

/**
 * WorkManager só para reconciliar estado, checagens pontuais e manutenção —
 * nunca para rastrear GPS.
 */
@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) : CheckScheduler, MobilityScheduler {
    private val wm get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        if (HoodieConfig.LEARNED_ROUTINE) wm.enqueueUniquePeriodicWork(RoutineLearningWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RoutineLearningWorker>(24, TimeUnit.HOURS).build())
        wm.enqueueUniquePeriodicWork(
            ReconcileWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(HoodieConfig.RECONCILE_INTERVAL_MIN, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            PhoneInsightsWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PhoneInsightsWorker>(HoodieConfig.PHONE_INSIGHTS_REFRESH_HOURS, TimeUnit.HOURS).build(),
        )
    }

    override fun reconcileNow() {
        wm.enqueueUniqueWork(ReconcileWorker.NAME + "_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReconcileWorker>().build())
    }

    /** Depois de sair do trabalho no horário de almoço, confere se a pessoa "parou" em algum lugar. */
    override fun scheduleLunchCheck(exitAt: Long, placeId: Long) {
        wm.enqueueUniqueWork(
            CheckWorker.LUNCH, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(LUNCH_DELAY_MIN, TimeUnit.MINUTES)
                .setInputData(workDataOf(CheckWorker.KIND to CheckWorker.LUNCH, CheckWorker.AT to exitAt, CheckWorker.PLACE to placeId))
                .build(),
        )
    }

    /** Deslocamento muito longo sem chegar a lugar conhecido → talvez seja um lugar novo. */
    override fun scheduleCommuteCheck(eventId: Long) {
        wm.enqueueUniqueWork(
            CheckWorker.COMMUTE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CheckWorker>()
                .setInitialDelay(COMMUTE_DELAY_MIN, TimeUnit.MINUTES)
                .setInputData(workDataOf(CheckWorker.KIND to CheckWorker.COMMUTE, CheckWorker.EVENT to eventId))
                .build(),
        )
    }

    override fun cancelChecks() {
        wm.cancelUniqueWork(CheckWorker.LUNCH)
        wm.cancelUniqueWork(CheckWorker.COMMUTE)
    }

    override fun scheduleMobilityCheck(delayMs: Long) {
        wm.enqueueUniqueWork(
            MobilityCheckWorker.NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MobilityCheckWorker>().setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
        )
    }

    override fun cancelMobilityCheck() = wm.cancelUniqueWork(MobilityCheckWorker.NAME).let { }

    fun cancelAll() = wm.cancelAllWork()

    companion object {
        const val LUNCH_DELAY_MIN = HoodieConfig.LUNCH_CHECK_DELAY_MIN
        const val COMMUTE_DELAY_MIN = HoodieConfig.COMMUTE_CHECK_DELAY_MIN
    }
}
