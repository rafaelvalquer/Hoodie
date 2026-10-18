package com.hoodie.app.engine.backup

import androidx.sqlite.db.SupportSQLiteDatabase

object BackupValidator {
    fun validateDatabase(db: SupportSQLiteDatabase) {
        db.query("PRAGMA integrity_check").use { requireBackup(it.moveToFirst() && it.getString(0) == "ok") }
        db.query("PRAGMA cipher_integrity_check").use {
            // SQLCipher returns no rows on success; some supported versions return 'ok'.
            while (it.moveToNext()) requireBackup(it.getString(0) == "ok")
        }
        db.query("PRAGMA foreign_key_check").use { requireBackup(!it.moveToFirst()) }
        fun references(table: String, column: String, parent: String, key: String = "id") {
            db.query("SELECT 1 FROM `$table` c LEFT JOIN `$parent` p ON c.`$column` = p.`$key` WHERE c.`$column` IS NOT NULL AND p.`$key` IS NULL LIMIT 1")
                .use { requireBackup(!it.moveToFirst()) }
        }
        references("mobility_segments", "sessionId", "mobility_sessions")
        references("transport_evidence", "sessionId", "mobility_sessions")
        references("mobility_segments", "evidenceId", "transport_evidence")
        // Historic place references can outlive a deleted place in the existing model. Preserve them;
        // an explicit SQL foreign key remains mandatory, and no new active state is reconstructed from them.
        db.query("SELECT 1 FROM mobility_sessions WHERE endedAt IS NULL UNION ALL SELECT 1 FROM context_events WHERE endedAt IS NULL LIMIT 1")
            .use { requireBackup(!it.moveToFirst()) }
    }
}
