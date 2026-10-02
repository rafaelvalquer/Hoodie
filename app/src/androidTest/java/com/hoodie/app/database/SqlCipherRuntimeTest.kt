package com.hoodie.app.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.DailyDeviceUsageEntity
import com.hoodie.app.core.database.DailyAppUsageEntity
import com.hoodie.app.core.database.DailyContextUsageEntity
import com.hoodie.app.core.database.DailyScreenHourlyEntity
import com.hoodie.app.core.database.DailyPhoneTimelineEntity
import com.hoodie.app.core.database.SqlCipherNativeLoader
import com.hoodie.app.core.database.DatabaseEncryption
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import com.hoodie.app.core.model.PlaceType
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Confirma a biblioteca JNI e o ciclo real Room/SQLCipher usado pela aplicação. */
@RunWith(AndroidJUnit4::class)
class SqlCipherRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "sqlcipher-runtime-test.db"

    @Before fun setUp() {
        SqlCipherNativeLoader.ensureLoaded()
        context.deleteDatabase(databaseName)
    }

    @After fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test fun sqlCipherNativeLibraryLoads() {
        SqlCipherNativeLoader.ensureLoaded()
    }

    @Test fun encryptedPlaceCanBeInsertedReadAndReopened() = runBlocking {
        val passphrase = "hoodie-runtime-test".toByteArray(Charsets.UTF_8)
        val first = open(passphrase)
        try {
            val writable = first.openHelper.writableDatabase
            assertTrue(writable.isOpen)
            val id = first.placeDao().insert(
                PlaceEntity(name = "Casa de teste", type = PlaceType.HOME, encryptedCoordinates = "ciphertext-fixture", radiusMeters = 120f, confidence = 1f, createdAt = 1_800_000_000_000L),
            )
            val stored = first.placeDao().getById(id)
            assertNotNull(stored)
            assertEquals("Casa de teste", stored?.name)
            assertEquals(PlaceType.HOME, stored?.type)
        } finally {
            first.close()
        }
        assertFalse("arquivo cifrado não pode ter cabeçalho SQLite puro", DatabaseEncryption.isPlaintext(context.getDatabasePath(databaseName)))

        val reopened = open(passphrase)
        try {
            val places = reopened.placeDao().getAll()
            assertEquals(1, places.size)
            assertEquals("Casa de teste", places.single().name)
        } finally {
            reopened.close()
        }
    }

    @Test fun digitalV5HistorySurvivesEncryptedReopen() = runBlocking {
        val passphrase = "hoodie-v5-runtime-test".toByteArray(Charsets.UTF_8)
        val date = "2026-10-01"
        val first = open(passphrase)
        try {
            assertEquals(5, first.openHelper.writableDatabase.version)
            first.deviceUsageDao().replaceDay(
                DailyDeviceUsageEntity(date, 400, 40, 40, 0, 400, 10, false, 40, 500),
                List(40) { DailyAppUsageEntity(date, "app.$it", "App $it", "OTHER", 10, 1, 0, 10, 500) },
                listOf(DailyContextUsageEntity(date, "WORK", 400, 40)), emptyList(),
                List(24) { DailyScreenHourlyEntity(date, it, if (it == 9) 400 else 0) },
                listOf(DailyPhoneTimelineEntity("block", date, "app.0", "App 0", "OTHER", 10, 20, "WORK")),
            )
        } finally { first.close() }
        assertFalse(DatabaseEncryption.isPlaintext(context.getDatabasePath(databaseName)))
        val reopened = open(passphrase)
        try {
            val dao = reopened.deviceUsageDao()
            assertEquals(40, dao.apps(date).size)
            assertEquals(24, dao.hourly(date).size)
            assertEquals(400L, dao.hourly(date)[9].screenMs)
            assertEquals("WORK", dao.phoneTimeline(date).single().context)
            assertEquals(400L, dao.contextTotals(date).single().foregroundMs)
        } finally { reopened.close() }
    }

    private fun open(passphrase: ByteArray) = Room.databaseBuilder(context, HoodieDatabase::class.java, databaseName)
        .openHelperFactory(SupportOpenHelperFactory(passphrase))
        .addMigrations(*ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()
}
