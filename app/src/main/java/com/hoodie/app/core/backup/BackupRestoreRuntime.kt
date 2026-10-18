package com.hoodie.app.core.backup

import android.content.Context
import androidx.work.WorkManager
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.engine.backup.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface BackupRestoreRuntime {
    suspend fun quiesce()
    fun restart()
}

/** Android side effects are isolated so the real committer can be tested without killing the test process. */
class AndroidBackupRestoreRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: DatabaseGate,
    private val repository: BackupRepository,
    private val reset: com.hoodie.app.core.security.LocalDataReset,
    private val notifications: com.hoodie.app.core.notification.HoodieNotifier,
    private val activityRecognition: com.hoodie.app.core.mobility.ActivityRecognitionRegistrar,
) : BackupRestoreRuntime {
    override suspend fun quiesce() {
        val activeWasReady = gate.isReady()
        gate.suspendForRestore()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        runCatching { activityRecognition.unregister() }
        runCatching { GeofenceManager.removeAll(context) }
        notifications.cancelAll()
        if (activeWasReady) repository.closeActiveIfOpen()
    }
    override fun restart() = reset.restart()
}
