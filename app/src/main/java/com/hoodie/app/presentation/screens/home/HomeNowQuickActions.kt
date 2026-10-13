package com.hoodie.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors

/** "Sim, correto" (1 toque) e "Corrigir". Depois de confirmado, só resta Corrigir. */
@Composable
internal fun HomeNowQuickActions(actions: QuickActionsUi, onConfirm: (Long) -> Unit, onCorrect: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!actions.canConfirm && !actions.canCorrect) return
    Row(modifier.fillMaxWidth().testTag("home_quick_actions"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (actions.canConfirm) PixelButton(
            stringResource(R.string.home_quick_confirm), { actions.confirmEventId?.let(onConfirm) },
            Modifier.weight(1f).testTag("home_quick_confirm"), enabled = enabled,
        )
        if (actions.canCorrect) PixelButton(
            stringResource(R.string.home_quick_correct), onCorrect,
            Modifier.weight(1f).testTag("home_quick_correct"), color = HoodieColors.Panel, textColor = HoodieColors.Ink, enabled = enabled,
        )
    }
}