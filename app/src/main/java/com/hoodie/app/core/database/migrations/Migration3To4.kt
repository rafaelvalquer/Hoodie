package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4: sessões de uso por app (`phone_app_sessions`) para o replay do Diário e o
 * uso do celular por visita. Só pacote e horários. Nada existente muda.
 */
object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `phone_app_sessions` (`id` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`packageName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_app_sessions_epochDay` ON `phone_app_sessions` (`epochDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_app_sessions_packageName` ON `phone_app_sessions` (`packageName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_app_sessions_startedAt` ON `phone_app_sessions` (`startedAt`)")
    }
}
