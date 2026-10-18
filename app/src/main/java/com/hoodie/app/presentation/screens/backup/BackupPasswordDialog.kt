package com.hoodie.app.presentation.screens.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import com.hoodie.app.engine.backup.BackupKeyDerivation

@Composable fun BackupPasswordDialog(export: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray, Boolean) -> Unit) {
    // Deliberately not rememberSaveable: no password in the Activity saved state or disk.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var digital by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (export) "Proteja seu backup" else "Desbloquear backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (export) Text("Inclui lugares, coordenadas, Jornada, atividades, memórias, rotinas, aprendizados e configurações. Use uma senha ou frase com pelo menos 12 caracteres e 5 caracteres diferentes.")
                OutlinedTextField(password, { if (it.length <= 256) password = it }, label = { Text("Senha de recuperação") }, singleLine = true,
                    modifier = Modifier.testTag("backup_password").semantics { contentType = if (export) ContentType.NewPassword else ContentType.Password }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation())
                if (export) OutlinedTextField(confirmation, { if (it.length <= 256) confirmation = it }, label = { Text("Confirme a senha") }, singleLine = true,
                    modifier = Modifier.testTag("backup_password_confirmation").semantics { contentType = ContentType.NewPassword }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation())
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar senha" else "Mostrar senha") }
                if (export) {
                    Row(Modifier.fillMaxWidth().toggleable(value = digital, role = Role.Checkbox, onValueChange = { digital = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(digital, onCheckedChange = null)
                        Text("Incluir histórico digital: aplicativos, sessões e estatísticas já salvas.", Modifier.weight(1f))
                    }
                    Text("Guarde esta senha em um lugar seguro. Se você a esquecer, o Hoodie não conseguirá recuperar este backup.")
                }
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty() && (!export || (password == confirmation && BackupKeyDerivation.isStrong(password.toCharArray()))),
            onClick = { val secret = password.toCharArray(); password = ""; confirmation = ""; onConfirm(secret, digital) }) { Text(if (export) "Escolher onde salvar" else "Validar backup") } },
        dismissButton = { TextButton(onClick = { password = ""; confirmation = ""; onDismiss() }) { Text("Cancelar") } },
    )
}
