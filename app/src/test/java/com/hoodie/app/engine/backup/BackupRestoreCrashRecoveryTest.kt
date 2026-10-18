package com.hoodie.app.engine.backup

import android.app.Application
import com.hoodie.app.core.backup.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRestoreCrashRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun everyInterruptedPhaseSelectsOneCompleteGenerationBeforeStorageOpens() {
        RestorePhase.entries.forEach { phase ->
            listOf(false, true).forEach { pointerAlreadySwitched ->
                val root = folder.newFolder()
                val old = StorageGeneration.fresh(); val next = StorageGeneration.fresh()
                // Separate namespaces model the actual two databases + two DataStores.
                File(root, old.databaseName).writeText("old-db"); File(root, old.settingsName).writeText("old-preferences")
                File(root, next.databaseName).writeText("new-db"); File(root, next.settingsName).writeText("new-preferences")
                val recovery = BackupRestoreRecovery(root)
                recovery.activate(if (pointerAlreadySwitched) next else old)
                BackupRestoreJournal(root).write(RestoreEntry(old.id, next.id, phase))
                val selected = recovery.recover()
                val expected = if (phase == RestorePhase.COMPLETED) next else old
                assertEquals(expected, selected)
                assertEquals(expected, recovery.current())
                assertEquals(expected, recovery.recover()) // recovery itself is idempotent
                assertEquals(if (phase == RestorePhase.COMPLETED) "new-db" else "old-db", File(root, selected.databaseName).readText())
                assertEquals(if (phase == RestorePhase.COMPLETED) "new-preferences" else "old-preferences", File(root, selected.settingsName).readText())
                assertTrue(File(root, old.databaseName).exists())
            }
        }
    }
    @Test fun existingInstallRetainsItsExactLegacyNamesWithoutJournal() {
        val generation = BackupRestoreRecovery(folder.newFolder()).recover()
        assertEquals("hoodie.db", generation.databaseName)
        assertEquals("hoodie_settings", generation.settingsName)
        assertEquals("hoodie_places_v1", generation.coordinateAlias)
        assertEquals("hoodie_db.key", generation.keyFile)
    }
    @Test fun generationIdentifiersCannotEscapePrivateStorage() {
        listOf("../../sdcard", "", "backup-test", "legacy/elsewhere").forEach { value ->
            try { StorageGeneration(value); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun unreadableJournalLocksRecoveryWithoutGuessingOrDeletingData() {
        val root = folder.newFolder()
        File(root, "backup-restore.journal").writeText("torn journal")
        val old = File(root, "preserved.db").apply { writeText("encrypted old data") }
        try {
            BackupRestoreStartup.prepare(root)
            assertTrue(BackupRestoreStartup.isBlocked(root))
            assertEquals(StorageGeneration.UNAVAILABLE, BackupRestoreStartup.generation(root)!!.id)
            assertEquals("encrypted old data", old.readText())
        } finally { BackupRestoreStartup.clear(root) }
    }
    @Test fun pointerWriteFailureRetainsKnownOldGenerationAndCanBeRetried() {
        val root = folder.newFolder()
        val old = StorageGeneration.fresh(); val next = StorageGeneration.fresh()
        BackupRestoreJournal(root).write(RestoreEntry(old.id, next.id, RestorePhase.COMMITTING))
        val blocker = File(root, "storage-generation").apply { mkdir() }
        val occupied = File(blocker, "occupied").apply { writeText("block rename") }
        try {
            BackupRestoreStartup.prepare(root)
            assertTrue(BackupRestoreStartup.isBlocked(root))
            assertEquals(old, BackupRestoreStartup.generation(root))
            assertTrue(occupied.delete()); assertTrue(blocker.delete())
            BackupRestoreStartup.prepare(root)
            assertFalse(BackupRestoreStartup.isBlocked(root))
            assertEquals(old, BackupRestoreRecovery(root).current())
        } finally { BackupRestoreStartup.clear(root) }
    }
    @Test fun blockedStartupReachesRecoveryGateWithoutCreatingDatabaseKeys() {
        val root = folder.newFolder()
        val base = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = object : android.content.ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        File(root, "backup-restore.journal").writeText("unreadable journal")
        try {
            BackupRestoreStartup.prepare(root)
            val keys = com.hoodie.app.core.security.DatabaseKeyStore(context)
            val gate = com.hoodie.app.core.database.DatabaseGate(context, keys)
            val result = gate.ensure() as com.hoodie.app.core.database.DatabaseBootstrapResult.Failed
            assertEquals(com.hoodie.app.core.database.DatabaseSecurityState.RestoreRecoveryFailed, result.state)
            assertFalse(keys.exists())
        } finally { BackupRestoreStartup.clear(root) }
    }
    @Test fun documentPickerCannotOverwriteAnInternalFileThroughAFileUri() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = folder.newFile().apply { writeText("preserved internal data") }
        val error = rejects { BackupDocumentStore(context).write(android.net.Uri.fromFile(original)).close() }
        assertEquals(BackupError.STORAGE, error.error)
        assertEquals("preserved internal data", original.readText())
    }
}
