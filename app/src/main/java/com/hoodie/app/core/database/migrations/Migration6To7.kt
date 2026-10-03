package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v7 adds indexes for daily reads, rankings and chronological history without rewriting rows. */
object Migration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_events_placeId_timestamp` ON `location_events` (`placeId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hoodie_activities_endedAt_startedAt` ON `hoodie_activities` (`endedAt`, `startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_timeline_events_sourceType_timestamp` ON `timeline_events` (`sourceType`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_app_sessions_epochDay_startedAt` ON `phone_app_sessions` (`epochDay`, `startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_date_foregroundMs` ON `daily_app_usage` (`date`, `foregroundMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_context_app_usage_date_foregroundMs` ON `daily_context_app_usage` (`date`, `foregroundMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_phone_timeline_date_startedAt_id` ON `daily_phone_timeline` (`date`, `startedAt`, `id`)")
    }
}
