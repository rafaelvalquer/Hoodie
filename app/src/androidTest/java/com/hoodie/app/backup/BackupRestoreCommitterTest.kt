package com.hoodie.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.security.DatabaseKeyResult
import com.hoodie.app.engine.backup.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the production committer with real durable writes and isolated Android storage. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreCommitterTest {
    @Test fun activationPublishesDatabasePreferencesAndKeysTogether() = exercise(failQuiesce = false)
    @Test fun failureAfterClosingOldRoomRollsBackAndKeepsOldDataReadable() = exercise(failQuiesce = true)

    private fun exercise(failQuiesce: Boolean) = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "commit-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String) = File(root, "databases/$name").also { it.parentFile!!.mkdirs() }
            override fun databaseList(): Array<String> = File(root, "databases").list() ?: emptyArray()
            override fun deleteDatabase(name: String): Boolean = LocalDelete(getDatabasePath(name))
        }
        val staging = BackupStagingManager(context)
        val old = staging.create(); val next = staging.create()
        try {
            old.database.openHelper.writableDatabase.execSQL("INSERT INTO memories VALUES ('old','','Anterior',1700000000000)")
            next.database.openHelper.writableDatabase.execSQL("INSERT INTO memories VALUES ('new','','Restaurado',1700000000000)")
            SettingsRepository(generationSettingsStore(context, old.generation)).setCatName("Anterior")
            BackupRestoreRecovery(context.noBackupFilesDir).activate(old.generation)
            val preferences = BackupSettingsMapper.restore(org.json.JSONObject().put("cat_name", "Restaurado"))
            val manifest = BackupManifest("test", 1792300000000, "UTC", java.util.UUID.randomUUID().toString(), false, emptyMap())
            var restarted = false
            val runtime = object : BackupRestoreRuntime {
                override suspend fun quiesce() {
                    old.database.close()
                    if (failQuiesce) throw java.io.IOException("injected interruption")
                }
                override fun restart() { restarted = true }
            }
            try {
                BackupRestoreCommitter(context, runtime).commit(BackupPreview(manifest, next, preferences))
                assertFalse(failQuiesce)
            } catch (e: BackupException) { assertTrue(failQuiesce); assertEquals(BackupError.ACTIVATION, e.error) }
            assertEquals(!failQuiesce, restarted)
            val selected = BackupRestoreRecovery(context.noBackupFilesDir).recover()
            val expected = if (failQuiesce) old else next
            assertEquals(expected.generation, selected)
            val pass = (expected.keys.read() as DatabaseKeyResult.Available).passphrase
            val reopened = Room.databaseBuilder(context, HoodieDatabase::class.java, selected.databaseName).openHelperFactory(SupportOpenHelperFactory(pass)).build()
            try {
                val settings = SettingsRepository(generationSettingsStore(context, selected)).current()
                reopened.openHelper.writableDatabase.query("SELECT title FROM memories").use { it.moveToFirst(); assertEquals(settings.catName, it.getString(0)) }
            } finally { reopened.close() }
            // A successful activation must still retain the old encrypted recovery generation.
            assertTrue(context.getDatabasePath(old.generation.databaseName).exists())
            assertTrue(old.keys.read() is DatabaseKeyResult.Available)
        } finally {
            staging.discard(old); staging.discard(next)
            // Remove only the dedicated test root, preserving live DataStore files until process exit.
            File(context.noBackupFilesDir, "storage-generation").delete()
            File(context.noBackupFilesDir, "backup-restore.journal").delete()
        }
    }
    private fun LocalDelete(file: File): Boolean = com.hoodie.app.core.security.LocalDataReset.deleteDatabaseFiles(file).isEmpty()
}
