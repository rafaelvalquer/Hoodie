package com.hoodie.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.security.DatabaseKeyResult
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreRollbackTest {
    @Test fun coldOpenAfterEachJournalBoundaryNeverMixesRoomAndDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = BackupStagingManager(context)
        val old = staging.create(); val next = staging.create()
        val journalRoot = File(context.cacheDir, "backup-recovery-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            SettingsRepository(generationSettingsStore(context, old.generation)).setCatName("Anterior")
            SettingsRepository(generationSettingsStore(context, next.generation)).setCatName("Restaurado")
            old.database.openHelper.writableDatabase.execSQL("INSERT INTO memories VALUES ('old','','Anterior',1700000000000)")
            next.database.openHelper.writableDatabase.execSQL("INSERT INTO memories VALUES ('new','','Restaurado',1700000000000)")
            old.database.close(); next.database.close()
            for (phase in RestorePhase.entries) {
                val recovery = BackupRestoreRecovery(journalRoot)
                recovery.activate(next.generation) // simulate interruption after a pointer write, before/after durable completion
                BackupRestoreJournal(journalRoot).write(RestoreEntry(old.generation.id, next.generation.id, phase))
                val generation = recovery.recover()
                val selected = if (phase == RestorePhase.COMPLETED) next else old
                assertEquals(selected.generation, generation)
                val password = (selected.keys.read() as DatabaseKeyResult.Available).passphrase
                val reopened = Room.databaseBuilder(context, HoodieDatabase::class.java, generation.databaseName)
                    .openHelperFactory(SupportOpenHelperFactory(password)).build()
                try {
                    reopened.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(12, it.getInt(0)) }
                    val name = SettingsRepository(generationSettingsStore(context, generation)).current().catName
                    reopened.openHelper.writableDatabase.query("SELECT title FROM memories").use { it.moveToFirst(); assertEquals(name, it.getString(0)) }
                } finally { reopened.close() }
            }
        } finally {
            staging.discard(old); staging.discard(next)
            journalRoot.listFiles()?.forEach { it.delete() }; journalRoot.delete()
        }
    }
}
