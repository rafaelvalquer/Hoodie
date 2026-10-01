package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.ContextAppUsage
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.engine.deviceusage.AppCategoryResolver
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Detalhe de um app: números do dia, onde foi usado e troca manual de categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsBottomSheet(
    app: AppUsageEntry,
    byContext: List<ContextAppUsage>,
    zone: ZoneId,
    onCategory: (HoodieAppCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HoodieColors.Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppBadge(app.iconSource, app.appCategory, size = 56.dp)
                Column {
                    Text(app.appLabel, style = RetroFontStyles.HudNumber, color = HoodieColors.Hood)
                    Text(app.packageName, style = RetroFontStyles.Small, color = HoodieColors.Muted, maxLines = 1)
                }
            }
            Detail("Tempo no dia", formatDuration(app.foregroundMs))
            Detail("Sessões", app.sessionCount.toString())
            app.firstUsedAt?.let { Detail("Primeiro uso", formatClock(it, zone)) }
            app.lastUsedAt?.let { Detail("Último uso", formatClock(it, zone)) }
            if (byContext.isNotEmpty()) {
                Text("ONDE FOI USADO", style = RetroFontStyles.HudLabel, color = HoodieColors.Muted, modifier = Modifier.padding(top = 4.dp))
                byContext.sortedByDescending { it.foregroundMs }.forEach { Detail("${it.context.emoji} ${it.context.label}", formatDuration(it.foregroundMs)) }
            }
            Text("CATEGORIA", style = RetroFontStyles.HudLabel, color = HoodieColors.Muted, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HoodieAppCategory.entries.forEach { c ->
                    val on = c == app.appCategory
                    Box(
                        Modifier
                            .background(if (on) RetroUiTheme.category(c) else HoodieColors.PanelLight)
                            .border(2.dp, HoodieColors.Outline)
                            .clickable { onCategory(c) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text("${c.emoji} ${c.label}", style = RetroFontStyles.Small, color = if (on) HoodieColors.Outline else HoodieColors.Ink)
                    }
                }
            }
            val auto = AppCategoryResolver.known(app.packageName)
            Text(
                "↺ Usar categoria automática" + (auto?.let { " (${it.label})" } ?: ""),
                style = RetroFontStyles.Small, color = HoodieColors.Blue,
                modifier = Modifier.clickable { onCategory(null) }.padding(vertical = 6.dp),
            )
            Text("Só o nome do app e o tempo de uso são registrados — nunca o que aparece na tela.", style = RetroFontStyles.Small, color = HoodieColors.Muted)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = RetroFontStyles.Body, color = HoodieColors.Muted)
        Text(value, style = RetroFontStyles.BodyBold, color = HoodieColors.Ink)
    }
}
