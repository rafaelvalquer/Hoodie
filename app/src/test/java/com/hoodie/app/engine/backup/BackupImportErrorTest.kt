package com.hoodie.app.engine.backup

import org.junit.Assert.*
import org.junit.Test

class BackupImportErrorTest {
    @Test fun failuresExposeOnlySafeActionableMessages() {
        assertEquals(BackupError.STORAGE, BackupError.from(java.io.IOException("secret coordinate")))
        assertEquals(BackupError.STORAGE, BackupError.from(SecurityException("private URI")))
        assertEquals(BackupError.CORRUPTED, BackupError.from(BackupException(BackupError.CORRUPTED)))
        assertEquals(BackupError.VALIDATION, BackupError.from(IllegalArgumentException("private SQL")))
        assertFalse(BackupError.from(java.io.IOException("secret coordinate")).message.contains("secret coordinate"))
    }
}
