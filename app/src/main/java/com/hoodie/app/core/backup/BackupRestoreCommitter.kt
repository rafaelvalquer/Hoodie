package com.hoodie.app.core.backup

import android.content.Context
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.datastore.generationSettingsStore
import com.hoodie.app.engine.backup.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class BackupRestoreCommitter(
    private val context: Context,
    private val runtime: BackupRestoreRuntime,
) {
    @Inject constructor(@ApplicationContext context: Context, runtime: AndroidBackupRestoreRuntime) : this(context, runtime as BackupRestoreRuntime)
    /** Called ONLY after the explicit destructive confirmation in the preview. Cancellation ends here. */
    suspend fun commit(preview: BackupPreview) = withContext(NonCancellable) {
        val storage = BackupRestoreRecovery(context.noBackupFilesDir)
        val previous = StorageGeneration.current(context)
        val journal = BackupRestoreJournal(context.noBackupFilesDir)
        val stage = preview.stage
        try {
            BackupValidator.validateDatabase(stage.database.openHelper.writableDatabase)
            val targetSettings = SettingsRepository(generationSettingsStore(context, stage.generation))
            targetSettings.replaceBackupPreferences(preview.preferences)
            stage.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            stage.database.close()
            val entry = RestoreEntry(previous.id, stage.generation.id, RestorePhase.PREPARED)
            journal.write(entry)
            runtime.quiesce()
            journal.write(entry.copy(phase = RestorePhase.COMMITTING))
            // Both the complete database and its complete preferences become visible via ONE atomic pointer.
            storage.activate(stage.generation)
            journal.write(entry.copy(phase = RestorePhase.ACTIVATED))
            journal.write(entry.copy(phase = RestorePhase.COMPLETED))
            runtime.restart()
        } catch (_: Throwable) {
            storage.activate(previous)
            journal.write(RestoreEntry(previous.id, stage.generation.id, RestorePhase.ROLLBACK_REQUIRED))
            // The old Room instance may already be closed; restart is required even after rollback.
            throw BackupException(BackupError.ACTIVATION)
        }
    }
}
