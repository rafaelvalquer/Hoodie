package com.hoodie.app.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.security.DatabaseKeyResult
import com.hoodie.app.core.security.LocalDataReset
import com.hoodie.app.engine.backup.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Real encrypted staging/rollback; only File.usableSpace is controlled. Never fills a device. */
@RunWith(AndroidJUnit4::class)
class BackupLowSpaceTest {
    @Test fun insufficientSpaceRejectsImportBeforeReadingOrAllocatingAnotherGeneration() {
        exercise { context, staging, old ->
            context.availableBytes = BackupStagingManager.MIN_FREE_BYTES - 1
            val unreadable = object : InputStream() {
                override fun read(): Int = throw AssertionError("Archive must not be read before the space check")
            }
            expectNoSpace {
                BackupImporter(staging).validate(unreadable, "hoodie recovery phrase".toCharArray()) { }
            }
            assertEquals(setOf(old.generation), BackupStorageCleanup.generations(context))
        }
    }

    @Test fun spaceLostDuringImportDiscardsThePartialGenerationAndPreservesTheActiveDatabase() {
        exercise { context, staging, old ->
            val at = System.currentTimeMillis()
            val counts = BackupSchemaV1.datasets.filterNot { it.digital }.associate { it.name to if (it.name == "places") 128L else 0L }
            val manifest = BackupManifest("low-space-test", at, "UTC", UUID.randomUUID().toString(), false, counts)
            val archive = ByteArrayOutputStream()
            val password = "hoodie recovery phrase".toCharArray()
            try {
                BackupCrypto.Writer(archive, password).use { writer ->
                    writer.record(manifest.json().toString().toByteArray(Charsets.UTF_8))
                    writer.record(JSONObject().put("kind", "settings").put("version", 1)
                        .put("data", JSONObject()).toString().toByteArray(Charsets.UTF_8))
                    repeat(128) { index ->
                        val place = BackupPlaceV1((index + 1).toLong(), "Synthetic ${index + 1}", "HOME",
                            150.0, 1.0, 0, 0, at, null, -23.0, -46.0)
                        writer.record(JSONObject().put("kind", "record").put("dataset", "places")
                            .put("version", 1).put("data", place.json()).toString().toByteArray(Charsets.UTF_8))
                    }
                    writer.finish()
                }
                var authenticatedUnits = 0
                expectNoSpace {
                    BackupImporter(staging).validate(archive.toByteArray().inputStream(), password) {
                        authenticatedUnits++
                        // Manifest + settings + 128 places: fail the periodic check after 127 inserts.
                        if (authenticatedUnits == 130) context.availableBytes = 0
                    }
                }
                assertEquals(130, authenticatedUnits)
                assertEquals(setOf(old.generation), BackupStorageCleanup.generations(context))
                assertTrue(context.databasePaths.size >= 2)
                context.databasePaths.filter { it.name != old.generation.databaseName }.forEach { path ->
                    assertFalse("Partial database remains: ${path.name}", path.exists())
                    val id = path.name.removePrefix("hoodie-").removeSuffix(".db")
                    val generation = StorageGeneration(id)
                    val keystore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    assertFalse(keystore.containsAlias(generation.keyAlias))
                    assertFalse(keystore.containsAlias(generation.coordinateAlias))
                }
            } finally { password.fill('\u0000') }
        }
    }

    private fun expectNoSpace(action: () -> Unit) {
        try { action(); fail("Import must reject insufficient space") }
        catch (error: BackupException) { assertEquals(BackupError.NO_SPACE, error.error) }
    }

    private fun exercise(action: (SpaceContext, BackupStagingManager, BackupStage) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = SpaceContext(base, File(base.cacheDir, "low-space-${UUID.randomUUID()}"))
        val staging = BackupStagingManager(context)
        val old = staging.create()
        try {
            old.database.openHelper.writableDatabase.execSQL("INSERT INTO memories VALUES ('original','','Preserved',?)", arrayOf(System.currentTimeMillis()))
            BackupRestoreRecovery(context.noBackupFilesDir).activate(old.generation)
            action(context, staging, old)
            assertEquals(old.generation, StorageGeneration.current(context))
            val key = old.keys.read()
            assertTrue(key is DatabaseKeyResult.Available)
            val passphrase = (key as DatabaseKeyResult.Available).passphrase
            old.database.close()
            val reopened = Room.databaseBuilder(context, HoodieDatabase::class.java, old.generation.databaseName)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf())).build()
            try {
                reopened.openHelper.readableDatabase.query("SELECT id, title FROM memories").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("original", it.getString(0))
                    assertEquals("Preserved", it.getString(1))
                    assertFalse(it.moveToNext())
                }
            } finally { reopened.close(); passphrase.fill(0) }
        } finally {
            staging.discard(old)
            File(context.noBackupFilesDir, "storage-generation").delete()
            File(context.noBackupFilesDir, "backup-restore.journal").delete()
        }
    }

    private class SpaceContext(base: Context, private val root: File) : ContextWrapper(base) {
        var availableBytes = BackupStagingManager.MIN_FREE_BYTES * 2
        val databasePaths = linkedSetOf<File>()
        private val controlledFiles = object : File(root, "files") {
            override fun getUsableSpace(): Long = availableBytes
        }
        override fun getFilesDir(): File = controlledFiles.apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(root, "databases/$name").also {
            it.parentFile!!.mkdirs()
            databasePaths.add(it)
        }
        override fun databaseList(): Array<String> = File(root, "databases").list() ?: emptyArray()
        override fun deleteDatabase(name: String): Boolean = LocalDataReset.deleteDatabaseFiles(getDatabasePath(name)).isEmpty()
    }
}
