package com.hoodie.app.core.security

import android.content.Context
import android.content.Intent
import com.hoodie.app.MainActivity
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.core.database.DefaultSecureDatabaseBootstrap
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apaga todos os dados locais SEM precisar abrir o banco — usado pela tela de
 * recuperação (senha irrecuperável) e pelo "Apagar todos os dados" dos Ajustes.
 * Nunca é chamado automaticamente: só por decisão explícita do usuário.
 */
@Singleton
class LocalDataReset @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keys: DatabaseKeyStore,
    private val gate: DatabaseGate,
    private val settings: SettingsRepository,
    private val scheduler: WorkScheduler,
    private val notifier: Notifier,
) {
    /** [closeDb] fecha o banco quando ele estiver aberto (Ajustes); na recuperação é null. */
    suspend fun wipe(closeDb: (() -> Unit)? = null) {
        scheduler.cancelAll()
        notifier.cancelAll()
        GeofenceManager.removeAll(context)
        settings.clear()
        withContext(Dispatchers.IO) {
            closeDb?.invoke()
            context.deleteDatabase(HoodieDatabase.NAME)
            deleteDatabaseFiles(context.getDatabasePath(HoodieDatabase.NAME))
            keys.delete()
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KeystoreCoordinateCipher.ALIAS) }
        }
        gate.reset()
    }

    /** Recomeça o processo para tudo nascer limpo (singletons, banco, Hilt). */
    fun restart() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /** Banco + arquivos auxiliares + restos de migração. Devolve o que ainda existir (vazio = ok). */
        fun deleteDatabaseFiles(db: File): List<File> {
            val all = listOf("", "-wal", "-shm", "-journal", DefaultSecureDatabaseBootstrap.TMP_SUFFIX).map { File(db.path + it) }
            all.forEach { it.delete() }
            return all.filter { it.exists() }
        }
    }
}
