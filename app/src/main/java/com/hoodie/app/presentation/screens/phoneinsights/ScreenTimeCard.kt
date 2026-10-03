package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
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
    val uiTextContext = LocalContext.current
    val s = insights.summary
    HudPanel(uiTextContext.getString(R.string.ui_extra_screen_time_card_1), modifier.fillMaxWidth(), accent = RetroUiTheme.Screen, trailing = if (s.isEstimated) uiTextContext.getString(R.string.ui_extra_screen_time_card_2) else null) {
        SectionLabel(stringResource(R.string.ui_screen_time_card_1))
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
            HudStatTile("${if (s.isEstimated) "≈" else ""}${s.unlockCount}", stringResource(R.string.phone_unlock_count), RetroUiTheme.Unlock, Modifier.weight(1f))
            HudStatTile("${s.sessionCount}", stringResource(R.string.phone_session_count), RetroUiTheme.Sessions, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudStatTile(formatDuration(s.longestSessionMs), uiTextContext.getString(R.string.ui_extra_screen_time_card_3), RetroUiTheme.Longest, Modifier.weight(1f))
            HudStatTile("${insights.appCount}", stringResource(R.string.phone_app_count), HoodieColors.Hood, Modifier.weight(1f))
        }
        if (insights.hourlyScreenMs.any { it > 0 }) {
            SectionLabel(stringResource(R.string.ui_screen_time_card_2))
            val peak = insights.hourlyScreenMs.indices.maxByOrNull { insights.hourlyScreenMs[it] }
            HourlyPixelChart(insights.hourlyScreenMs, highlightHour = peak)
            peak?.let { Text(stringResource(R.string.phone_peak_hour, it), style = RetroFontStyles.Small, color = HoodieColors.Gold) }
        }
    }
}
