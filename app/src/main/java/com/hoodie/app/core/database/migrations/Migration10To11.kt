package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration10To11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mobility_segments ADD COLUMN evidenceId INTEGER")
        db.execSQL("ALTER TABLE mobility_segments ADD COLUMN confirmedAt INTEGER")
        // Historical session speeds cannot be assigned safely to a particular vehicle period.
        db.execSQL("""CREATE TABLE IF NOT EXISTS transport_evidence (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, startedAt INTEGER NOT NULL,
            endedAt INTEGER, attempts INTEGER NOT NULL, lastAttemptAt INTEGER, lastWorkRequestId TEXT, consumedWorkRequestIds TEXT NOT NULL, reservationId TEXT,
            sampleCount INTEGER NOT NULL, meanSpeedKmh REAL, maxSpeedKmh REAL, speedVariation REAL NOT NULL,
            stopStart INTEGER, stopCount INTEGER NOT NULL, stoppedMs INTEGER NOT NULL, stopSquareSum REAL NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES mobility_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transport_evidence_sessionId ON transport_evidence(sessionId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS effective_routine_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL,
            effectiveFrom INTEGER NOT NULL, inputKey TEXT NOT NULL, snapshot TEXT NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_effective_routine_versions_epochDay_effectiveFrom ON effective_routine_versions(epochDay, effectiveFrom)")
    }
}
