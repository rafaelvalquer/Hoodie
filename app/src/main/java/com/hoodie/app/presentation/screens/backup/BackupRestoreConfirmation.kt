package com.hoodie.app.presentation.screens.backup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoodie.app.presentation.components.PixelButton

@Composable fun BackupRestoreConfirmation(recovery: Boolean, onBackupCurrent: () -> Unit, onRestore: () -> Unit, onCancel: () -> Unit) {
    Text("Restaurar este backup substituirá os dados atuais do Hoodie neste aparelho. Essa ação não pode ser desfeita pelo aplicativo após a conclusão. Recomendamos salvar um backup dos dados atuais antes de continuar.")
    if (!recovery) PixelButton("Criar backup dos dados atuais", onBackupCurrent, Modifier.fillMaxWidth())
    PixelButton("Restaurar e substituir", onRestore, Modifier.fillMaxWidth())
    PixelButton("Cancelar restauração", onCancel, Modifier.fillMaxWidth())
}
