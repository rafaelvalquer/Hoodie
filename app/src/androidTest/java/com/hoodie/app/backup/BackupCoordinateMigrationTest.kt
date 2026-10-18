package com.hoodie.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BackupCoordinateMigrationTest {
    @Test fun exportedArchiveRestoresAfterAllOldDeviceKeysAreDestroyed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = BackupStagingManager(context)
        val source = staging.create()
        val password = "hoodie recovery phrase".toCharArray()
        try {
            source.database.placeDao().insert(PlaceEntity(name = "Casa fictícia", type = PlaceType.HOME,
                encryptedCoordinates = source.coordinates.encrypt(-22.0, -43.0), radiusMeters = 150f, confidence = .9f, createdAt = 1700000000000))
            val settings = SettingsRepository(generationSettingsStore(context, source.generation))
            val repository = BackupRepository(dagger.Lazy { source.database }, dagger.Lazy { source.coordinates }, settings)
            val output = ByteArrayOutputStream()
            BackupExporter(repository).export(output, password, false) { }
            source.database.close()
            source.keys.delete()
            val keystore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keystore.deleteEntry(source.generation.coordinateAlias)
            assertFalse(source.keys.exists())
            assertFalse(keystore.containsAlias(source.generation.coordinateAlias))
            val preview = BackupImporter(staging).validate(output.toByteArray().inputStream(), password) { }
            try {
                val place = preview.stage.database.placeDao().getAll().single()
                assertEquals("Casa fictícia", place.name)
                assertEquals(-22.0 to -43.0, preview.stage.coordinates.decrypt(place.encryptedCoordinates))
            } finally { staging.discard(preview.stage) }
        } finally { password.fill('\u0000'); staging.discard(source) }
    }
    @Test fun unrecoverablePlaceFailsExportInsteadOfSilentlyLosingCoordinates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = BackupStagingManager(context)
        val source = staging.create(); val unrelatedDevice = staging.create()
        try {
            source.database.placeDao().insert(PlaceEntity(name = "Casa fictícia", type = PlaceType.HOME,
                encryptedCoordinates = unrelatedDevice.coordinates.encrypt(-23.0, -46.0), radiusMeters = 150f, confidence = .9f, createdAt = 1700000000000))
            val repository = BackupRepository(dagger.Lazy { source.database }, dagger.Lazy { source.coordinates }, SettingsRepository(generationSettingsStore(context, source.generation)))
            val output = ByteArrayOutputStream()
            try { BackupExporter(repository).export(output, "hoodie recovery phrase".toCharArray(), true) { }; fail("Lost coordinates must abort export") }
            catch (e: BackupException) { assertEquals(BackupError.COORDINATES, e.error) }
            try { BackupCrypto.read(output.toByteArray().inputStream(), "hoodie recovery phrase".toCharArray()) { }; fail() }
            catch (_: BackupException) { }
        } finally { staging.discard(source); staging.discard(unrelatedDevice) }
    }
    @Test fun frozenFirstVersionBackupRestoresOnIndependentLocalKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val staging = BackupStagingManager(context)
        val preview = InstrumentationRegistry.getInstrumentation().context.assets.open("backup-v1-minimal.hoodiebackup").use {
            BackupImporter(staging).validate(it, "hoodie recovery phrase".toCharArray()) { }
        }
        try {
            preview.stage.database.openHelper.writableDatabase.query("SELECT id, encryptedCoordinates FROM places").use {
                it.moveToFirst(); assertEquals(7L, it.getLong(0)); assertEquals(-23.0 to -46.0, preview.stage.coordinates.decrypt(it.getString(1)))
            }
        } finally { staging.discard(preview.stage) }
    }
}
