package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HoodieDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Cria v4 a partir do schema exportado e migra para v5. Room valida o schema
 * completo; agregados e sessões antigos são preservados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration4To5Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test
    fun `adiciona historico completo sem perder os dados v4`() = runBlocking {
        createV4 { db ->
            db.execSQL(
                "INSERT INTO daily_device_usage (date, screenTimeMs, sessionCount, unlockCount, firstUseAt, lastUseAt, longestSessionMs, isEstimated, appCount, updatedAt) " +
                    "VALUES ('2026-10-05', 1000, 1, 1, NULL, NULL, 1000, 0, 1, 1)",
            )
            db.execSQL("INSERT INTO daily_app_usage VALUES ('2026-10-05', 'com.spotify.music', 'Spotify', 'MUSIC', 1000, 1, NULL, NULL, 1)")
            db.execSQL("INSERT INTO phone_app_sessions VALUES ('old', 20731, 'com.spotify.music', 10, 20)")
        }
        val room = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
            .addMigrations(*com.hoodie.app.core.database.migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(com.hoodie.app.core.database.HOODIE_DATABASE_VERSION, room.openHelper.writableDatabase.version)
            val dao = room.deviceUsageDao()
            assertEquals(1000L, dao.day("2026-10-05")!!.screenTimeMs)
            assertEquals(1, dao.sessionCount())
            assertEquals(0, dao.hourly("2026-10-05").size)
            assertEquals(0, dao.phoneTimeline("2026-10-05").size)
            assertEquals(0, dao.contextTotals("2026-10-05").size)
            dao.clearSessions()
            val day = java.time.LocalDate.parse("2026-10-05").toEpochDay()
            dao.insertSessions(listOf(com.hoodie.app.core.database.PhoneAppSessionEntity("com.spotify.music@10", day, "com.spotify.music", 10, 20)))
            assertEquals("com.spotify.music", dao.sessions(day).single().packageName)
            dao.clearHistory()
            assertEquals(0, dao.sessionCount())
        } finally {
            room.close()
        }
    }

    /** Banco v4 a partir do JSON exportado pelo Room (tabelas, índices e identity hash). */
    private fun createV4(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMAS, "4.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = e.optJSONArray("indices") ?: continue
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.use { seed(it) }
        helper.close()
    }

    private companion object {
        const val DB = "migration-4-5-test.db"
        /** Testes JVM rodam com o diretório do módulo como cwd. */
        val SCHEMAS = File("schemas/com.hoodie.app.core.database.HoodieDatabase")
    }
}
