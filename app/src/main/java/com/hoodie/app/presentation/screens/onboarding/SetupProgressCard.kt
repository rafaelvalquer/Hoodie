package com.hoodie.app.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.R
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.data.repository.RoutineRepository
import com.hoodie.app.domain.setup.SetupItem
import com.hoodie.app.domain.setup.SetupProgress
import com.hoodie.app.domain.setup.SetupStage
import com.hoodie.app.engine.setup.SetupProgressTracker
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupProgressViewModel @Inject constructor(
    private val tracker: SetupProgressTracker,
    private val settings: SettingsRepository,
    private val routines: RoutineRepository,
    private val permissions: LocationPermissionManager,
    private val clock: ClockProvider,
) : ViewModel() {
    val progress = tracker.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = tracker.refresh()
    fun hasForeground() = permissions.hasForeground()
    fun needsBackground() = permissions.current().needsBackgroundStep
    fun appSettingsIntent() = permissions.appSettingsIntent()

    /** "Não trabalho" é uma resposta: grava a rotina sem trabalho e marca como decidido. */
    fun declareNoWork() = viewModelScope.launch {
        routines.save(Routine(workMode = WorkMode.NONE), clock.nowMillis())
        settings.setSetupWorkDone(true)
    }

    /** Notificações: aceitar ou recusar é uma decisão; nunca insistimos. */
    fun notificationsDecided(allowed: Boolean) = viewModelScope.launch {
        settings.setSetupNotificationsDone(true)
        if (!allowed) settings.setNotifications(false)
    }
}

/** Cartão discreto da Home: mostra o próximo passo e deixa o resto para depois. */
@Composable
fun SetupProgressHost(onOpen: (String) -> Unit, modifier: Modifier = Modifier, vm: SetupProgressViewModel = hiltViewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var explain by remember { mutableStateOf<SetupItem?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.notificationsDecided(granted) }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

    val p = progress ?: return
    if (dismissed || p.stage == SetupStage.FULLY_CONFIGURED || p.stage == SetupStage.NOT_STARTED) return
    SetupProgressCard(
        p, modifier,
        onAction = { explain = it },
        onNoWork = vm::declareNoWork,
        onDemo = { onOpen(Routes.DEMO_DAY) },
        onLater = { dismissed = true },
    )
    explain?.let { item ->
        SetupPermissionExplainer(item, onDismiss = { explain = null }, onContinue = {
            explain = null
            when (item) {
                SetupItem.HOME -> onOpen(Routes.placePicker(PlaceType.HOME))
                SetupItem.WORK -> onOpen(Routes.ROUTINE)
                SetupItem.BACKGROUND_LOCATION -> when {
                    !vm.hasForeground() -> foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    // Android 11+: o "o tempo todo" só é concedido nas configurações do sistema.
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else -> runCatching { context.startActivity(vm.appSettingsIntent()) }
                }
                SetupItem.NOTIFICATIONS ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.notificationsDecided(true)
            }
        })
    }
}

@Composable
internal fun SetupProgressCard(
    progress: SetupProgress, modifier: Modifier = Modifier,
    onAction: (SetupItem) -> Unit, onNoWork: () -> Unit, onDemo: () -> Unit, onLater: () -> Unit,
) {
    val next = progress.next ?: return
    PixelPanel(modifier.fillMaxWidth().testTag("setup_progress_card"), color = HoodieColors.PanelLight) {
        SectionLabel(stringResource(R.string.setup_card_title))
        Text(stringResource(R.string.setup_card_body), color = HoodieColors.Muted, style = MaterialTheme.typography.bodyMedium)
        SetupItem.entries.forEach { item ->
            val done = item !in progress.pending
            Text((if (done) "✓ " else "○ ") + stringResource(labelOf(item)), color = if (done) HoodieColors.Mint else HoodieColors.Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PixelButton(stringResource(actionOf(next)), { onAction(next) }, Modifier.fillMaxWidth().testTag("setup_next"))
            if (next == SetupItem.WORK) PixelButton(stringResource(R.string.setup_no_work), onNoWork, Modifier.fillMaxWidth(), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(stringResource(R.string.setup_demo), onDemo, Modifier.weight(1f).testTag("setup_demo"), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
                PixelButton(stringResource(R.string.setup_later), onLater, Modifier.weight(1f), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
        }
    }
}

private fun labelOf(item: SetupItem) = when (item) {
    SetupItem.HOME -> R.string.setup_item_home
    SetupItem.BACKGROUND_LOCATION -> R.string.setup_item_bg
    SetupItem.WORK -> R.string.setup_item_work
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notif
}

private fun actionOf(item: SetupItem) = when (item) {
    SetupItem.HOME -> R.string.setup_action_home
    SetupItem.BACKGROUND_LOCATION -> R.string.setup_action_bg
    SetupItem.WORK -> R.string.setup_action_work
    SetupItem.NOTIFICATIONS -> R.string.setup_action_notif
}