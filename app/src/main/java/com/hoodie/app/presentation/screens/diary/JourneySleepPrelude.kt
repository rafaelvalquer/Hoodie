package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

@Composable
internal fun JourneySleepPrelude(window: DailyActivityWindow, zone: ZoneId) {
    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (window.wakeReason == WakeReason.SCHEDULE_FALLBACK) {
                Text("🌅 Dia considerado iniciado às ${formatClock(window.activeStartAt, zone)}", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Hood)
            } else if (window.sleepBeforeStart != null) {
                Text("🌙 Dormindo até ~${formatClock(window.activeStartAt, zone)}", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Muted)
                Text("🌅 Dia ativo ${formatClock(window.activeStartAt, zone)}", style = MaterialTheme.typography.titleSmall, color = HoodieColors.Hood)
            } else {
                Text("🌅 Dia ativo desde ${formatClock(window.activeStartAt, zone)}", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Hood)
            }
        }
    }
}
