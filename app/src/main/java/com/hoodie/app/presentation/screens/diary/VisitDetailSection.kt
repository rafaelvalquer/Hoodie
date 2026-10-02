package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DiaryVisitDetails
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/**
 *     VISITA 1
 *     08:49–12:16
 *     📱 celular  41 min
 *     Teams      21m
 *     Chrome     12m
 */
@Composable
fun VisitDetailSection(number: Int, details: DiaryVisitDetails, zone: ZoneId, maxApps: Int = 3) {
    val v = details.visit
    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VISITA $number", style = MaterialTheme.typography.labelLarge, color = HoodieColors.Gold, modifier = Modifier.weight(1f))
                Text(formatDuration(v.durationMs), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            }
            Text("${formatClock(v.arrivalAt, zone)}–${v.departureAt?.let { formatClock(it, zone) } ?: "agora"}", style = MaterialTheme.typography.bodyMedium)
            if (details.hoodieActivities.isNotEmpty()) {
                Text("🐱 " + details.hoodieActivities.joinToString(" · ") { "${it.emoji} ${it.label}" }, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Hood)
            }
            val phone = details.phoneUsage
            if (phone == null) {
                Text("📱 sem uso do celular registrado", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            } else {
                Row(Modifier.padding(top = 2.dp)) {
                    Text("📱 celular", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(formatDuration(phone.foregroundMs), style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink)
                }
                phone.apps.take(maxApps).forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppBadge(AppIconSource.Installed(app.packageName), app.category, size = 18.dp, showCategoryDot = false)
                        Text(app.appLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(formatDuration(app.foregroundMs), modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
                    }
                }
            }
        }
    }
}
