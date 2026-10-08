package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2: a timeline passa a saber de onde veio cada linha (sourceType/sourceId).
 * Linhas antigas ficam com origem nula — continuam visíveis, só não podem ser
 * invalidadas automaticamente.
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE timeline_events ADD COLUMN sourceType TEXT")
        db.execSQL("ALTER TABLE timeline_events ADD COLUMN sourceId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_timeline_events_sourceType_sourceId ON timeline_events (sourceType, sourceId)")
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8)
