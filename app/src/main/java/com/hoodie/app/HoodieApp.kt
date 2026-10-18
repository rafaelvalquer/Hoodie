package com.hoodie.app

import kotlinx.coroutines.launch
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hoodie.app.core.notification.HoodieNotifier
import com.hoodie.app.core.database.SqlCipherNativeLoader
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HoodieApp : Application(), Configuration.Provider {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        com.hoodie.app.core.backup.BackupRestoreStartup.prepare(base.noBackupFilesDir)
    }
    companion object {
        init {
            // Robolectric executa no host JVM e não consegue carregar a .so Android;
            // o preload real continua obrigatório em qualquer runtime do aplicativo.
            if (android.os.Build.FINGERPRINT != "robolectric") {
                SqlCipherNativeLoader.ensureLoaded()
            }
        }
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var processForeground: com.hoodie.app.core.location.ProcessForegroundState
    @Inject lateinit var notifier: HoodieNotifier
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var debugLog: DebugEventLogger
    @Inject lateinit var mobilityRegistration: com.hoodie.app.core.mobility.MobilityRegistration
    @Inject lateinit var databaseGate: com.hoodie.app.core.database.DatabaseGate
    @Inject lateinit var dayStateCoordinator: dagger.Lazy<com.hoodie.app.engine.daystate.DayStateCoordinator>
    @Inject lateinit var effectiveRoutine: dagger.Lazy<com.hoodie.app.data.repository.EffectiveRoutineRepository>
    @Inject lateinit var contextEngine: dagger.Lazy<com.hoodie.app.engine.context.ContextEngine>
    @Inject lateinit var restoredGeofences: dagger.Lazy<com.hoodie.app.core.geofence.GeofenceManager>
    @Inject lateinit var backupOperations: com.hoodie.app.core.backup.BackupOperationGate

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        debugLog.log(DebugEventLogger.Category.DATABASE, "SQLCIPHER_NATIVE_LIBRARY_READY")
        notifier.createChannels()
        scheduler.schedulePeriodic()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                backupOperations.run {
                    if (com.hoodie.app.core.backup.BackupRestoreStartup.generation(noBackupFilesDir)?.id != com.hoodie.app.core.backup.StorageGeneration.UNAVAILABLE) {
                        com.hoodie.app.core.backup.BackupStorageCleanup.removeAbandoned(this@HoodieApp)
                    }
                    com.hoodie.app.core.backup.BackupExportRecovery(this@HoodieApp).recover()
                }
            }
        }
        // Activity Recognition: registro idempotente (some em reboot/atualização/limpeza do Play Services).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            if (databaseGate.isReady()) {
                runCatching { mobilityRegistration.sync() }
                if (com.hoodie.app.core.backup.StorageGeneration.current(this@HoodieApp).id != com.hoodie.app.core.backup.StorageGeneration.LEGACY) {
                    runCatching { restoredGeofences.get().registerAll() }
                }
                effectiveRoutine.get().currentSnapshot()
                if (com.hoodie.app.core.config.HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION) {
                    runCatching { contextEngine.get().dismissPendingContextConfirmations() }
                }
                if (com.hoodie.app.core.config.HoodieConfig.DAY_STATE_ENGINE) dayStateCoordinator.get().start(this)
            }
        }
        configureMap()
        // Sprite sheets do Aseprite (assets/pixel/hoodie) por cima do procedural.
        com.hoodie.app.pixel.sprite.AndroidSpriteSheets.install(this)
    }

    /** osmdroid: user-agent exigido pelos servidores do OSM e cache de tiles só no armazenamento interno. */
    private fun configureMap() {
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
        }
    }
}
