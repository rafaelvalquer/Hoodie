package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import com.hoodie.app.core.model.TimelineSourceType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Banco v1 (criado a partir de schemas/.../1.json, com dados) aberto pelo Room
 * atual: roda 1→2→… e o Room valida cada tabela contra o schema esperado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration1To2Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test
    fun `timeline antiga sobrevive e ganha origem nula`() = runBlocking {
        createV1 { db ->
            db.execSQL("INSERT INTO timeline_events (id, timestamp, actor, emoji, text) VALUES (1, 1000, 'USER', '🏠', 'Casa')")
            db.execSQL(
                "INSERT INTO context_events (id, type, startedAt, endedAt, confidence, placeId, source) " +
                    "VALUES (1, 'HOME', 1000, NULL, 1.0, NULL, 'ONBOARDING')",
            )
        }
        val room = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val old = room.timelineDao().range(0, Long.MAX_VALUE).single()
            assertEquals("Casa", old.text)
            assertNull(old.sourceType)
            assertNull(old.sourceId)
            assertEquals(1, room.contextEventDao().count())
            room.openHelper.writableDatabase.execSQL(
                "INSERT INTO timeline_events (timestamp, actor, emoji, text, sourceType, sourceId) VALUES (2000, 'USER', '🚌', 'Saiu', 'CONTEXT', 7)",
            )
            assertEquals(1, room.timelineDao().bySource(TimelineSourceType.CONTEXT, 7).size)
        } finally {
            room.close()
        }
    }

    private fun createV1(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMAS, "1.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(1) {
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
        const val DB = "migration-1-2-test.db"
        val SCHEMAS = File("schemas/com.hoodie.app.core.database.HoodieDatabase")
    }
}
