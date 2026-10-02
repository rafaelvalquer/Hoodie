package com.hoodie.app.presentation.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.database.DatabaseSecurityState
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Tela dedicada quando o banco local não pode ser aberto com segurança. Nunca
 * apaga nada sozinha: apagar exige toque explícito e confirmação.
 */
@Composable
fun DatabaseRecoveryScreen(
    state: DatabaseSecurityState,
    busy: Boolean,
    onRetry: () -> Unit,
    onReset: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val (title, body) = when (state) {
        DatabaseSecurityState.KeyUnrecoverable ->
            "Não foi possível abrir os dados locais" to
                "A chave de segurança deste aparelho não está mais disponível."
        is DatabaseSecurityState.MigrationFailed ->
            "Não foi possível proteger os dados locais" to
                "A atualização para o banco cifrado não terminou. Seus dados continuam guardados como estavam; tente novamente."
        else -> "Preparando os dados locais" to "Um instante…"
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedHoodie(AnimationId.SHRUG, size = 150.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        PixelPanel(Modifier.fillMaxWidth()) {
            Text(body)
            if (state is DatabaseSecurityState.MigrationFailed) {
                Text("\nDetalhe técnico: ${state.reason}", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (busy) CircularProgressIndicator()
        PixelButton("Tentar novamente", onRetry, Modifier.fillMaxWidth(), enabled = !busy)
        PixelButton(
            "Apagar dados locais e começar novamente", { confirm = true }, Modifier.fillMaxWidth(),
            color = HoodieColors.Coral, enabled = !busy,
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar tudo?") },
            text = { Text("Lugares, histórico, memórias e o estado do Hoodie serão apagados deste aparelho. Não dá para desfazer.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Apagar", color = HoodieColors.Coral) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
