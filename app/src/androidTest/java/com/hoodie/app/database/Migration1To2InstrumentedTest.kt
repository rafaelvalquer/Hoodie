package com.hoodie.app.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.migrations.Migration1To2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migração v1 → v2 no SQLite real do aparelho. */
@RunWith(AndroidJUnit4::class)
class Migration1To2InstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HoodieDatabase::class.java)

    @Test
    fun timelineAntigaSobreviveEGanhaOrigem() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO timeline_events (id, timestamp, actor, emoji, text) VALUES (1, 1000, 'USER', '🏠', 'Casa')")
        }
        helper.runMigrationsAndValidate(DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT text, sourceType FROM timeline_events WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Casa", c.getString(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-instrumented-test.db"
    }
}
