package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add intelligence snapshots and feedback without rewriting any canonical history. */
object Migration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `day_state` (`id` INTEGER NOT NULL, `state` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `confidence` REAL NOT NULL, `reason` TEXT NOT NULL, `provisional` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `diary_corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `targetType` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `originalContext` TEXT, `correctedContext` TEXT, `originalPlaceId` INTEGER, `correctedPlaceId` INTEGER, `originalStartAt` INTEGER, `correctedStartAt` INTEGER, `originalEndAt` INTEGER, `correctedEndAt` INTEGER, `originalMode` TEXT, `correctedMode` TEXT, `originalSource` TEXT NOT NULL, `originalConfidence` REAL NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_corrections_createdAt` ON `diary_corrections` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_corrections_targetType_targetId` ON `diary_corrections` (`targetType`, `targetId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `learned_routine_slots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayGroup` TEXT NOT NULL, `type` TEXT NOT NULL, `medianMinute` INTEGER NOT NULL, `deviationMinutes` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, `confidence` REAL NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_learned_routine_slots_dayGroup_type` ON `learned_routine_slots` (`dayGroup`, `type`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `transport_patterns` (`originPlaceId` INTEGER NOT NULL, `destinationPlaceId` INTEGER NOT NULL, `dayGroup` TEXT NOT NULL, `timeBucket` INTEGER NOT NULL, `mode` TEXT NOT NULL, `confirmations` INTEGER NOT NULL, `rejections` INTEGER NOT NULL, `confidence` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`originPlaceId`, `destinationPlaceId`, `dayGroup`, `timeBucket`, `mode`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transport_patterns_updatedAt` ON `transport_patterns` (`updatedAt`)")
    }
}
