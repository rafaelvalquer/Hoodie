package com.hoodie.app.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.engine.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupRecoveryImportTest {
    @Test fun fullImportPreviewDoesNotTouchAnUnrecoverableActiveDatabaseOrCoordinateKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var bootstrapCalls = 0
        val gate = DatabaseGate { object : SecureDatabaseBootstrap {
            override fun prepare(): DatabaseBootstrapResult { bootstrapCalls++; return DatabaseBootstrapResult.Failed(DatabaseSecurityState.KeyUnrecoverable) }
        } }
        val settings = SettingsRepository(generationSettingsStore(context, StorageGeneration.fresh()))
        val repository = BackupRepository(dagger.Lazy<HoodieDatabase> { throw AssertionError("Active database opened") },
            dagger.Lazy<CoordinateCipher> { throw AssertionError("Old coordinates key requested") }, settings)
        val staging = BackupStagingManager(context)
        val runtime = object : BackupRestoreRuntime {
            override suspend fun quiesce() { fail("Preview must not activate") }
            override fun restart() { fail("Preview must not restart") }
        }
        val coordinator = BackupCoordinator(context, BackupDocumentStore(context), BackupExporter(repository), BackupImporter(staging), staging,
            BackupOperationGate(), BackupRestoreCommitter(context, runtime), gate, settings)
        val encrypted = File(context.cacheDir, "recovery-${java.util.UUID.randomUUID()}.hoodiebackup")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("backup-v1-minimal.hoodiebackup").use { input -> encrypted.outputStream().use { input.copyTo(it) } }
            val password = "hoodie recovery phrase".toCharArray()
            val preview = coordinator.preview(Uri.fromFile(encrypted), password) { }
            try {
                assertEquals(0, bootstrapCalls)
                assertEquals(1L, preview.manifest.counts["places"])
                assertTrue(password.all { it == '\u0000' })
            } finally { coordinator.discard(preview) }
        } finally { encrypted.delete() }
    }
}
