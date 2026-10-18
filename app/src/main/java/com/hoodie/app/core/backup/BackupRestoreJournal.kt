package com.hoodie.app.core.backup

import android.util.AtomicFile
import java.io.File

enum class RestorePhase { PREPARED, COMMITTING, ACTIVATED, COMPLETED, ROLLBACK_REQUIRED }
data class RestoreEntry(val previous: String, val next: String, val phase: RestorePhase)

/** Only generation identifiers, never user data, passwords or keys. */
class BackupRestoreJournal(private val root: File) {
    private val journal get() = File(root, "backup-restore.journal")
    fun read(): RestoreEntry? {
        if (!journal.exists() && !File(journal.path + ".bak").exists()) return null
        val fields = AtomicFile(journal).openRead().bufferedReader().use { it.readText() }.split('\n')
        check(fields.size == 3 && fields.take(2).all(StorageGeneration::valid)) { "Invalid restore journal" }
        return RestoreEntry(fields[0], fields[1], RestorePhase.valueOf(fields[2]))
    }
    fun write(entry: RestoreEntry) = durableWrite(journal, "${entry.previous}\n${entry.next}\n${entry.phase.name}")
    companion object {
        fun durableWrite(file: File, value: String) {
            file.parentFile!!.mkdirs()
            if (file.isDirectory) throw java.io.IOException("Invalid storage target")
            // Read any legacy AtomicFile recovery point before replacing it with the NIO writer.
            if (File(file.path + ".bak").exists()) AtomicFile(file).openRead().close()
            val temporary = File(file.parentFile, file.name + ".tmp")
            try {
                java.io.FileOutputStream(temporary).use { stream ->
                    stream.write(value.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                try {
                    java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                val persisted = file.readText(Charsets.UTF_8)
                if (persisted != value) throw java.io.IOException("Storage publication failed")
            } finally { temporary.delete() }
        }
    }
}

data class StorageGeneration(val id: String) {
    init { require(valid(id)) }
    val databaseName get() = if (id == LEGACY) "hoodie.db" else "hoodie-$id.db"
    val keyFile get() = if (id == LEGACY) "hoodie_db.key" else "hoodie-$id.key"
    val keyAlias get() = if (id == LEGACY) "hoodie_db_key_wrap" else "hoodie-db-$id"
    val coordinateAlias get() = if (id == LEGACY) "hoodie_places_v1" else "hoodie-places-$id"
    val settingsName get() = if (id == LEGACY) "hoodie_settings" else "hoodie-settings-$id"
    companion object {
        const val LEGACY = "legacy"
        const val UNAVAILABLE = "unavailable"
        fun valid(id: String) = id == LEGACY || id == UNAVAILABLE || id.matches(Regex("backup-[a-f0-9]{32}"))
        fun fresh() = StorageGeneration("backup-" + java.util.UUID.randomUUID().toString().replace("-", ""))
        fun current(context: android.content.Context): StorageGeneration = BackupRestoreStartup.generation(context.noBackupFilesDir)
            ?: BackupRestoreRecovery(context.noBackupFilesDir).current()
    }
}
