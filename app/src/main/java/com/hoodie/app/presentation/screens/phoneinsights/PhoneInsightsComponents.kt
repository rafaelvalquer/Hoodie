package com.hoodie.app.presentation.screens.phoneinsights

import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.CategoryUsageSummary
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.PhoneTimelineItem
import com.hoodie.app.engine.deviceusage.DigitalMood
import com.hoodie.app.engine.deviceusage.DigitalReaction
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.phoneinsights.HudPanel
import com.hoodie.app.pixel.phoneinsights.PixelTag
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.pixel.phoneinsights.RetroUiTheme
import com.hoodie.app.pixel.phoneinsights.StackedPixelBar
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** Card 4 — tempo por categoria: uma barra fatiada + legenda em blocos. */
@Composable
fun CategoryUsageCard(categories: List<CategoryUsageSummary>, modifier: Modifier = Modifier) {
    val uiTextContext = LocalContext.current
    val total = categories.sumOf { it.foregroundMs }.coerceAtLeast(1)
    HudPanel(uiTextContext.getString(R.string.ui_extra_phone_insights_components_1), modifier.fillMaxWidth(), accent = HoodieColors.Coral) {
        if (categories.isEmpty()) {
            Text(stringResource(R.string.ui_phone_insights_components_1), style = RetroFontStyles.Body, color = HoodieColors.Muted)
            return@HudPanel
        }
        StackedPixelBar(categories.map { it.foregroundMs.toFloat() to RetroUiTheme.category(it.category) })
        categories.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(RetroUiTheme.category(c.category)).border(2.dp, HoodieColors.Outline))
                Text("${c.category.emoji} ${c.category.label}", style = RetroFontStyles.Body, color = HoodieColors.Ink, modifier = Modifier.weight(1f))
                Text("${c.foregroundMs * 100 / total}%", style = RetroFontStyles.Small, color = HoodieColors.Muted, modifier = Modifier.width(40.dp))
                Text(formatDuration(c.foregroundMs), style = RetroFontStyles.BodyBold, color = HoodieColors.Ink)
            }
        }
    }
}

/** Card 5 — linha do tempo digital: blocos relevantes de uso, com o contexto em que aconteceram. */
@Composable
fun DigitalTimelineCard(items: List<PhoneTimelineItem>, apps: List<AppUsageEntry>, zone: ZoneId, modifier: Modifier = Modifier) {
    HudPanel("Linha do tempo digital", modifier.fillMaxWidth(), accent = HoodieColors.Hood, trailing = "${items.size} blocos") {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.ui_phone_insights_components_2),
                style = RetroFontStyles.Small, color = HoodieColors.Muted,
            )
        }
        items.forEach { item ->
            val entry = apps.firstOrNull { it.packageName == item.packageName }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatClock(item.startedAt, zone), style = RetroFontStyles.HudLabel, color = HoodieColors.Gold, maxLines = 1, softWrap = false)
                // Trilho vertical em pixels ligando os blocos.
                Box(Modifier.width(4.dp).height(28.dp).background(RetroUiTheme.category(item.category)).border(1.dp, HoodieColors.Outline))
                AppBadge(entry?.iconSource ?: AppIconSource.Installed(item.packageName), item.category, size = 26.dp, showCategoryDot = false)
                Column(Modifier.weight(1f)) {
                    Text("${item.appLabel} · ${formatDuration(item.durationMs)}", style = RetroFontStyles.BodyBold, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatClock(item.startedAt, zone)}–${formatClock(item.endedAt, zone)}" + (item.context?.let { " · ${it.emoji} ${it.label}" } ?: ""),
                        style = RetroFontStyles.Small, color = HoodieColors.Muted, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** O Hoodie comenta o dia digital (com a animação que combina com o humor). */
@Composable
fun HoodieDigitalCard(reaction: DigitalReaction, catName: String, modifier: Modifier = Modifier) {
    PixelPanel(modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedHoodie(moodAnimation(reaction.mood), size = 84.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(catName.uppercase(), style = RetroFontStyles.PanelTitle, color = HoodieColors.Hood)
                SpeechBubble(reaction.text)
            }
        }
    }
}

fun moodAnimation(mood: DigitalMood): AnimationId = when (mood) {
    DigitalMood.CALM -> AnimationId.IDLE_PHONE
    DigitalMood.CURIOUS -> AnimationId.PHONE_REACT_WOW
    DigitalMood.MUSICAL -> AnimationId.IDLE_EAR
    DigitalMood.PLAYFUL -> AnimationId.PHONE_REACT_SMILE
    DigitalMood.BUSY -> AnimationId.PHONE_TYPE
    DigitalMood.TIRED -> AnimationId.YAWN
}

@Composable
fun DigitalLoading(modifier: Modifier = Modifier) {
    PixelPanel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = RetroUiTheme.Screen)
            Text(stringResource(R.string.ui_phone_insights_components_3), style = RetroFontStyles.Body, color = HoodieColors.Muted)
        }
    }
}

/** Estados vazios elegantes: o gato explica o que falta, nunca uma tela em branco. */
@Composable
fun DigitalEmptyState(title: String, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    PixelPanel(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedHoodie(AnimationId.IDLE_PHONE, size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = RetroFontStyles.BodyBold, color = HoodieColors.Hood)
                Text(text, style = RetroFontStyles.Small, color = HoodieColors.Muted)
            }
        }
        if (action != null && onAction != null) PixelButton(action, onAction, Modifier.fillMaxWidth().padding(top = 10.dp), color = RetroUiTheme.Screen)
    }
}

// ── Integração com o Diário (aba Geral) ──

/** Card "Seu celular": resumo compacto que leva para a aba Digital. */
@Composable
fun DiaryPhoneCard(insights: DailyPhoneInsights, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val uiTextContext = LocalContext.current
    val s = insights.summary
    HudPanel(uiTextContext.getString(R.string.ui_extra_phone_insights_components_2), modifier.fillMaxWidth(), accent = RetroUiTheme.Screen, trailing = uiTextContext.getString(R.string.ui_extra_phone_insights_components_3), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_phone_insights_components_4), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
                Text(formatDuration(s.screenTimeMs), style = RetroFontStyles.HudNumber, color = HoodieColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_phone_insights_components_5), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
                Text("${if (s.isEstimated) "≈" else ""}${s.unlockCount}", style = RetroFontStyles.HudNumber, color = HoodieColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_phone_insights_components_6), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
                Text("${insights.appCount}", style = RetroFontStyles.HudNumber, color = HoodieColors.Ink)
            }
        }
        if (insights.topApps.isNotEmpty()) {
            Text(stringResource(R.string.ui_phone_insights_components_7), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                insights.topApps.take(3).forEach { app ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppBadge(app.iconSource, app.appCategory, size = 28.dp, showCategoryDot = false)
                        Column {
                            Text(app.appLabel, style = RetroFontStyles.Small, color = HoodieColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDuration(app.foregroundMs), style = RetroFontStyles.HudLabel, color = HoodieColors.Gold)
                        }
                    }
                }
            }
        }
    }
}

/** "Uso do celular no trabalho": usado no detalhe de um contexto/lugar do Diário. */
@Composable
fun ContextPhoneUsageSection(insights: DailyPhoneInsights?, context: com.hoodie.app.core.model.UserContextType, modifier: Modifier = Modifier) {
    val usage = insights?.usageIn(context)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.phone_usage_context_title, context.label.uppercase()), style = RetroFontStyles.HudLabel, color = HoodieColors.Muted)
        if (usage == null || usage.apps.isEmpty()) {
            Text(stringResource(R.string.ui_phone_insights_components_8), style = RetroFontStyles.Small, color = HoodieColors.Muted)
        } else {
            ContextUsageBlock(usage, 1f, insights?.topApps.orEmpty(), showApps = true, appsShown = 5)
            PixelTag(sessionsLabel(usage.sessionCount), RetroUiTheme.context(context))
        }
    }
}
