package com.hoodie.app.presentation.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.engine.backup.*
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel

@Composable fun BackupScreen(onBack: () -> Unit, recovery: Boolean = false, vm: BackupViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = ui.busy || ui.error == BackupError.ACTIVATION) {
        if (ui.state != BackupState.RESTORING && ui.error != BackupError.ACTIVATION) vm.cancel()
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME), vm::destinationSelected)
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::fileSelected)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Dados e segurança", style = MaterialTheme.typography.headlineSmall)
        Text("Seus dados ficam no seu aparelho. Crie um backup protegido para guardar suas memórias e restaurá-las em outro celular.")
        Text("O backup é manual e não exige Conta Google ou internet. Guarde o arquivo e a senha; o Hoodie não consegue recuperar nenhum deles. Se escolher um provedor de nuvem no seletor do Android, o armazenamento será administrado por esse provedor.")
        if (last != null && !recovery) Text("Último backup criado neste aparelho: ${backupDate(last!!)}")
        if (recovery) Text("A chave dos dados antigos pode estar indisponível. A restauração usa a senha do backup e exige sua confirmação antes de substituir os dados.")
        if (ui.error == BackupError.ACTIVATION) {
            Text(BackupError.ACTIVATION.message, color = MaterialTheme.colorScheme.error)
            PixelButton("Reiniciar e recuperar", vm::restart)
        } else if (ui.busy) {
            BackupProgressContent(ui.state)
            if (ui.state != BackupState.RESTORING) PixelButton("Cancelar operação", vm::cancel)
        } else {
            if (!recovery) PixelButton("Criar backup", vm::requestExport, Modifier.fillMaxWidth())
            PixelButton("Restaurar backup", { open.launch(arrayOf(BackupFormat.MIME, "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth())
            Text("A restauração substitui os dados atuais. Escolha um arquivo .hoodiebackup e informe a senha usada na criação.")
            if (ui.state == BackupState.SUCCESS) Text("Backup criado e verificado com sucesso.")
            if (ui.state == BackupState.CANCELLED) Text(BackupError.CANCELLED.message)
            ui.error?.let { error ->
                Text(error.message, color = MaterialTheme.colorScheme.error)
                if (error == BackupError.ACTIVATION) PixelButton("Reiniciar e recuperar", vm::restart)
            }
            ui.preview?.let { preview ->
                PixelPanel(Modifier.fillMaxWidth()) { BackupRestorePreview(preview.manifest) }
                BackupRestoreConfirmation(recovery, vm::requestExport, vm::restoreConfirmed, vm::cancel)
            }
            TextButton(onClick = { vm.cancel(); onBack() }) { Text("Voltar") }
        }
    }
    ui.passwordForExport?.let { export -> BackupPasswordDialog(export, vm::dismissPassword) { password, digital ->
        if (export) {
            vm.exportPassword(password, digital)
            create.launch("Hoodie_Backup_${java.time.LocalDate.now()}${BackupFormat.EXTENSION}")
        } else vm.importPassword(password)
    } }
}
