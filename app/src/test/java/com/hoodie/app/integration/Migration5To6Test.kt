package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.MobilitySegmentEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.database.migrations.Migration5To6
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
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
 * v5 → v6 (Mobilidade): tabelas novas sem tocar no que existe. O banco v5 vem do
 * schema exportado; o Room v6 valida o schema inteiro ao abrir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration5To6Test {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() { context.deleteDatabase(DB) }
    @After fun cleanUp() { context.deleteDatabase(DB) }

    @Test
    fun `cria mobilidade e mantem perguntas antigas`() = runBlocking {
        createV5 { db ->
            db.execSQL("INSERT INTO context_questions (id, kind, candidate, placeId, encryptedCoordinates, chosenPlaceType, contextEventId, askedAt, answeredAt, answer) VALUES (1, 'CONFIRM_CONTEXT', 'WORK', NULL, NULL, NULL, NULL, 1000, NULL, NULL)")
        }
        val room = Room.databaseBuilder(context, HoodieDatabase::class.java, DB).addMigrations(Migration5To6).allowMainThreadQueries().build()
        try {
            assertEquals(6, room.openHelper.writableDatabase.version)
            val q = room.questionDao().getById(1)!!
            assertNull("perguntas antigas não têm deslocamento", q.mobilitySessionId)
            val id = room.mobilitySessionDao().insert(
                MobilitySessionEntity(startedAt = 10, initialMode = MovementMode.WALKING, currentMode = MovementMode.BUS, state = MobilityState.IN_VEHICLE, confidence = 1f, source = MobilitySource.CONFIRMATION),
            )
            room.mobilitySegmentDao().insert(MobilitySegmentEntity(sessionId = id, mode = MovementMode.BUS, startedAt = 10, confidence = 1f, source = MobilitySource.CONFIRMATION))
            assertEquals(MovementMode.BUS, room.mobilitySessionDao().open()!!.currentMode)
            // Apagar o histórico leva os trechos junto (ON DELETE CASCADE).
            room.mobilitySessionDao().clear()
            assertEquals(0, room.mobilitySegmentDao().forSession(id).size)
        } finally {
            room.close()
        }
    }

    private fun createV5(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMAS, "5.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(object : SupportSQLiteOpenHelper.Callback(5) {
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
        const val DB = "migration-5-6-test.db"
        val SCHEMAS = File("schemas/com.hoodie.app.core.database.HoodieDatabase")
    }
}
