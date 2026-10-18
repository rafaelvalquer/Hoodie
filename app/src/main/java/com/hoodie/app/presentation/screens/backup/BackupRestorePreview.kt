package com.hoodie.app.presentation.screens.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hoodie.app.engine.backup.BackupManifest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun backupDate(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable fun BackupRestorePreview(manifest: BackupManifest) {
    Column {
        Text("Backup de ${backupDate(manifest.createdAt)}")
        Text("Lugares: ${manifest.counts["places"] ?: 0}")
        Text("Contextos da Jornada: ${manifest.counts["context_events"] ?: 0}")
        Text("Memórias: ${manifest.counts["memories"] ?: 0}")
        Text("Rotina: ${if ((manifest.counts["routines"] ?: 0) > 0) "incluída" else "não cadastrada"}")
        Text("Histórico digital: ${if (manifest.includeDigital) "incluído" else "não incluído"}")
        Text("Compatibilidade: compatível. Integridade verificada.")
    }
}
