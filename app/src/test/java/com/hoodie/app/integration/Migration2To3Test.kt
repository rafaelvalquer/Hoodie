package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.migrations.Migration2To3
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v2 → v3 (Diário Digital). O banco v2 é criado exatamente a partir do schema
 * exportado (schemas/.../2.json) e depois aberto pelo Room v3: o Room compara
 * cada tabela com o schema esperado ao abrir, então qualquer diferença na
 * migração derruba o teste. Não depende de os JSON estarem nos assets de teste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration2To3Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test
    fun `cria tabelas digitais sem tocar no resto`() = runBlocking {
        createV2 { db ->
            db.execSQL("INSERT INTO timeline_events (id, timestamp, actor, emoji, text, sourceType, sourceId) VALUES (1, 1000, 'USER', '🏠', 'Casa', NULL, NULL)")
        }

        val room = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
            .addMigrations(*com.hoodie.app.core.database.migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Abrir dispara as migrações (2→3→4) + validação do schema atual.
            assertEquals(com.hoodie.app.core.database.HOODIE_DATABASE_VERSION, room.openHelper.writableDatabase.version)
            assertEquals("Casa", room.timelineDao().range(0, Long.MAX_VALUE).single().text)

            val dao = room.deviceUsageDao()
            assertNull(dao.day("2026-10-05"))
            room.openHelper.writableDatabase.execSQL(
                "INSERT INTO daily_device_usage (date, screenTimeMs, sessionCount, unlockCount, firstUseAt, lastUseAt, longestSessionMs, isEstimated, appCount, updatedAt) " +
                    "VALUES ('2026-10-05', 1000, 1, 1, NULL, NULL, 1000, 0, 1, 1)",
            )
            room.openHelper.writableDatabase.execSQL("INSERT INTO daily_app_usage VALUES ('2026-10-05', 'com.spotify.music', 'Spotify', 'MUSIC', 1000, 1, NULL, NULL, 1)")
            room.openHelper.writableDatabase.execSQL("INSERT INTO daily_context_app_usage VALUES ('2026-10-05', 'HOME', 'com.spotify.music', 'Spotify', 1000, 1)")
            room.openHelper.writableDatabase.execSQL("INSERT INTO app_category_overrides VALUES ('com.spotify.music', 'WORK', 1)")
            assertEquals(1000L, dao.day("2026-10-05")!!.screenTimeMs)
            assertEquals("Spotify", dao.apps("2026-10-05").single().appLabel)
            assertEquals("HOME", dao.contextApps("2026-10-05").single().context)
            assertTrue(dao.overrides().isNotEmpty())
        } finally {
            room.close()
        }
    }

    /** Banco v2 a partir do JSON exportado pelo Room (tabelas, índices e identity hash). */
    private fun createV2(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMAS, "2.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(2) {
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
        const val DB = "migration-2-3-test.db"
        /** Testes JVM rodam com o diretório do módulo como cwd. */
        val SCHEMAS = File("schemas/com.hoodie.app.core.database.HoodieDatabase")
    }
}
