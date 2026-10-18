package com.hoodie.app.engine.backup

import android.content.Context
import android.net.Uri
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class BackupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documents: BackupDocumentStore,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val staging: BackupStagingManager,
    private val operations: BackupOperationGate,
    private val committer: BackupRestoreCommitter,
    private val databaseGate: DatabaseGate,
    private val settings: SettingsRepository,
) {
    suspend fun export(uri: Uri, password: CharArray, digital: Boolean, state: (BackupState) -> Unit) {
        var entered = false
        try { withContext(Dispatchers.IO) { operations.run {
            val temp = File(context.noBackupFilesDir, "export-${java.util.UUID.randomUUID()}.hoodiebackup.tmp")
            val exportRecovery = BackupExportRecovery(context)
            var success = false
            val job = currentCoroutineContext()
            entered = true
            try {
                requireBackup(BackupKeyDerivation.isStrong(password))
                requireBackup(databaseGate.isReady(), BackupError.DATABASE)
                state(BackupState.PREPARING)
                exportRecovery.begin(uri)
                staging.ensureSpace()
                state(BackupState.READING)
                temp.outputStream().use { output ->
                    state(BackupState.ENCRYPTING)
                    exporter.export(output, password, digital) { job.ensureActive(); staging.ensureSpace() }
                }
                state(BackupState.WRITING)
                documents.write(uri).use { output -> temp.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    try { while (true) { job.ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }; output.flush() }
                    finally { buffer.fill(0) }
                } }
                state(BackupState.VERIFYING)
                documents.read(uri).use { input -> BackupCrypto.read(input, password) { job.ensureActive() } }
                job.ensureActive()
                withContext(NonCancellable) {
                    exportRecovery.verified(uri)
                    settings.setLastBackup(System.currentTimeMillis())
                    success = true
                    state(BackupState.SUCCESS)
                }
            } finally {
                password.fill('\u0000'); temp.delete()
                exportRecovery.finished()
                if (!success && !documents.delete(uri)) {
                    exportRecovery.warnIncomplete()
                    throw BackupException(BackupError.INCOMPLETE_DOCUMENT)
                }
            }
        } } } finally {
            password.fill('\u0000')
            if (!entered) discardIncompleteDocument(uri)
        }
    }

    suspend fun preview(uri: Uri, password: CharArray, state: (BackupState) -> Unit): BackupPreview {
        var prepared: BackupPreview? = null
        try {
            val result = withContext(Dispatchers.IO) {
                operations.run {
                    val job = currentCoroutineContext()
                    state(BackupState.DECRYPTING)
                    val preview = documents.read(uri).use { importer.validate(it, password) { job.ensureActive() } }
                    prepared = preview
                    state(BackupState.VALIDATING)
                    job.ensureActive()
                    state(BackupState.PREVIEW_READY)
                    preview
                }
            }
            prepared = null // transfer ownership only after the cancellable dispatcher hand-off
            return result
        } finally {
            password.fill('\u0000')
            prepared?.let { preview -> withContext(NonCancellable + Dispatchers.IO) { staging.discard(preview.stage) } }
        }
    }
    suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) { operations.run { committer.commit(preview) } }
    suspend fun discard(preview: BackupPreview) = withContext(Dispatchers.IO) { staging.discard(preview.stage) }
    fun hasInterruptedExport() = BackupExportRecovery(context).hasWarning()
    suspend fun discardIncompleteDocument(uri: Uri): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        documents.delete(uri).also { if (!it) BackupExportRecovery(context).warnIncomplete() }
    }
}
