package com.hoodie.app.core.backup

import android.util.AtomicFile
import java.io.File

/** Must run before ANY storage singleton is constructed, including DataStore. */
class BackupRestoreRecovery(private val root: File) {
    fun candidate(): StorageGeneration {
        val entry = BackupRestoreJournal(root).read() ?: return current()
        return StorageGeneration(if (entry.phase == RestorePhase.COMPLETED) entry.next else entry.previous)
    }
    fun current(): StorageGeneration {
        val marker = File(root, "storage-generation")
        val id = if (marker.exists() || File(marker.path + ".bak").exists())
            AtomicFile(marker).openRead().bufferedReader().use { it.readText() } else StorageGeneration.LEGACY
        return StorageGeneration(id)
    }
    @Synchronized fun recover(): StorageGeneration {
        val journal = BackupRestoreJournal(root)
        val entry = journal.read() ?: return current()
        // Until COMPLETED the old generation remains the authoritative recovery point.
        val chosen = if (entry.phase == RestorePhase.COMPLETED) entry.next else entry.previous
        if (runCatching { current().id }.getOrNull() != chosen) activate(StorageGeneration(chosen))
        if (entry.phase != RestorePhase.COMPLETED) journal.write(entry.copy(phase = RestorePhase.ROLLBACK_REQUIRED))
        return StorageGeneration(chosen)
    }
    fun activate(generation: StorageGeneration) = BackupRestoreJournal.durableWrite(File(root, "storage-generation"), generation.id)
}
