package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Keeps the broad diary context while retaining the manually selected shopping venue. */
object Migration8To9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE context_events ADD COLUMN venueType TEXT")
        db.execSQL("ALTER TABLE diary_corrections ADD COLUMN originalVenueType TEXT")
        db.execSQL("ALTER TABLE diary_corrections ADD COLUMN correctedVenueType TEXT")
    }
}
