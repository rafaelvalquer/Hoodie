package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v6: Mobilidade Contextual. Duas tabelas novas — deslocamentos e seus trechos —
 * só com lugares conhecidos, horários e modos. Nenhuma coordenada. Nada existente muda.
 */
object Migration5To6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `mobility_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, `originPlaceId` INTEGER, `destinationPlaceId` INTEGER, `initialMode` TEXT NOT NULL, `currentMode` TEXT NOT NULL, " +
                "`state` TEXT NOT NULL, `confidence` REAL NOT NULL, `confirmed` INTEGER NOT NULL, `source` TEXT NOT NULL, `leftOrigin` INTEGER NOT NULL, " +
                "`arrivalConfirmed` INTEGER, `arrivalAutoConfirmed` INTEGER NOT NULL, `movingSince` INTEGER, `stillSince` INTEGER, `pendingModeQuestion` INTEGER NOT NULL, " +
                "`questionsAsked` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobility_sessions_startedAt` ON `mobility_sessions` (`startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobility_sessions_endedAt` ON `mobility_sessions` (`endedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `mobility_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`mode` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `confidence` REAL NOT NULL, `confirmed` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `mobility_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobility_segments_sessionId` ON `mobility_segments` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobility_segments_startedAt` ON `mobility_segments` (`startedAt`)")
        // Perguntas de mobilidade apontam para o deslocamento (as antigas ficam null).
        db.execSQL("ALTER TABLE `context_questions` ADD COLUMN `mobilitySessionId` INTEGER")
    }
}
