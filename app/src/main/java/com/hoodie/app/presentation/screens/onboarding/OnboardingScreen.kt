package com.hoodie.app.presentation.screens.onboarding

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.FlowRow
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
import com.hoodie.app.presentation.common.runSystemAction
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
import androidx.compose.ui.platform.testTag
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
            OnboardingUiEvent.OpenAppSettings -> runSystemAction({ cause -> android.util.Log.e("OnboardingScreen", "Failed to open app settings", cause); vm.appSettingsFailed() }) { context.startActivity(vm.appSettingsIntent()) }
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

    OnboardingContent(
        state = state,
        permission = permission,
        hasLocation = permissionTick >= 0 && vm.hasLocation(),
        actions = OnboardingActions(
            go = vm::go,
            setName = vm::setName,
            markHomeHere = { vm.markHomeHere() },
            afterHome = vm::afterHome,
            pickWorkAddress = vm::pickWorkAddress,
            setWorkMode = vm::setWorkMode,
            updateRoutine = vm::updateRoutine,
            toggleDay = vm::toggleDay,
            finish = { vm.finish() },
            requestForeground = { foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            requestBackground = {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else vm.openAppSettings()
            },
        ),
    )
}

internal data class OnboardingActions(
    val go: (OnboardingStep) -> Unit = {},
    val setName: (String) -> Unit = {},
    val markHomeHere: () -> Unit = {},
    val afterHome: () -> Unit = {},
    val pickWorkAddress: () -> Unit = {},
    val setWorkMode: (WorkMode) -> Unit = {},
    val updateRoutine: ((com.hoodie.app.core.model.Routine) -> com.hoodie.app.core.model.Routine) -> Unit = {},
    val toggleDay: (DayOfWeek) -> Unit = {},
    val finish: () -> Unit = {},
    val requestForeground: () -> Unit = {},
    val requestBackground: () -> Unit = {},
)

@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    permission: LocationPermissionState,
    hasLocation: Boolean,
    actions: OnboardingActions = OnboardingActions(),
) {
    val uiTextContext = LocalContext.current
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()).testTag("onboarding_scroll")
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
                SpeechBubble(stringResource(R.string.onboarding_intro, state.catName))
                Text(
                    stringResource(R.string.ui_onboarding_screen_2),
                    color = HoodieColors.Muted, textAlign = TextAlign.Center,
                )
                PixelButton(stringResource(R.string.ui_onboarding_screen_3), { actions.go(OnboardingStep.NAME) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.NAME -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_4))
                OutlinedTextField(state.catName, actions.setName, singleLine = true, label = { Text(stringResource(R.string.ui_onboarding_screen_5)) }, modifier = Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.ui_onboarding_screen_6), { actions.go(OnboardingStep.PERMISSION) }, Modifier.fillMaxWidth(), enabled = state.catName.isNotBlank())
            }
            OnboardingStep.PERMISSION -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_7))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_location_reason, state.catName))
                    Text(stringResource(R.string.ui_onboarding_screen_8), color = HoodieColors.Muted)
                }
                if (hasLocation) {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_9), { actions.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth())
                } else {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_10), {
                        actions.requestForeground()
                    }, Modifier.fillMaxWidth())
                    PixelButton(stringResource(R.string.ui_onboarding_screen_11), { actions.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
            }
            OnboardingStep.HOME -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_12))
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_13))
                if (state.busy) CircularProgressIndicator()
                if (hasLocation) {
                    PixelButton(stringResource(R.string.ui_onboarding_screen_14), actions.markHomeHere, Modifier.fillMaxWidth(), enabled = !state.busy)
                } else {
                    Text(stringResource(R.string.onboarding_without_location, state.catName), color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_15), { actions.go(OnboardingStep.HOME_ADDRESS) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton(stringResource(R.string.ui_onboarding_screen_16), actions.afterHome, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
            OnboardingStep.BACKGROUND -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_17))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_background_reason, state.catName))
                    Text(stringResource(R.string.ui_onboarding_screen_18), style = MaterialTheme.typography.titleMedium)
                }
                if (permission == LocationPermissionState.BACKGROUND) {
                    Text(stringResource(R.string.onboarding_background_ready, state.catName), color = HoodieColors.Mint, textAlign = TextAlign.Center)
                    PixelButton(stringResource(R.string.ui_onboarding_screen_19), { actions.go(OnboardingStep.WORK) }, Modifier.fillMaxWidth())
                } else {
                    if (permission == LocationPermissionState.APPROXIMATE_ONLY) {
                        Text(stringResource(R.string.ui_onboarding_screen_20), color = HoodieColors.Coral, textAlign = TextAlign.Center)
                    }
                    PixelButton(stringResource(R.string.ui_onboarding_screen_21), {
                        actions.requestBackground()
                    }, Modifier.fillMaxWidth())
                    PixelButton(stringResource(R.string.ui_onboarding_screen_22), { actions.go(OnboardingStep.WORK) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
            }
            OnboardingStep.WORK -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_23))
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_24))
                PixelButton(stringResource(R.string.ui_onboarding_screen_25), actions.pickWorkAddress, Modifier.fillMaxWidth())
                PixelButton(stringResource(R.string.ui_onboarding_screen_26), { actions.setWorkMode(WorkMode.OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Blue)
                PixelButton(stringResource(R.string.ui_onboarding_screen_27), { actions.setWorkMode(WorkMode.HOME_OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton(stringResource(R.string.ui_onboarding_screen_28), { actions.setWorkMode(WorkMode.NONE) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                if (state.routine.workMode == WorkMode.OFFICE) {
                    Text(stringResource(R.string.ui_onboarding_screen_29), color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
            }
            OnboardingStep.SCHEDULE -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_30))
                val r = state.routine
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(uiTextContext.getString(R.string.ui_extra_onboarding_screen_1), r.startMinute, { m -> actions.updateRoutine { it.copy(startMinute = m) } }, Modifier.weight(1f))
                    TimeField(uiTextContext.getString(R.string.ui_extra_onboarding_screen_2), r.endMinute, { m -> actions.updateRoutine { it.copy(endMinute = m) } }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(uiTextContext.getString(R.string.ui_extra_onboarding_screen_3), r.lunchStartMinute, { m -> actions.updateRoutine { it.copy(lunchStartMinute = m) } }, Modifier.weight(1f))
                    TimeField(uiTextContext.getString(R.string.ui_extra_onboarding_screen_4), r.lunchEndMinute, { m -> actions.updateRoutine { it.copy(lunchEndMinute = m) } }, Modifier.weight(1f))
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_31), { actions.go(OnboardingStep.DAYS) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.DAYS -> {
                SectionLabel(stringResource(R.string.ui_onboarding_screen_32))
                DayToggles(state.routine.days, actions.toggleDay)
                PixelButton(stringResource(R.string.ui_onboarding_screen_33), { actions.go(OnboardingStep.DONE) }, Modifier.fillMaxWidth(), enabled = state.routine.days.isNotEmpty())
            }
            OnboardingStep.DONE -> {
                SpeechBubble(stringResource(R.string.ui_onboarding_screen_34))
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_onboarding_screen_35), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_onboarding_screen_36) + if (state.homeSaved) uiTextContext.getString(R.string.ui_extra_onboarding_screen_5) else uiTextContext.getString(R.string.ui_extra_onboarding_screen_6), color = HoodieColors.Muted)
                    Text(stringResource(R.string.ui_onboarding_screen_37) + state.routine.workMode.label + if (state.workSaved) uiTextContext.getString(R.string.ui_extra_onboarding_screen_7) else "", color = HoodieColors.Muted)
                }
                PixelButton(stringResource(R.string.ui_onboarding_screen_38), actions.finish, Modifier.fillMaxWidth(), color = HoodieColors.Gold, enabled = !state.busy)
            }
        }
    }
}

@Composable
fun DayToggles(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    val uiTextContext = LocalContext.current
    val labels = listOf(uiTextContext.getString(R.string.ui_extra_onboarding_screen_8), uiTextContext.getString(R.string.ui_extra_onboarding_screen_9), uiTextContext.getString(R.string.ui_extra_onboarding_screen_10), uiTextContext.getString(R.string.ui_extra_onboarding_screen_11), uiTextContext.getString(R.string.ui_extra_onboarding_screen_12), uiTextContext.getString(R.string.ui_extra_onboarding_screen_13), uiTextContext.getString(R.string.ui_extra_onboarding_screen_14))
    FlowRow(modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, d ->
            val on = d in days
            val description = uiTextContext.getString(when (d) {
                DayOfWeek.MONDAY -> R.string.weekday_monday
                DayOfWeek.TUESDAY -> R.string.weekday_tuesday
                DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
                DayOfWeek.THURSDAY -> R.string.weekday_thursday
                DayOfWeek.FRIDAY -> R.string.weekday_friday
                DayOfWeek.SATURDAY -> R.string.weekday_saturday
                DayOfWeek.SUNDAY -> R.string.weekday_sunday
            })
            val selectionDescription = uiTextContext.getString(if (on) R.string.control_selected else R.string.control_not_selected)
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) HoodieColors.Blue else HoodieColors.PanelLight)
                    .border(2.dp, HoodieColors.Outline)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(d) })
                    .semantics { contentDescription = description; stateDescription = selectionDescription }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(labels[i] + if (on) uiTextContext.getString(R.string.ui_extra_onboarding_screen_15) else uiTextContext.getString(R.string.ui_extra_onboarding_screen_16), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = if (on) HoodieColors.Outline else HoodieColors.Ink)
            }
        }
    }
}
