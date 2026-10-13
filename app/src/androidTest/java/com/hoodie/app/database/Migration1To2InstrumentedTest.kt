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
    @Test fun migration10To11PreservesTripsCorrectionsAndManualConfiguration() {
        val name = "migration-10-11-instrumented.db"
        helper.createDatabase(name, 10).use { db ->
            db.execSQL("INSERT INTO routines VALUES (1, 'OFFICE', 31, 510, 1050, 720, 780, 1000)")
            db.execSQL("INSERT INTO mobility_sessions (id, startedAt, initialMode, currentMode, state, confidence, confirmed, source, leftOrigin, arrivalAutoConfirmed, pendingModeQuestion, questionsAsked, speedSampleAttempts, speedSampleCount, meanSpeedKmh) VALUES (31, 1000, 'BUS', 'BUS', 'IN_VEHICLE', 1, 1, 'USER_CORRECTION', 1, 0, 0, 0, 5, 4, 45)")
            db.execSQL("INSERT INTO mobility_segments (id, sessionId, mode, startedAt, confidence, confirmed, source) VALUES (32, 31, 'BUS', 1000, 1, 1, 'USER_CORRECTION')")
            db.execSQL("INSERT INTO diary_corrections (id, targetType, targetId, originalMode, correctedMode, originalSource, originalConfidence, createdAt) VALUES (33, 'MOBILITY_SEGMENT', 32, 'CAR', 'BUS', 'ACTIVITY_RECOGNITION', 0.6, 2000)")
        }
        helper.runMigrationsAndValidate(name, 11, true, *com.hoodie.app.core.database.migrations.ALL_MIGRATIONS).use { db ->
            db.query("SELECT speedSampleCount FROM mobility_sessions WHERE id = 31").use { c -> assertTrue(c.moveToFirst()); assertEquals(4, c.getInt(0)) }
            db.query("SELECT source, confirmedAt, evidenceId FROM mobility_segments WHERE id = 32").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("USER_CORRECTION", c.getString(0)); assertTrue(c.isNull(1)); assertTrue(c.isNull(2))
            }
            db.query("SELECT COUNT(*) FROM transport_evidence").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            db.query("SELECT startMinute FROM routines WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertEquals(510, c.getInt(0)) }
            db.query("SELECT correctedMode FROM diary_corrections WHERE id = 33").use { c -> assertTrue(c.moveToFirst()); assertEquals("BUS", c.getString(0)) }
        }
    }

    @Test fun allHistoricalVersionsReach11OnDevice() {
        for (version in 1..10) {
            val name = "migration-chain-$version-instrumented.db"
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, 11, true, *com.hoodie.app.core.database.migrations.ALL_MIGRATIONS).use { db ->
                assertEquals(11, db.version)
            }
        }
    }

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
