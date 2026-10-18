package com.hoodie.app.core.backup

import android.content.Context
import com.hoodie.app.core.security.DatabaseKeyStore
import com.hoodie.app.core.security.LocalDataReset
import java.io.File

object BackupStorageCleanup {
    fun generations(context: Context): Set<StorageGeneration> {
        val names = context.databaseList().toList() + (context.noBackupFilesDir.list()?.toList() ?: emptyList()) +
            (File(context.filesDir, "datastore").list()?.toList() ?: emptyList())
        return names.mapNotNull { name ->
            Regex("(?:hoodie-)?(backup-[a-f0-9]{32})(?:\\.db|\\.key|\\.ready)(?:.*)?").matchEntire(name)?.groupValues?.get(1)
                ?: Regex("hoodie-settings-(backup-[a-f0-9]{32})\\.preferences_pb(?:.*)?").matchEntire(name)?.groupValues?.get(1)
        }
            .map(::StorageGeneration).toSet()
    }
    /** Startup only, before any DataStore or Room instance exists. Keep the last committed recovery point. */
    fun removeAbandoned(context: Context) {
        val current = StorageGeneration.current(context).id
        val entry = BackupRestoreJournal(context.noBackupFilesDir).read()
        val keep = setOf(current, entry?.previous)
        generations(context).filter { it.id !in keep }.forEach { deleteGeneration(context, it) }
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("export-") && it.name.endsWith(".hoodiebackup.tmp") }?.forEach { it.delete() }
    }
    fun deleteGeneration(context: Context, generation: StorageGeneration) {
        LocalDataReset.deleteDatabaseFiles(context.getDatabasePath(generation.databaseName))
        DatabaseKeyStore(context, generation.keyAlias, generation.keyFile).delete()
        runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(generation.coordinateAlias) }
        File(context.noBackupFilesDir, "${generation.id}.ready").delete()
        File(context.filesDir, "datastore/${generation.settingsName}.preferences_pb").delete()
        File(context.filesDir, "datastore/${generation.settingsName}.preferences_pb.tmp").delete()
    }
}
