package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Onde o usuário está, desde quando e com que confiança. Toque na confiança mostra de onde veio a
 * identificação (sem falar de algoritmos). Sem contexto, diz que ainda está entendendo, sem inventar local.
 */
@Composable
internal fun UserContextCard(
    snapshot: HomeNowSnapshot?, now: Long, zone: ZoneId, modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    var explain by remember { mutableStateOf(false) }
    PixelPanel(modifier.fillMaxWidth().testTag("home_user_context"), color = HoodieColors.PanelLight) {
        SectionLabel(stringResource(R.string.home_now_you))
        if (snapshot == null) {
            Text(stringResource(R.string.home_now_unknown), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
        } else {
            val title = userContextTitle(snapshot)
            Text(title, style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
            val known = snapshot.status in setOf(HomeNowStatus.CONFIRMED, HomeNowStatus.IDENTIFIED, HomeNowStatus.PROBABLE)
            if (known) {
                val statusText = when (snapshot.status) {
                    HomeNowStatus.CONFIRMED -> stringResource(R.string.home_now_confirmed)
                    HomeNowStatus.PROBABLE -> stringResource(R.string.home_now_probable)
                    else -> stringResource(R.string.home_now_identified)
                }
                val since = snapshot.contextStartedAt?.let { stringResource(R.string.home_now_since, formatClock(it, zone)) }
                Text(listOfNotNull(statusText, since).joinToString(" · "), color = HoodieColors.Muted, modifier = Modifier.testTag("home_context_status"))
                snapshot.contextStartedAt?.let { Text(stringResource(R.string.home_now_duration, formatDuration((now - it).coerceAtLeast(0))), color = HoodieColors.Muted) }
                if (snapshot.placeName != null && snapshot.placeName != snapshot.context?.label) Text(snapshot.placeName, style = MaterialTheme.typography.bodyMedium)
                if (snapshot.status != HomeNowStatus.CONFIRMED) snapshot.contextConfidence?.let { score ->
                    Text(
                        stringResource(R.string.home_now_confidence, (score.value * 100).roundToInt()) + " ⓘ",
                        color = HoodieColors.Hood,
                        modifier = Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button) { explain = !explain }.testTag("home_confidence"),
                    )
                }
                if (explain) Text(
                    stringResource(explanationRes(ConfidenceExplainer.origin(snapshot.contextSource, snapshot.status))),
                    style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted, modifier = Modifier.testTag("home_confidence_explanation"),
                )
            }
            snapshot.wakeAt?.let { wake ->
                Text(stringResource(if (snapshot.wakeConfidence == com.hoodie.app.domain.daycycle.WakeConfidence.HIGH) R.string.home_now_woke_at else R.string.home_now_wake_estimated, formatClock(wake, zone)), color = HoodieColors.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column { actions() }
    }
}

private fun explanationRes(origin: ConfidenceOrigin) = when (origin) {
    ConfidenceOrigin.PLACE_ARRIVAL -> R.string.home_conf_place
    ConfidenceOrigin.ROUTINE -> R.string.home_conf_routine
    ConfidenceOrigin.USER_CONFIRMED -> R.string.home_conf_confirmed
    ConfidenceOrigin.USER_CORRECTED -> R.string.home_conf_corrected
    ConfidenceOrigin.MOVEMENT -> R.string.home_conf_movement
    ConfidenceOrigin.LOCATION_CHECK -> R.string.home_conf_location
    ConfidenceOrigin.UNKNOWN -> R.string.home_conf_unknown
}