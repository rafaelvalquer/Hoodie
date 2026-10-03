package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.ContextUsageSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.phoneinsights.HudPanel
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.pixel.phoneinsights.SegmentedPixelBar
import com.hoodie.app.presentation.theme.HoodieColors

/** Card 3 — onde o celular foi usado: Casa, Trabalho, Transporte, Almoço, Academia... */
@Composable
fun UsageByContextCard(
    usage: List<ContextUsageSummary>,
    apps: List<AppUsageEntry>,
    showApps: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiTextContext = LocalContext.current
    val max = usage.maxOfOrNull { it.foregroundMs }?.coerceAtLeast(1) ?: 1
    HudPanel(uiTextContext.getString(R.string.ui_extra_usage_by_context_card_1), modifier.fillMaxWidth(), accent = HoodieColors.Mint) {
        if (usage.isEmpty()) {
            Text(stringResource(R.string.ui_usage_by_context_card_1), style = RetroFontStyles.Body, color = HoodieColors.Muted)
        }
        usage.forEach { ctx ->
            ContextUsageBlock(ctx, ctx.foregroundMs.toFloat() / max, apps, showApps)
        }
    }
}

@Composable
fun ContextUsageBlock(ctx: ContextUsageSummary, fraction: Float, apps: List<AppUsageEntry>, showApps: Boolean, appsShown: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${ctx.context.emoji} ${ctx.context.label}", style = RetroFontStyles.BodyBold, color = HoodieColors.Ink, modifier = Modifier.weight(1f))
            Text(formatDuration(ctx.foregroundMs), style = RetroFontStyles.BodyBold, color = HoodieColors.Ink)
        }
        SegmentedPixelBar(fraction, RetroUiTheme.context(ctx.context), segments = 18, height = 10.dp)
        if (showApps) {
            ctx.apps.take(appsShown).forEach { app ->
                val entry = apps.firstOrNull { it.packageName == app.packageName }
                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppBadge(entry?.iconSource ?: AppIconSource.Installed(app.packageName), entry?.appCategory ?: HoodieAppCategory.OTHER, size = 22.dp, showCategoryDot = false)
                    Text(app.appLabel, style = RetroFontStyles.Small, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(formatDuration(app.foregroundMs), style = RetroFontStyles.Small, color = HoodieColors.Muted)
                }
            }
        }
    }
}
