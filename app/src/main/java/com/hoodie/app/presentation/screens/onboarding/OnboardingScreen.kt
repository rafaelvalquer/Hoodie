package com.hoodie.app.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.hoodie.app.R
import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.CollectUiEvents
import com.hoodie.app.presentation.common.appErrorText
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.presentation.screens.places.picker.PlacePickerContent
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.components.TimeField
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.DayOfWeek

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var permissionTick by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permission by vm.permission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectUiEvents(vm.events) { event ->
        when (event) {
            OnboardingUiEvent.OpenAppSettings -> runCatching { context.startActivity(vm.appSettingsIntent()) }
                .onFailure { vm.appSettingsFailed() }
        }
    }
    // Android 10 mostra o diálogo; 11+ manda para as configurações do app.
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++; vm.revalidatePermission()
    }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        vm.revalidatePermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.go(OnboardingStep.HOME)
    }
    // Ao voltar das configurações do sistema: revalida a permissão.
    LifecycleResumeEffect(Unit) {
        vm.revalidatePermission()
        permissionTick++
        onPauseOrDispose { }
    }

    // Passos de endereço ocupam a tela toda (o mapa não pode ficar dentro de scroll).
    when (state.step) {
        OnboardingStep.HOME_ADDRESS -> {
            PlacePickerContent(
                PlaceType.HOME, null, onDone = vm::onHomeAddressSaved, onCancel = { vm.go(OnboardingStep.HOME) },
                modifier = Modifier.safeDrawingPadding(), allowTypeChange = false,
            )
            return
        }
        OnboardingStep.WORK_ADDRESS -> {
            PlacePickerContent(
                PlaceType.WORK, null, onDone = vm::onWorkAddressSaved, onCancel = { vm.go(OnboardingStep.WORK) },
                modifier = Modifier.safeDrawingPadding(), allowTypeChange = false,
            )
            return
        }
        else -> Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val anim = when (state.step) {
            OnboardingStep.WELCOME, OnboardingStep.DONE -> AnimationId.WAVE
            OnboardingStep.PERMISSION, OnboardingStep.BACKGROUND -> AnimationId.THINK_STAND
            OnboardingStep.HOME -> AnimationId.LOOK_AROUND
            OnboardingStep.WORK, OnboardingStep.SCHEDULE -> AnimationId.WORK_TYPING
            else -> AnimationId.IDLE
        }
        Text(stringResource(R.string.ui_onboarding_screen_1), style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        AnimatedHoodie(anim, size = 150.dp)
        state.error?.let { error ->
            val text = when {
                state.step == OnboardingStep.HOME && error == LocationError.Unavailable -> stringResource(R.string.onboarding_home_location_failed)
                state.step == OnboardingStep.HOME -> stringResource(R.string.onboarding_home_save_failed)
                else -> context.appErrorText(error)
            }
            Text(text, color = HoodieColors.Coral, textAlign = TextAlign.Center)
        }

        when (state.step) {
            // Tratados acima, em tela cheia.
            OnboardingStep.HOME_ADDRESS, OnboardingStep.WORK_ADDRESS -> Unit
            OnboardingStep.WELCOME -> {
                SpeechBubble("Olá. Eu sou ${state.catName}.\nVamos descobrir como é o seu dia.")
                Text(
                    stringResource(R.string.ui_onboarding_screen_2),
                    color = HoodieColors.Muted, textAlign = TextAlign.Center,
                )
                PixelButton(stringResource(R.string.ui_onboarding_screen_3), { vm.go(OnboardingStep.NAME) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.NAME -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_4))
                OutlinedTextField(state.catName, vm::setName, singleLine = true, label = { Text(stringResource(R.string.ui_onboarding_screen_5)) }, modifier = Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.ui_onboarding_screen_6), { vm.go(OnboardingStep.PERMISSION) }, Modifier.fillMaxWidth(), enabled = state.catName.isNotBlank())
            }
            OnboardingStep.PERMISSION -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_7))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text("A localização permite ao ${state.catName} entender quando você está em casa, no trabalho ou em outros lugares importantes.")
                    Text(stringResource(R.string.ui_onboarding_screen_8), color = HoodieColors.Muted)
                }
                if (permissionTick >= 0 && vm.hasLocation()) {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_9), { vm.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth())
                } else {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_10), {
                        foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, Modifier.fillMaxWidth())
                    PixelButton(stringResource(R.string.ui_onboarding_screen_11), { vm.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
            }
            OnboardingStep.HOME -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_12))
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_13))
                if (state.busy) CircularProgressIndicator()
                if (vm.hasLocation()) {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_14), vm::markHomeHere, Modifier.fillMaxWidth(), enabled = !state.busy)
                } else {
                    Text("Sem localização, o ${state.catName} segue a rotina provável. Dá para ativar depois.", color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_15), { vm.go(OnboardingStep.HOME_ADDRESS) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton(stringResource(R.string.ui_onboarding_screen_16), vm::afterHome, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
            OnboardingStep.BACKGROUND -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_17))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text("Para o ${state.catName} perceber quando você chega ou sai mesmo com o aplicativo fechado, ative:")
                    Text(stringResource(R.string.ui_onboarding_screen_18), style = MaterialTheme.typography.titleMedium)
                }
                if (permission == LocationPermissionState.BACKGROUND) {
                    Text("✅ Tudo certo: o ${state.catName} vai perceber suas chegadas.", color = HoodieColors.Mint, textAlign = TextAlign.Center)
                    PixelButton(stringResource(R.string.ui_onboarding_screen_19), { vm.go(OnboardingStep.WORK) }, Modifier.fillMaxWidth())
                } else {
                    if (permission == LocationPermissionState.APPROXIMATE_ONLY) {
                        Text(stringResource(R.string.ui_onboarding_screen_20), color = HoodieColors.Coral, textAlign = TextAlign.Center)
                    }
                    PixelButton(stringResource(R.string.ui_onboarding_screen_21), {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else vm.openAppSettings()
                    }, Modifier.fillMaxWidth())
                    PixelButton(stringResource(R.string.ui_onboarding_screen_22), { vm.go(OnboardingStep.WORK) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
            }
            OnboardingStep.WORK -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_23))
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_24))
                PixelButton(stringResource(R.string.ui_onboarding_screen_25), vm::pickWorkAddress, Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.ui_onboarding_screen_26), { vm.setWorkMode(WorkMode.OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Blue)
                PixelButton(stringResource(R.string.ui_onboarding_screen_27), { vm.setWorkMode(WorkMode.HOME_OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton(stringResource(R.string.ui_onboarding_screen_28), { vm.setWorkMode(WorkMode.NONE) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                if (state.routine.workMode == WorkMode.OFFICE) {
                    Text(stringResource(R.string.ui_onboarding_screen_29), color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
            }
            OnboardingStep.SCHEDULE -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_30))
                val r = state.routine
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Entrada", r.startMinute, { m -> vm.updateRoutine { it.copy(startMinute = m) } }, Modifier.weight(1f))
                    TimeField("Saída", r.endMinute, { m -> vm.updateRoutine { it.copy(endMinute = m) } }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Almoço", r.lunchStartMinute, { m -> vm.updateRoutine { it.copy(lunchStartMinute = m) } }, Modifier.weight(1f))
                    TimeField("Volta", r.lunchEndMinute, { m -> vm.updateRoutine { it.copy(lunchEndMinute = m) } }, Modifier.weight(1f))
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_31), { vm.go(OnboardingStep.DAYS) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.DAYS -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_32))
                DayToggles(state.routine.days, vm::toggleDay)
                PixelButton(stringResource(R.string.ui_onboarding_screen_33), { vm.go(OnboardingStep.DONE) }, Modifier.fillMaxWidth(), enabled = state.routine.days.isNotEmpty())
            }
            OnboardingStep.DONE -> {
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_34))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_onboarding_screen_35), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_onboarding_screen_36) + if (state.homeSaved) "salva" else "definir depois", color = HoodieColors.Muted)
                    Text(stringResource(R.string.ui_onboarding_screen_37) + state.routine.workMode.label + if (state.workSaved) " · local salvo" else "", color = HoodieColors.Muted)
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_38), vm::finish, Modifier.fillMaxWidth(), color = HoodieColors.Gold, enabled = !state.busy)
            }
        }
    }
}

@Composable
fun DayToggles(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, d ->
            val on = d in days
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) HoodieColors.Blue else HoodieColors.PanelLight)
                    .border(2.dp, HoodieColors.Outline)
                    .clickable { onToggle(d) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(labels[i] + if (on) "\n✓" else "\n ", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = if (on) HoodieColors.Outline else HoodieColors.Ink)
            }
        }
    }
}
