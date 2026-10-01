package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.phoneinsights.HudPanel
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * "Análise do celular": explica, antes de mandar o usuário para a tela do
 * sistema, o que o Hoodie vê, o que não vê e onde tudo fica.
 */
@Composable
fun UsagePermissionScreen(
    permission: UsagePermissionState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel("Análise do celular", modifier.fillMaxWidth(), accent = RetroUiTheme.Screen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedHoodie(AnimationId.PHONE_SCROLL, size = 84.dp)
            Text(
                "O Hoodie pode usar os dados de uso do Android para montar seu diário digital.",
                style = RetroFontStyles.Body, color = HoodieColors.Ink, modifier = Modifier.weight(1f),
            )
        }
        PermissionList(
            "Podemos ver", HoodieColors.Mint, "✔",
            listOf("tempo de tela", "apps usados", "tempo por app", "sessões", "desbloqueios"),
        )
        PermissionList(
            "Não vemos", HoodieColors.Coral, "✖",
            listOf("mensagens", "texto digitado", "fotos", "conteúdo da tela"),
        )
        Text("🔒 Tudo fica salvo neste aparelho. Nada vai para servidor ou nuvem.", style = RetroFontStyles.BodyBold, color = HoodieColors.Gold)
        Text("Você pode desligar em Ajustes e apagar o histórico digital quando quiser.", style = RetroFontStyles.Small, color = HoodieColors.Muted)
        if (permission == UsagePermissionState.UNAVAILABLE) {
            Text("Este aparelho não oferece a tela de “Acesso ao uso”.", style = RetroFontStyles.Small, color = HoodieColors.Coral)
        } else {
            PixelButton("Ativar acesso", onActivate, Modifier.fillMaxWidth(), color = RetroUiTheme.Screen)
            Text("Na tela do Android, procure “Hoodie” e ligue “Permitir acesso ao uso”.", style = RetroFontStyles.Small, color = HoodieColors.Muted)
        }
    }
}

@Composable
private fun PermissionList(title: String, color: androidx.compose.ui.graphics.Color, mark: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title.uppercase(), style = RetroFontStyles.HudLabel, color = color)
        items.forEach { Text("$mark $it", style = RetroFontStyles.Body, color = HoodieColors.Ink) }
    }
}
