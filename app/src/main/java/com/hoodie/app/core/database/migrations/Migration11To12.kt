package com.hoodie.app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** UX 2.0: correções reversíveis (Desfazer) e fila persistente de entrega de notificações. */
object Migration11To12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS correction_undo (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, operationId TEXT NOT NULL, kind TEXT NOT NULL,
            formatVersion INTEGER NOT NULL, windowFrom INTEGER NOT NULL, snapshot TEXT NOT NULL,
            createdAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, status TEXT NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_correction_undo_operationId ON correction_undo(operationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_correction_undo_status ON correction_undo(status)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS notification_delivery (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, questionId INTEGER, kind TEXT NOT NULL, text TEXT NOT NULL,
            createdAt INTEGER NOT NULL, nextEligibleAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL,
            deliveredAt INTEGER, status TEXT NOT NULL, attemptCount INTEGER NOT NULL, dedupeKey TEXT NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_delivery_dedupeKey ON notification_delivery(dedupeKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_delivery_status_nextEligibleAt ON notification_delivery(status, nextEligibleAt)")
    }
}