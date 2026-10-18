package com.hoodie.app.presentation.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.R
import com.hoodie.app.engine.setup.ProgressiveStep
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.screens.demo.DemoDayScreen
import com.hoodie.app.presentation.theme.HoodieColors

/** Entrada em 3 passos: conhecer o Hoodie, escolher o nome e decidir o acompanhamento. Permissões ficam para depois. */
@Composable
fun ProgressiveOnboardingScreen(vm: ProgressiveOnboardingViewModel = hiltViewModel()) {
    var restore by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (restore) {
        com.hoodie.app.presentation.screens.backup.BackupScreen(onBack = { restore = false })
        return
    }
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.showDemo) {
        DemoDayScreen(onExit = { vm.showDemo(false) })
        return
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).testTag("onboarding_scroll").padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.ui_onboarding_screen_1), style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        AnimatedHoodie(if (state.step == ProgressiveStep.TRACKING) AnimationId.THINK_STAND else AnimationId.WAVE, size = 150.dp)
        when (state.step) {
            ProgressiveStep.WELCOME -> {
                SpeechBubble(stringResource(R.string.onboarding_intro, state.catName))
                Text(stringResource(R.string.prog_welcome_body), color = HoodieColors.Muted, textAlign = TextAlign.Center)
                PixelButton(stringResource(R.string.ui_onboarding_screen_3), { vm.go(ProgressiveStep.NAME) }, Modifier.fillMaxWidth().testTag("prog_start"))
                if (com.hoodie.app.core.config.HoodieConfig.HOODIE_LOCAL_ENCRYPTED_BACKUP_V1) {
                    PixelButton("Restaurar meus dados", { restore = true }, Modifier.fillMaxWidth())
                }
                PixelButton(stringResource(R.string.setup_demo), { vm.showDemo(true) }, Modifier.fillMaxWidth().testTag("prog_demo"), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
            ProgressiveStep.NAME -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_4))
                OutlinedTextField(state.catName, vm::setName, singleLine = true, label = { Text(stringResource(R.string.ui_onboarding_screen_5)) }, modifier = Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.ui_onboarding_screen_3), { vm.go(ProgressiveStep.TRACKING) }, Modifier.fillMaxWidth().testTag("prog_name_next"), enabled = state.catName.isNotBlank())
            }
            ProgressiveStep.TRACKING -> {
                SectionLabel(stringResource(R.string.prog_tracking_title))
                Text(stringResource(R.string.prog_tracking_body), color = HoodieColors.Muted, textAlign = TextAlign.Center)
                Text(stringResource(R.string.prog_tracking_limits), color = HoodieColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                PixelButton(stringResource(R.string.prog_enter_home), { vm.finish() }, Modifier.fillMaxWidth().testTag("prog_finish"), enabled = !state.busy)
                PixelButton(stringResource(R.string.setup_demo), { vm.showDemo(true) }, Modifier.fillMaxWidth(), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
        }
    }
}
