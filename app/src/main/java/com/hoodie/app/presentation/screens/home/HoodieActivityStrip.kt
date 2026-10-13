package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hoodie.app.R
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/** "Hoodie está trabalhando · desde 08:12 · há 2h": o que o personagem faz, logo abaixo da cena. */
@Composable
internal fun HoodieActivityStrip(
    activity: HoodieActivity?, startedAt: Long?, now: Long, zone: ZoneId, catName: String, modifier: Modifier = Modifier,
) {
    if (activity == null) return
    Column(modifier.fillMaxWidth().testTag("home_hoodie_activity")) {
        Text(stringResource(R.string.home_activity, catName, activity.label), style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
        startedAt?.let { start ->
            Text(stringResource(R.string.home_activity_since, formatClock(start, zone), formatDuration((now - start).coerceAtLeast(0))), color = HoodieColors.Muted)
        }
    }
}