package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HOODIE_DATABASE_VERSION
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.CorrectionUndoEntity
import com.hoodie.app.core.database.DeliveryStatus
import com.hoodie.app.core.database.NotificationDeliveryEntity
import com.hoodie.app.core.database.UndoStatus
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** v11 → v12 (UX 2.0): cria correction_undo e notification_delivery sem tocar nos dados existentes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration11To12Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test fun v11HistoryIsPreservedAndNewTablesWork() = runBlocking {
        createVersion(11) {
            it.execSQL("INSERT INTO context_events (type, startedAt, endedAt, confidence, placeId, source, venueType) VALUES ('HOME', 10, NULL, 0.9, NULL, 'GEOFENCE', NULL)")
        }
        val room = Room.databaseBuilder(context, HoodieDatabase::class.java, DB).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(HOODIE_DATABASE_VERSION, room.openHelper.writableDatabase.version)
            assertEquals(1, room.contextEventDao().count())
            assertEquals(10L, room.contextEventDao().current()!!.startedAt)
            val undo = room.correctionUndoDao()
            undo.insert(CorrectionUndoEntity(operationId = "op", kind = "FROM_NOW", formatVersion = 1, windowFrom = 10, snapshot = "{}", createdAt = 1, expiresAt = 2, status = UndoStatus.AVAILABLE))
            assertNotNull(undo.byOperation("op"))
            val delivery = room.notificationDeliveryDao()
            val row = NotificationDeliveryEntity(questionId = 5, kind = "QUESTION", createdAt = 1, nextEligibleAt = 1, expiresAt = 9, deliveredAt = null, status = DeliveryStatus.PENDING, attemptCount = 0, dedupeKey = "q5")
            delivery.insertIgnore(row)
            assertEquals(-1L, delivery.insertIgnore(row)) // dedupeKey impede duplicata
            assertEquals(1, delivery.due(5).size)
        } finally { room.close() }
    }

    private fun createVersion(version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/com.hoodie.app.core.database.HoodieDatabase", "$version.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indexes = entity.optJSONArray("indices") ?: continue
                        for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.use(seed)
        helper.close()
    }
    private companion object { const val DB = "migration-11-12-test.db" }
}