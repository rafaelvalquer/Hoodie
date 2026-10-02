package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserva v4 e adiciona histórico digital completo. Não inventa dados antigos. */
object Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_screen_hourly` (`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `screenMs` INTEGER NOT NULL, PRIMARY KEY(`date`, `hour`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_context_usage` (`date` TEXT NOT NULL, `context` TEXT NOT NULL, `foregroundMs` INTEGER NOT NULL, `sessionCount` INTEGER NOT NULL, PRIMARY KEY(`date`, `context`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_phone_timeline` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `category` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `context` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_phone_timeline_date` ON `daily_phone_timeline` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_phone_timeline_startedAt` ON `daily_phone_timeline` (`startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_events_endedAt_startedAt` ON `context_events` (`endedAt`, `startedAt`)")
    }
}
