package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HOODIE_DATABASE_VERSION
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration7To8Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test fun intelligenceTablesPreserveV7DigitalHistoryAndIndexes() = runBlocking {
        createVersion(7) {
            it.execSQL("INSERT INTO places VALUES (1, 'Casa', 'HOME', 'encrypted', 150, 1, 1, 2, 1, 20)")
            it.execSQL("INSERT INTO context_events VALUES (1, 'HOME', 10, 20, 0.9, 1, 'GEOFENCE')")
            it.execSQL("INSERT INTO mobility_sessions VALUES (1, 10, 20, 1, 1, 'WALKING', 'WALKING', 'ARRIVED', 0.9, 1, 'GEOFENCE', 1, 1, 0, 10, NULL, 0, 0)")
            it.execSQL("INSERT INTO mobility_segments VALUES (1, 1, 'WALKING', 10, 20, 0.9, 1, 'GEOFENCE')")
            it.execSQL("INSERT INTO daily_app_usage VALUES ('2026-10-05', 'com.spotify.music', 'Spotify', 'MUSIC', 1000, 1, NULL, NULL, 1)")
            it.execSQL("INSERT INTO phone_app_sessions VALUES ('old', 20731, 'com.spotify.music', 10, 20)")
            it.execSQL("INSERT INTO daily_context_usage VALUES ('2026-10-05', 'HOME', 1000, 1)")
        }
        val room = openCurrent()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(HOODIE_DATABASE_VERSION, db.version)
            listOf("places", "context_events", "mobility_sessions", "mobility_segments").forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table` WHERE id = 1").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)) }
            }
            listOf("day_state", "diary_corrections", "learned_routine_slots", "transport_patterns").forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table`").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
            }
            room.intelligenceDao().saveDayState(com.hoodie.app.core.database.DayStateEntity(state = "ACTIVE", startedAt = 10, confidence = .9f, reason = "ACTIVE_CONTEXT", provisional = false, updatedAt = 20))
            assertEquals("ACTIVE", room.intelligenceDao().dayState()?.state)
            assertEquals(1000L, room.deviceUsageDao().apps("2026-10-05").single().foregroundMs)
            assertEquals("old", room.deviceUsageDao().sessions(20731).single().id)
            assertEquals(1000L, room.deviceUsageDao().contextTotals("2026-10-05").single().foregroundMs)
            val queries = listOf(
                "SELECT * FROM location_events WHERE placeId = 1 ORDER BY timestamp DESC LIMIT 1" to "index_location_events_placeId_timestamp",
                "SELECT * FROM hoodie_activities WHERE endedAt = 20 ORDER BY startedAt DESC LIMIT 1" to "index_hoodie_activities_endedAt_startedAt",
                "SELECT * FROM timeline_events WHERE sourceType = 'HOODIE_ACTIVITY' AND timestamp >= 0 AND timestamp <= 100" to "index_timeline_events_sourceType_timestamp",
                "SELECT * FROM phone_app_sessions WHERE epochDay = 20731 ORDER BY startedAt" to "index_phone_app_sessions_epochDay_startedAt",
                "SELECT * FROM daily_app_usage WHERE date = '2026-10-05' ORDER BY foregroundMs DESC" to "index_daily_app_usage_date_foregroundMs",
                "SELECT * FROM daily_context_app_usage WHERE date = '2026-10-05' ORDER BY foregroundMs DESC" to "index_daily_context_app_usage_date_foregroundMs",
                "SELECT * FROM daily_phone_timeline WHERE date = '2026-10-05' ORDER BY startedAt, id" to "index_daily_phone_timeline_date_startedAt_id",
            )
            queries.forEach { (sql, index) ->
                val details = db.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }.joinToString("\n")
                }
                assertTrue("$sql must use $index: $details", details.contains(index))
                assertFalse("$sql must not need temporary sorting: $details", details.contains("TEMP B-TREE"))
            }
        } finally { room.close() }
        val reopened = openCurrent()
        try {
            assertEquals("ACTIVE", reopened.intelligenceDao().dayState()?.state)
            assertEquals(20L, reopened.intelligenceDao().dayState()?.updatedAt)
        } finally { reopened.close() }
    }

    @Test fun allHistoricalSchemasReachCurrentVersionWithoutDestructiveFallback() {
        for (version in 1..7) {
            context.deleteDatabase(DB)
            createVersion(version) {}
            val room = openCurrent()
            try { assertEquals("from v$version", HOODIE_DATABASE_VERSION, room.openHelper.writableDatabase.version) }
            finally { room.close() }
        }
    }

    private fun openCurrent() = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
        .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()

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
    private companion object { const val DB = "migration-7-8-test.db" }
}
