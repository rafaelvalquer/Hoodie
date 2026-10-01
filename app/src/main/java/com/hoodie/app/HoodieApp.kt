package com.hoodie.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hoodie.app.core.notification.HoodieNotifier
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HoodieApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: HoodieNotifier
    @Inject lateinit var scheduler: WorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        scheduler.schedulePeriodic()
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
