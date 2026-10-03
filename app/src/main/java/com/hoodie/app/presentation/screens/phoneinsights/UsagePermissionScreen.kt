package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    onOpenAppDetails: () -> Unit = {},
) {
    val uiTextContext = LocalContext.current
    var showHelp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showHelp) RestrictedSettingsHelpSheet(onDismiss = { showHelp = false }, onOpenAppDetails = onOpenAppDetails)
    HudPanel(uiTextContext.getString(R.string.ui_extra_usage_permission_screen_1), modifier.fillMaxWidth(), accent = RetroUiTheme.Screen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedHoodie(AnimationId.PHONE_SCROLL, size = 84.dp)
            Text(
                stringResource(R.string.ui_usage_permission_screen_1),
                style = RetroFontStyles.Body, color = HoodieColors.Ink, modifier = Modifier.weight(1f),
            )
        }
        PermissionList(
            uiTextContext.getString(R.string.ui_extra_usage_permission_screen_2), HoodieColors.Mint, "✔",
            listOf(uiTextContext.getString(R.string.ui_extra_usage_permission_screen_3), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_4), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_5), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_6), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_7)),
        )
        PermissionList(
            uiTextContext.getString(R.string.ui_extra_usage_permission_screen_8), HoodieColors.Coral, "✖",
            listOf(uiTextContext.getString(R.string.ui_extra_usage_permission_screen_9), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_10), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_11), uiTextContext.getString(R.string.ui_extra_usage_permission_screen_12)),
        )
        Text(stringResource(R.string.ui_usage_permission_screen_2), style = RetroFontStyles.BodyBold, color = HoodieColors.Gold)
        Text(stringResource(R.string.ui_usage_permission_screen_3), style = RetroFontStyles.Small, color = HoodieColors.Muted)
        if (permission == UsagePermissionState.UNAVAILABLE) {
            Text(stringResource(R.string.ui_usage_permission_screen_4), style = RetroFontStyles.Small, color = HoodieColors.Coral)
        } else {
            PixelButton(stringResource(R.string.ui_usage_permission_screen_5), onActivate, Modifier.fillMaxWidth(), color = RetroUiTheme.Screen)
            Text(stringResource(R.string.ui_usage_permission_screen_6), style = RetroFontStyles.Small, color = HoodieColors.Muted)
            val permissionUi = UsagePermissionUiState(permission)
            Text(androidx.compose.ui.res.stringResource(if (permissionUi.showRestrictedHelp)
                com.hoodie.app.R.string.usage_restricted_hint else com.hoodie.app.R.string.usage_optional_help),
                style = RetroFontStyles.Small, color = HoodieColors.Muted)
            PixelButton(androidx.compose.ui.res.stringResource(com.hoodie.app.R.string.usage_restricted_help),
                { showHelp = true }, Modifier.fillMaxWidth())
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
