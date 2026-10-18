package com.hoodie.app.core.backup

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Fail closed into the recovery UI if a journal/pointer cannot be repaired, including full storage. */
object BackupRestoreStartup {
    private val blocked = ConcurrentHashMap<String, StorageGeneration>()
    fun prepare(root: File) {
        val recovery = BackupRestoreRecovery(root)
        try {
            val chosen = recovery.recover()
            if (chosen.id == StorageGeneration.UNAVAILABLE) blocked[root.absolutePath] = chosen
            else blocked.remove(root.absolutePath)
        } catch (_: Exception) {
            // A valid journal can still identify the old complete generation when its pointer write failed.
            // With no trustworthy metadata, never guess a database or silently create a fresh installation.
            blocked[root.absolutePath] = runCatching { recovery.candidate() }.getOrDefault(StorageGeneration(StorageGeneration.UNAVAILABLE))
        }
    }
    fun generation(root: File): StorageGeneration? = blocked[root.absolutePath]
    fun isBlocked(root: File): Boolean = blocked.containsKey(root.absolutePath)
    fun clear(root: File) { blocked.remove(root.absolutePath) }
}
