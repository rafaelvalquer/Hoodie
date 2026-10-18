package com.hoodie.app.core.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File

/** Remembers only a document URI so an interrupted write can be removed at the next startup. */
class BackupExportRecovery(private val context: Context) {
    private val file get() = File(context.noBackupFilesDir, "backup-export.journal")
    private val warning get() = File(context.noBackupFilesDir, "backup-incomplete-warning")
    fun begin(uri: Uri) {
        BackupRestoreJournal.durableWrite(file, "WRITING\n$uri")
    }
    fun verified(uri: Uri) = BackupRestoreJournal.durableWrite(file, "VERIFIED\n$uri")
    fun finished() { AtomicFile(file).delete(); File(file.path + ".tmp").delete() }
    fun warnIncomplete() = BackupRestoreJournal.durableWrite(warning, "1")
    fun hasWarning() = warning.exists()
    fun recover() {
        if (!file.exists() && !File(file.path + ".bak").exists()) return
        val fields = runCatching { AtomicFile(file).openRead().bufferedReader().use { it.readText() }.split('\n', limit = 2) }.getOrNull()
        if (fields == null || fields.size != 2) { warnIncomplete(); finished(); return }
        if (fields[0] != "VERIFIED") {
            val removed = BackupDocumentStore(context).delete(Uri.parse(fields[1]))
            if (!removed) warnIncomplete()
        }
        finished()
    }
}
