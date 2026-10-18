package com.hoodie.app.presentation.screens.backup

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import com.hoodie.app.engine.backup.BackupState

@Composable fun BackupProgressContent(state: BackupState) {
    CircularProgressIndicator()
    Text(when (state) {
        BackupState.PREPARING -> "Preparando…"
        BackupState.READING -> "Lendo os dados salvos…"
        BackupState.ENCRYPTING -> "Protegendo seus dados…"
        BackupState.WRITING -> "Salvando o arquivo…"
        BackupState.VERIFYING -> "Verificando o arquivo salvo…"
        BackupState.DECRYPTING -> "Desbloqueando e validando o backup…"
        BackupState.VALIDATING -> "Conferindo os dados…"
        BackupState.RESTORING -> "Restaurando. O Hoodie será reiniciado…"
        else -> "Aguarde…"
    }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
