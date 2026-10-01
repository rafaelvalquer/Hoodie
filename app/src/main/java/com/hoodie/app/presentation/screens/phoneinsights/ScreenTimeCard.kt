package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.pixel.phoneinsights.HourlyPixelChart
import com.hoodie.app.pixel.phoneinsights.HudPanel
import com.hoodie.app.pixel.phoneinsights.HudStatTile
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Card 1 — placar do dia: tempo de tela grande, contadores e o "equalizador" por hora. */
@Composable
fun ScreenTimeCard(insights: DailyPhoneInsights, zone: ZoneId, modifier: Modifier = Modifier) {
    val s = insights.summary
    HudPanel("Resumo do celular", modifier.fillMaxWidth(), accent = RetroUiTheme.Screen, trailing = if (s.isEstimated) "≈ estimado" else null) {
        SectionLabel("📱 Tempo de tela")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatDuration(s.screenTimeMs), style = RetroFontStyles.HudNumberLarge, color = HoodieColors.Ink)
            if (s.firstUseAt != null && s.lastUseAt != null) {
                Text(
                    "${formatClock(s.firstUseAt, zone)} → ${formatClock(s.lastUseAt, zone)}",
                    style = RetroFontStyles.Small, color = HoodieColors.Muted,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudStatTile("${if (s.isEstimated) "≈" else ""}${s.unlockCount}", "🔓 Desbloq.", RetroUiTheme.Unlock, Modifier.weight(1f))
            HudStatTile("${s.sessionCount}", "Sessões", RetroUiTheme.Sessions, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudStatTile(formatDuration(s.longestSessionMs), "Maior sessão", RetroUiTheme.Longest, Modifier.weight(1f))
            HudStatTile("${insights.appCount}", "📦 Apps", HoodieColors.Hood, Modifier.weight(1f))
        }
        if (insights.hourlyScreenMs.any { it > 0 }) {
            SectionLabel("Uso por hora")
            val peak = insights.hourlyScreenMs.indices.maxByOrNull { insights.hourlyScreenMs[it] }
            HourlyPixelChart(insights.hourlyScreenMs, highlightHour = peak)
            peak?.let { Text("Pico às ${"%02d".format(it)}h", style = RetroFontStyles.Small, color = HoodieColors.Gold) }
        }
    }
}
