package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Persists mobility evidence and privacy-safe speed aggregates without trajectory data. */
object Migration9To10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN lastObservedMovement TEXT")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN lastObservationAt INTEGER")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN lastVehicleAt INTEGER")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN vehicleExitAt INTEGER")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN pendingMovementMode TEXT")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN pendingMovementAt INTEGER")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN speedSampleAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN lastSpeedSampleAt INTEGER")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN speedSampleCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN meanSpeedKmh REAL")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN maxSpeedKmh REAL")
        db.execSQL("ALTER TABLE mobility_sessions ADD COLUMN speedVariation REAL NOT NULL DEFAULT 0")
    }
}
