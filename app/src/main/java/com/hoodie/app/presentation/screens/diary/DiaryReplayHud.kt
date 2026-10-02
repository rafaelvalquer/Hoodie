package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.domain.diary.model.ReplayVisualState
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Linhas do HUD (texto puro, testável): horário, contexto, Hoodie e app ativo. */
data class ReplayHudLines(val clock: String, val context: String?, val hoodie: String?, val app: String?)

object ReplayHudText {
    fun contextLabel(c: UserContextType): String = "${c.emoji} ${c.label}"

    fun lines(v: ReplayVisualState, zone: ZoneId): ReplayHudLines? {
        val ts = v.timestamp ?: return null
        return ReplayHudLines(
            clock = formatClock(ts, zone),
            context = v.context?.let(::contextLabel),
            hoodie = v.hoodieActivity?.let { "🐱 Hoodie: ${it.label.lowercase()}" },
            app = v.activePhoneApp?.let { "${it.appLabel} · ${it.category.label.lowercase()}" },
        )
    }
}

/**
 *     ┌──────────────────────────────┐
 *     │ 10:37                        │
 *     │ 🏢 Trabalho                  │
 *     │ 🐱 Hoodie: trabalhando       │
 *     │ [ícone] Spotify · música     │
 *     └──────────────────────────────┘
 */
@Composable
fun DiaryReplayHud(visual: ReplayVisualState, zone: ZoneId, modifier: Modifier = Modifier) {
    val lines = ReplayHudText.lines(visual, zone) ?: return
    PixelPanel(modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lines.clock, style = MaterialTheme.typography.titleLarge, color = HoodieColors.Gold)
            lines.context?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink) }
            lines.hoodie?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Hood) }
            val app = visual.activePhoneApp
            if (app != null && lines.app != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppBadge(AppIconSource.Installed(app.packageName), app.category, size = 18.dp, showCategoryDot = false)
                    Text(lines.app, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Ink)
                }
            } else {
                Text(stringResource(R.string.ui_diary_replay_hud_1), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.size(width = 200.dp, height = 18.dp))
            }
        }
    }
}
