package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
internal fun HomeNowCard(
    snapshot: HomeNowSnapshot?, now: Long, zone: ZoneId,
    onConfirm: (Long) -> Unit, onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot == null) return
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
    val title = when (snapshot.status) {
        HomeNowStatus.UNKNOWN -> stringResource(R.string.home_now_unknown)
        HomeNowStatus.UNAVAILABLE -> stringResource(R.string.home_now_location_unavailable)
        HomeNowStatus.PROBABLE -> stringResource(R.string.home_now_probable_context, contextTitle.lowercase())
        HomeNowStatus.CONFIRMED -> contextTitle
    }
    val heading = stringResource(R.string.home_now_title)
    PixelPanel(modifier.fillMaxWidth().semantics { contentDescription = "$heading: $title" }, color = HoodieColors.PanelLight) {
        SectionLabel(heading)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood, modifier = Modifier.weight(1f))
            snapshot.contextConfidence?.let { score ->
                if (context != null && snapshot.status != HomeNowStatus.UNAVAILABLE) Text("${(score.value * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (snapshot.status == HomeNowStatus.PROBABLE) Text(stringResource(R.string.home_now_probable), color = HoodieColors.Muted)
        if (snapshot.status == HomeNowStatus.CONFIRMED) Text(stringResource(R.string.home_now_confirmed), color = HoodieColors.Muted)
        if (snapshot.placeName != null && snapshot.placeName != context?.label) Text(snapshot.placeName, style = MaterialTheme.typography.bodyMedium)
        snapshot.contextStartedAt?.let { start ->
            Text(stringResource(R.string.home_now_since, formatClock(start, zone)), color = HoodieColors.Muted)
            Text(stringResource(R.string.home_now_duration, formatDuration((now - start).coerceAtLeast(0))), color = HoodieColors.Muted)
        }
        snapshot.wakeAt?.let { wake ->
            Text(stringResource(if (snapshot.wakeConfidence == com.hoodie.app.domain.daycycle.WakeConfidence.HIGH) R.string.home_now_woke_at else R.string.home_now_wake_estimated, formatClock(wake, zone)), color = HoodieColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val eventId = snapshot.contextEventId
            if (eventId != null && snapshot.status == HomeNowStatus.PROBABLE) PixelButton(stringResource(R.string.home_now_confirm), { onConfirm(eventId) }, Modifier.weight(1f))
            PixelButton(stringResource(R.string.home_now_correct), onCorrect, Modifier.weight(1f), color = HoodieColors.Gold)
        }
    }
}
