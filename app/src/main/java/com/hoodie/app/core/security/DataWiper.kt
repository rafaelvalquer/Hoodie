package com.hoodie.app.core.security

import android.content.Context
import android.content.Intent
import com.hoodie.app.MainActivity
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Apagar todos os dados": nada do usuário sobrevive — geofences, tarefas,
 * notificações pendentes, preferências, o arquivo do banco cifrado, a senha do
 * banco e as chaves do Keystore. Depois o app reinicia limpo no onboarding.
 */
@Singleton
class DataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: HoodieDatabase,
    private val settings: SettingsRepository,
    private val geofences: GeofenceRegistrar,
    private val scheduler: WorkScheduler,
    private val notifier: Notifier,
    private val keys: DatabaseKeyStore,
) {
    suspend fun deleteEverything() {
        geofences.clear()
        scheduler.cancelAll()
        notifier.cancelAll()
        settings.clear()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            db.close()
            context.deleteDatabase(HoodieDatabase.NAME)
            keys.delete()
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KeystoreCoordinateCipher.ALIAS) }
        }
        restart()
    }

    /** O banco singleton foi fechado: recomeça o processo para tudo nascer limpo. */
    private fun restart() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
