package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Bloco "Hoodie · Agora": em destaque o que o Hoodie está fazendo (lendo, jogando…) e, logo abaixo,
 * o contexto do usuário ("Você"), com a confiança e os horários. Cada parte aparece só se existir.
 */
@Composable
internal fun HomeNowCard(
    snapshot: HomeNowSnapshot?, now: Long, zone: ZoneId,
    modifier: Modifier = Modifier,
    activity: HoodieActivity? = null, activityStartedAt: Long? = null, catName: String = "Hoodie",
) {
    if (snapshot == null && activity == null) return
    val heading = stringResource(R.string.home_now_title)
    val activityTitle = activity?.let { stringResource(R.string.home_activity, catName, it.label) }
    val title = snapshot?.let { userContextTitle(it) }
    val description = when {
        activityTitle == null -> "$heading: $title"
        title == null -> "$heading: $activityTitle"
        else -> "$heading: $activityTitle. ${stringResource(R.string.home_now_you)}: $title"
    }
    PixelPanel(modifier.fillMaxWidth().testTag("home_now_card").semantics { contentDescription = description }, color = HoodieColors.PanelLight) {
        SectionLabel(heading)
        if (activityTitle != null) Column(Modifier.testTag("home_hoodie_activity")) {
            Text(activityTitle, style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
            activityStartedAt?.let { start ->
                Text(stringResource(R.string.home_activity_since, formatClock(start, zone), formatDuration((now - start).coerceAtLeast(0))), color = HoodieColors.Muted)
            }
        }
        if (snapshot != null && title != null) {
            if (activityTitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.home_now_you).uppercase(), style = MaterialTheme.typography.labelMedium, color = HoodieColors.Muted)
            }
            UserContextDetails(snapshot, title, emphasized = activityTitle == null, now, zone)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun userContextTitle(snapshot: HomeNowSnapshot): String {
    val context = snapshot.context
    val contextTitle = when {
        snapshot.dayState?.state == DayState.SLEEPING && context == com.hoodie.app.core.model.UserContextType.HOME -> stringResource(R.string.home_now_resting)
        snapshot.dayState?.state == DayState.WAKING && context == com.hoodie.app.core.model.UserContextType.HOME -> stringResource(R.string.home_now_waking)
        snapshot.dayState?.state == DayState.COMMUTING -> stringResource(R.string.home_now_commuting)
        snapshot.dayState?.state == DayState.WINDING_DOWN && context == com.hoodie.app.core.model.UserContextType.HOME -> stringResource(R.string.home_now_winding_down)
        context == com.hoodie.app.core.model.UserContextType.WORK -> stringResource(R.string.home_now_working)
        context == com.hoodie.app.core.model.UserContextType.LUNCH || context == com.hoodie.app.core.model.UserContextType.DINING -> stringResource(R.string.home_now_lunch)
        context == com.hoodie.app.core.model.UserContextType.GYM -> stringResource(R.string.home_now_gym)
        context == null -> if (snapshot.status == HomeNowStatus.UNAVAILABLE) stringResource(R.string.home_now_location_unavailable) else stringResource(R.string.home_now_unknown)
        context == com.hoodie.app.core.model.UserContextType.HOME -> stringResource(R.string.home_now_home)
        else -> context.label
    }
    return when (snapshot.status) {
        HomeNowStatus.UNKNOWN -> stringResource(R.string.home_now_unknown)
        HomeNowStatus.UNAVAILABLE -> stringResource(R.string.home_now_location_unavailable)
        HomeNowStatus.PROBABLE -> stringResource(R.string.home_now_probable_context, contextTitle.lowercase())
        HomeNowStatus.IDENTIFIED -> contextTitle
        HomeNowStatus.CONFIRMED -> contextTitle
    }
}

@Composable
private fun UserContextDetails(snapshot: HomeNowSnapshot, title: String, emphasized: Boolean, now: Long, zone: ZoneId) {
    val context = snapshot.context
    Column {
        Text(title, style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
        if (snapshot.status == HomeNowStatus.IDENTIFIED) Text(stringResource(R.string.home_now_identified), color = HoodieColors.Muted)
        if (snapshot.status == HomeNowStatus.PROBABLE) Text(stringResource(R.string.home_now_probable), color = HoodieColors.Muted)
        if (snapshot.status in setOf(HomeNowStatus.PROBABLE, HomeNowStatus.IDENTIFIED)) {
            snapshot.contextConfidence?.let { score -> Text(stringResource(R.string.home_now_confidence, (score.value * 100).roundToInt()), color = HoodieColors.Muted) }
        }
        if (snapshot.status == HomeNowStatus.CONFIRMED) Text(stringResource(R.string.home_now_confirmed), color = HoodieColors.Muted)
        if (snapshot.status in setOf(HomeNowStatus.CONFIRMED, HomeNowStatus.IDENTIFIED, HomeNowStatus.PROBABLE) &&
            snapshot.placeName != null && snapshot.placeName != context?.label
        ) Text(snapshot.placeName, style = MaterialTheme.typography.bodyMedium)
        snapshot.contextStartedAt?.takeIf { snapshot.status in setOf(HomeNowStatus.CONFIRMED, HomeNowStatus.IDENTIFIED, HomeNowStatus.PROBABLE) }?.let { start ->
            Text(stringResource(R.string.home_now_since, formatClock(start, zone)), color = HoodieColors.Muted)
            Text(stringResource(R.string.home_now_duration, formatDuration((now - start).coerceAtLeast(0))), color = HoodieColors.Muted)
        }
        snapshot.wakeAt?.let { wake ->
            Text(stringResource(if (snapshot.wakeConfidence == com.hoodie.app.domain.daycycle.WakeConfidence.HIGH) R.string.home_now_woke_at else R.string.home_now_wake_estimated, formatClock(wake, zone)), color = HoodieColors.Muted)
        }
    }
}
