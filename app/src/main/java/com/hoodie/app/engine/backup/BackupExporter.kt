package com.hoodie.app.engine.backup

import java.io.OutputStream
import javax.inject.Inject

class BackupExporter @Inject constructor(private val repository: BackupRepository) {
    suspend fun export(output: OutputStream, password: CharArray, includeDigital: Boolean, checkCancelled: () -> Unit) {
        BackupCrypto.Writer(output, password).use { writer ->
            repository.snapshot(includeDigital, { json ->
                val bytes = json.toString().toByteArray(Charsets.UTF_8)
                try { writer.record(bytes) } finally { bytes.fill(0) }
            }, checkCancelled)
            checkCancelled()
            writer.finish()
        }
    }
}
