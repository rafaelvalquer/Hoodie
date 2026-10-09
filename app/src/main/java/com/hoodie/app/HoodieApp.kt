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
    @Inject lateinit var notifier: HoodieNotifier
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var debugLog: DebugEventLogger
    @Inject lateinit var mobilityRegistration: com.hoodie.app.core.mobility.MobilityRegistration
    @Inject lateinit var databaseGate: com.hoodie.app.core.database.DatabaseGate
    @Inject lateinit var dayStateCoordinator: dagger.Lazy<com.hoodie.app.engine.daystate.DayStateCoordinator>
    @Inject lateinit var contextEngine: dagger.Lazy<com.hoodie.app.engine.context.ContextEngine>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        debugLog.log(DebugEventLogger.Category.DATABASE, "SQLCIPHER_NATIVE_LIBRARY_READY")
        notifier.createChannels()
        scheduler.schedulePeriodic()
        // Activity Recognition: registro idempotente (some em reboot/atualização/limpeza do Play Services).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { mobilityRegistration.sync() }
            if (databaseGate.isReady()) {
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
