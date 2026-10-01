package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.phoneinsights.HudPanel
import com.hoodie.app.pixel.phoneinsights.PixelTag
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.pixel.phoneinsights.SegmentedPixelBar
import com.hoodie.app.presentation.theme.HoodieColors

/** Card 2 — ranking de apps: ícone real emoldurado, tempo, sessões e barra de RPG. */
@Composable
fun TopAppsCard(apps: List<AppUsageEntry>, onApp: (AppUsageEntry) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = apps.take(if (expanded) HoodieConfig.TOP_APPS_SHOWN else 5)
    val max = apps.firstOrNull()?.foregroundMs?.coerceAtLeast(1) ?: 1
    HudPanel("Top apps", modifier.fillMaxWidth(), accent = HoodieColors.Gold, trailing = "${apps.size} apps") {
        if (apps.isEmpty()) Text("Nenhum app usado neste dia.", style = RetroFontStyles.Body, color = HoodieColors.Muted)
        shown.forEachIndexed { index, app -> AppRow(index + 1, app, app.foregroundMs.toFloat() / max) { onApp(app) } }
        if (apps.size > 5) {
            Text(
                if (expanded) "▲ mostrar menos" else "▼ ver top ${minOf(apps.size, HoodieConfig.TOP_APPS_SHOWN)}",
                style = RetroFontStyles.HudLabel, color = HoodieColors.Blue,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
            )
        }
        Text("Toque num app para ver detalhes e mudar a categoria.", style = RetroFontStyles.Small, color = HoodieColors.Muted)
    }
}

@Composable
fun AppRow(rank: Int, app: AppUsageEntry, fraction: Float, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$rank", style = RetroFontStyles.HudNumber.copy(fontSize = RetroFontStyles.Body.fontSize), color = if (rank == 1) HoodieColors.Gold else HoodieColors.Muted, modifier = Modifier.width(18.dp))
        AppBadge(app.iconSource, app.appCategory)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.appLabel, style = RetroFontStyles.BodyBold, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatDuration(app.foregroundMs), style = RetroFontStyles.BodyBold, color = HoodieColors.Ink)
            }
            SegmentedPixelBar(fraction, RetroUiTheme.category(app.appCategory), segments = 14, height = 9.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelTag("${app.appCategory.emoji} ${app.appCategory.label}", RetroUiTheme.category(app.appCategory))
                Text(sessionsLabel(app.sessionCount), style = RetroFontStyles.Small, color = HoodieColors.Muted)
            }
        }
    }
}

fun sessionsLabel(n: Int) = if (n == 1) "1 sessão" else "$n sessões"
