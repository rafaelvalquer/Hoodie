package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3: Phone Insights (Diário Digital). Quatro tabelas novas só com agregados
 * por dia e as categorias escolhidas pelo usuário. Nada existente muda.
 */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_device_usage` (`date` TEXT NOT NULL, `screenTimeMs` INTEGER NOT NULL, " +
                "`sessionCount` INTEGER NOT NULL, `unlockCount` INTEGER NOT NULL, `firstUseAt` INTEGER, `lastUseAt` INTEGER, " +
                "`longestSessionMs` INTEGER NOT NULL, `isEstimated` INTEGER NOT NULL, `appCount` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_app_usage` (`date` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                "`appLabel` TEXT NOT NULL, `appCategory` TEXT NOT NULL, `foregroundMs` INTEGER NOT NULL, " +
                "`sessionCount` INTEGER NOT NULL, `firstUsedAt` INTEGER, `lastUsedAt` INTEGER, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`, `packageName`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_context_app_usage` (`date` TEXT NOT NULL, `context` TEXT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `foregroundMs` INTEGER NOT NULL, " +
                "`sessionCount` INTEGER NOT NULL, PRIMARY KEY(`date`, `context`, `packageName`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_category_overrides` (`packageName` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
        )
    }
}
