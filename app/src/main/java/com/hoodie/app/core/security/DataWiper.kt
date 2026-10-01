package com.hoodie.app.core.security

import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Apagar todos os dados": nada do usuário pode sobreviver — banco, preferências,
 * geofences, tarefas agendadas e notificações pendentes.
 */
@Singleton
class DataWiper @Inject constructor(
    private val db: HoodieDatabase,
    private val settings: SettingsRepository,
    private val geofences: GeofenceRegistrar,
    private val scheduler: WorkScheduler,
    private val notifier: Notifier,
) {
    suspend fun deleteEverything() {
        geofences.clear()
        scheduler.cancelAll()
        notifier.cancelAll()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clear()
    }
}
