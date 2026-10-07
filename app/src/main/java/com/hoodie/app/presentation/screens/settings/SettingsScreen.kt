package com.hoodie.app.presentation.screens.settings

import androidx.compose.ui.res.stringResource
import com.hoodie.app.presentation.common.runSystemAction
import com.hoodie.app.R
import com.hoodie.app.presentation.common.UiText
import com.hoodie.app.presentation.common.resolve
import com.hoodie.app.core.error.*
import com.hoodie.app.presentation.common.runUiAction
import com.hoodie.app.presentation.common.CollectUiEvents
import com.hoodie.app.presentation.common.appErrorText
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.hoodie.app.presentation.common.retryableUiState
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.BuildConfig
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.geofence.GeofenceRegistrationError
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.security.DataWiper
import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.deviceusage.UsageAccessManager
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.domain.phoneinsights.usecase.ClearDigitalHistoryUseCase
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val permissions: LocationPermissionManager,
    private val geofences: GeofenceRegistrar,
    private val wiper: DataWiper,
    private val usageAccess: UsageAccessManager,
    private val clearDigital: ClearDigitalHistoryUseCase,
    private val activityPermissions: com.hoodie.app.core.mobility.ActivityRecognitionPermissionManager,
    private val mobilityRegistration: com.hoodie.app.core.mobility.MobilityRegistration,
    private val mobilityEngine: com.hoodie.app.engine.mobility.MobilityEngine,
) : ViewModel() {
    val activityPermission: StateFlow<com.hoodie.app.core.mobility.ActivityRecognitionPermissionState> = activityPermissions.stateFlow
    /** Permissão a pedir em runtime (null abaixo do Android 10: já concedida na instalação). */
    val activityRuntimePermission: String? = activityPermissions.runtimePermission

    fun setMobility(m: com.hoodie.app.core.datastore.MobilitySettings) = action {
        settings.setMobility(m)
        mobilityRegistration.sync()
    }

    /** Resultado do pedido de permissão (ou volta dos Ajustes do sistema). */
    fun onActivityPermissionResult() = action {
        activityPermissions.refresh()
        mobilityRegistration.sync()
    }

    fun activityAppSettingsIntent(): Intent = activityPermissions.appSettingsIntent()

    fun clearMobilityHistory() = action {
        mobilityEngine.clearHistory()
        _events.send(SettingsUiEvent.ShowMessage(R.string.mobility_history_cleared))
    }
    val usagePermission: StateFlow<UsagePermissionState> = usageAccess.state
    private val reloadSettings = MutableStateFlow(0L)
    val state: StateFlow<SettingsUiState> = retryableUiState(
        retries = reloadSettings,
        loading = SettingsUiState(),
        onFailure = { cause ->
            Log.e("SettingsViewModel", "Failed to read settings", cause)
            SettingsUiState(isLoading = false, error = cause.appErrorOr(DatabaseError.ReadFailed))
        },
        source = { settings.settings.map { SettingsUiState(settings = it, isLoading = false) } },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun retryLoad() { reloadSettings.update { it + 1 } }
    val permission: StateFlow<LocationPermissionState> = permissions.state
    val geofenceResult: StateFlow<GeofenceRegistrationResult?> = geofences.lastResult
    private val _events = kotlinx.coroutines.channels.Channel<SettingsUiEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setName(n: String) = action { settings.setCatName(n) }
    fun setCommute(s: CommuteStyle) = action { settings.setCommuteStyle(s) }
    fun setNotifications(on: Boolean) = action { settings.setNotifications(on) }

    /** Voltou das configurações do sistema: revalida e reaplica os geofences. */
    fun onResume() = action {
        val usage = usageAccess.refresh()
        val digital = settings.current().digital
        if (usage == UsagePermissionState.GRANTED && digital.analysisRequested) {
            settings.setDigital(digital.copy(analysisEnabled = true, analysisRequested = false))
        }
        activityPermissions.refresh()
        mobilityRegistration.sync()
        val before = permission.value
        val now = permissions.refresh()
        if (before != now || geofences.lastResult.value == null) geofences.registerAll()
    }

    fun reregister() = action {
        val r = geofences.registerAll()
        _events.send(SettingsUiEvent.ShowMessage(if (r.ok) R.string.geofences_registered else R.string.geofences_registration_failed))
    }

    fun appSettingsIntent(): Intent = permissions.appSettingsIntent()
    fun locationSettingsIntent(): Intent = permissions.locationSettingsIntent()

    /** Apaga tudo: banco, chave do banco, preferências, geofences, tarefas e notificações. Volta ao onboarding. */
    fun deleteEverything() = action { wiper.deleteEverything() }

    fun setDigital(d: DigitalSettings) = action {
        val request = d.analysisEnabled && !usageAccess.isGranted()
        settings.setDigital(d.copy(analysisEnabled = d.analysisEnabled && !request, analysisRequested = request))
        if (request) _events.send(SettingsUiEvent.OpenUsageSettings)
    }
    fun usageAccessIntent(): Intent = usageAccess.settingsIntent()
    fun systemSettingsLaunchFailed() { _events.trySend(SettingsUiEvent.ShowError(SystemSettingsError.Unavailable)) }

    fun permissionLaunchFailed() { _events.trySend(SettingsUiEvent.ShowError(UsageAccessError.Unavailable)) }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        runUiAction(DatabaseError.WriteFailed, { error, cause ->
            Log.e("SettingsViewModel", "Failed to apply settings action", cause)
            _events.send(SettingsUiEvent.ShowError(error))
        }, block)
    }

    /** Apaga só os agregados do Diário Digital (as categorias escolhidas ficam). */
    fun clearDigitalHistory() = action {
        clearDigital()
        _events.send(SettingsUiEvent.ShowMessage(R.string.digital_history_cleared))
    }
}

sealed interface SettingsUiEvent {
    data object OpenUsageSettings : SettingsUiEvent
    data class ShowError(val error: AppError) : SettingsUiEvent
    data class ShowMessage(@androidx.annotation.StringRes val resource: Int) : SettingsUiEvent
}

/** Texto do card "Localização" a partir da permissão e do último registro de geofences. */
internal fun locationSummary(permission: LocationPermissionState, result: GeofenceRegistrationResult?): UiText = when (permission) {
    LocationPermissionState.NONE -> UiText.Resource(R.string.location_none)
    LocationPermissionState.LOCATION_DISABLED -> UiText.Resource(R.string.location_disabled_summary)
    LocationPermissionState.APPROXIMATE_ONLY -> UiText.Resource(R.string.location_approximate_summary)
    LocationPermissionState.FOREGROUND -> UiText.Resource(R.string.location_foreground_summary)
    LocationPermissionState.BACKGROUND -> when {
        result == null -> UiText.Resource(R.string.location_background_summary)
        result.ok && result.requested == 0 -> UiText.Resource(R.string.location_no_places)
        result.ok && result.skipped > 0 -> UiText.Resource(R.string.location_places_skipped, listOf(result.registered, result.skipped))
        result.ok -> UiText.Quantity(R.plurals.location_places_monitored, result.registered, listOf(result.registered))
        else -> UiText.Resource(R.string.location_paused_reason, listOf(UiText.Resource(when (result.error) {
            GeofenceRegistrationError.GEOFENCE_NOT_AVAILABLE -> R.string.location_geofence_unavailable
            GeofenceRegistrationError.GEOFENCE_TOO_MANY_GEOFENCES -> R.string.location_geofence_limit
            GeofenceRegistrationError.GEOFENCE_TOO_MANY_PENDING_INTENTS -> R.string.location_registration_limit
            GeofenceRegistrationError.PERMISSION_DENIED -> R.string.error_location_permission
            GeofenceRegistrationError.LOCATION_DISABLED -> R.string.error_location_disabled
            GeofenceRegistrationError.PLAY_SERVICES_ERROR -> R.string.location_play_services_unavailable
            else -> R.string.location_geofence_unknown
        })))
    }
}

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val s = ui.settings
    val permission by vm.permission.collectAsStateWithLifecycle()
    val geofenceResult by vm.geofenceResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectUiEvents(vm.events) { event ->
        when (event) {
            SettingsUiEvent.OpenUsageSettings -> runSystemAction({ vm.permissionLaunchFailed() }) { context.startActivity(vm.usageAccessIntent()) }
            is SettingsUiEvent.ShowError -> scope.launch { snackbar.showSnackbar(context.appErrorText(event.error)) }
            is SettingsUiEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(context.getString(event.resource)) }
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val usagePermission by vm.usagePermission.collectAsStateWithLifecycle()
    val activityPermission by vm.activityPermission.collectAsStateWithLifecycle()
    val requestActivity = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { vm.onActivityPermissionResult() }
    SettingsContent(
        s = s,
        isLoading = ui.isLoading,
        error = ui.error,
        permission = permission,
        geofenceResult = geofenceResult,
        usagePermission = usagePermission,
        activityPermission = activityPermission,
        onOpen = onOpen,
        snackbar = snackbar,
        actions = SettingsActions(
            retryLoad = vm::retryLoad,
            setName = { vm.setName(it) },
            setCommute = { vm.setCommute(it) },
            setNotifications = { vm.setNotifications(it) },
            reregister = { vm.reregister() },
            setDigital = { vm.setDigital(it) },
            setMobility = { vm.setMobility(it) },
            clearDigitalHistory = { vm.clearDigitalHistory() },
            clearMobilityHistory = { vm.clearMobilityHistory() },
            deleteEverything = { vm.deleteEverything() },
            openLocationSettings = { runSystemAction({ vm.systemSettingsLaunchFailed() }) { context.startActivity(if (permission == LocationPermissionState.LOCATION_DISABLED) vm.locationSettingsIntent() else vm.appSettingsIntent()) } },
            openUsageSettings = { runSystemAction({ vm.permissionLaunchFailed() }) { context.startActivity(vm.usageAccessIntent()) } },
            requestActivityPermission = {
                val p = vm.activityRuntimePermission
                runSystemAction({ vm.systemSettingsLaunchFailed() }) {
                    if (p != null) requestActivity.launch(p) else context.startActivity(vm.activityAppSettingsIntent())
                }
            },
        ),
    )
}

internal data class SettingsActions(
    val retryLoad: () -> Unit = {},
    val setName: (String) -> Unit = {},
    val setCommute: (CommuteStyle) -> Unit = {},
    val setNotifications: (Boolean) -> Unit = {},
    val reregister: () -> Unit = {},
    val setDigital: (DigitalSettings) -> Unit = {},
    val setMobility: (com.hoodie.app.core.datastore.MobilitySettings) -> Unit = {},
    val clearDigitalHistory: () -> Unit = {},
    val clearMobilityHistory: () -> Unit = {},
    val deleteEverything: () -> Unit = {},
    val openLocationSettings: () -> Unit = {},
    val openUsageSettings: () -> Unit = {},
    val requestActivityPermission: () -> Unit = {},
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun SettingsContent(
    s: AppSettings,
    isLoading: Boolean = false,
    error: AppError? = null,
    permission: LocationPermissionState,
    geofenceResult: GeofenceRegistrationResult?,
    usagePermission: UsagePermissionState,
    activityPermission: com.hoodie.app.core.mobility.ActivityRecognitionPermissionState,
    onOpen: (String) -> Unit,
    actions: SettingsActions = SettingsActions(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiTextContext = LocalContext.current
    val context = LocalContext.current
    if (isLoading || error != null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.ui_settings_screen_1), style = MaterialTheme.typography.headlineSmall)
            if (isLoading) com.hoodie.app.presentation.components.SettingsLoadingSkeleton()
            else com.hoodie.app.presentation.components.ErrorState(context.appErrorText(requireNotNull(error)), actions.retryLoad)
        }
        return
    }
    var editingName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDigitalDelete by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).testTag("settings_scroll").padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SnackbarHost(snackbar)
        Text(stringResource(R.string.ui_settings_screen_1), style = MaterialTheme.typography.headlineSmall)

        PixelPanel(Modifier.fillMaxWidth(), onClick = { editingName = true }) {
            SectionLabel(stringResource(R.string.ui_settings_screen_2))
            Text(s.catName, style = MaterialTheme.typography.titleMedium)
        }
        PixelButton(stringResource(R.string.ui_settings_screen_3), { onOpen(Routes.ROUTINE) }, Modifier.fillMaxWidth())
        PixelButton(stringResource(R.string.settings_cat_profile, s.catName), { onOpen(Routes.PROFILE) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
        PixelButton(stringResource(R.string.ui_settings_screen_4), { onOpen(Routes.MEMORIES) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)

        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.settings_cat_commute, s.catName))
            ChipRow(CommuteStyle.entries.map { it.label }, s.commuteStyle.ordinal, { actions.setCommute(CommuteStyle.entries[it]) })
        }

        PixelPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel(stringResource(R.string.ui_settings_screen_5))
                    Text(stringResource(R.string.ui_settings_screen_6), color = HoodieColors.Muted)
                }
                Switch(s.notificationsEnabled, actions.setNotifications)
            }
        }

        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.ui_settings_screen_7))
            Text(locationSummary(permission, geofenceResult).resolve(context))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PixelButton(stringResource(R.string.ui_settings_screen_8), {
                    actions.openLocationSettings()
                }, color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                PixelButton(stringResource(R.string.ui_settings_screen_9), actions.reregister, color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
        }

        var confirmMobilityDelete by remember { mutableStateOf(false) }
        MobilitySettingsPanel(
            s.mobility, activityPermission,
            onChange = actions.setMobility,
            onPermission = {
                actions.requestActivityPermission()
            },
            onClear = { confirmMobilityDelete = true },
        )
        if (confirmMobilityDelete) {
            AlertDialog(
                onDismissRequest = { confirmMobilityDelete = false },
                title = { Text(stringResource(R.string.ui_settings_screen_10)) },
                text = { Text(stringResource(R.string.settings_clear_mobility_warning, s.catName)) },
                confirmButton = { TextButton(onClick = { actions.clearMobilityHistory(); confirmMobilityDelete = false }) { Text(stringResource(R.string.ui_settings_screen_11), color = HoodieColors.Coral) } },
                dismissButton = { TextButton(onClick = { confirmMobilityDelete = false }) { Text(stringResource(R.string.ui_settings_screen_12)) } },
            )
        }

        DigitalSettingsPanel(
            s.digital, usagePermission,
            onChange = actions.setDigital,
            onAccess = { actions.openUsageSettings() },
            onClear = { confirmDigitalDelete = true },
        )

        PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
            SectionLabel(stringResource(R.string.ui_settings_screen_13))
            Text(
                stringResource(R.string.ui_settings_screen_14) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_1) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_2) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_3) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_4) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_5) +
                    uiTextContext.getString(R.string.ui_extra_settings_screen_6),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PixelButton(stringResource(R.string.ui_settings_screen_15), { confirmDelete = true }, Modifier.fillMaxWidth(), color = HoodieColors.Coral)

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = HoodieColors.Muted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { versionTaps++ }.padding(8.dp),
        )
        // Ferramenta interna: sempre visível em debug, escondida (5 toques) em release.
        if (BuildConfig.DEBUG || versionTaps >= 5) {
            PixelButton(stringResource(R.string.ui_settings_screen_16), { onOpen(Routes.DEV_LAB) }, Modifier.fillMaxWidth(), color = HoodieColors.Mint)
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(s.catName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.ui_settings_screen_17)) },
            text = { OutlinedTextField(name, { name = it.take(16) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { actions.setName(name); editingName = false }) { Text(stringResource(R.string.ui_settings_screen_18)) } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.ui_settings_screen_19)) } },
        )
    }
    if (confirmDigitalDelete) {
        AlertDialog(
            onDismissRequest = { confirmDigitalDelete = false },
            title = { Text(stringResource(R.string.ui_settings_screen_20)) },
            text = { Text(stringResource(R.string.ui_settings_screen_21)) },
            confirmButton = { TextButton(onClick = { actions.clearDigitalHistory(); confirmDigitalDelete = false }) { Text(stringResource(R.string.ui_settings_screen_22), color = HoodieColors.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDigitalDelete = false }) { Text(stringResource(R.string.ui_settings_screen_23)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.ui_settings_screen_24)) },
            text = { Text(stringResource(R.string.settings_clear_all_warning, s.catName)) },
            confirmButton = { TextButton(onClick = { actions.deleteEverything(); confirmDelete = false }) { Text(stringResource(R.string.ui_settings_screen_25), color = HoodieColors.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_settings_screen_26)) } },
        )
    }
}

/** Mobilidade Contextual: detecção, aprendizado, transporte preferido e histórico. */
@Composable
private fun MobilitySettingsPanel(
    m: com.hoodie.app.core.datastore.MobilitySettings,
    permission: com.hoodie.app.core.mobility.ActivityRecognitionPermissionState,
    onChange: (com.hoodie.app.core.datastore.MobilitySettings) -> Unit,
    onPermission: () -> Unit,
    onClear: () -> Unit,
) {
    val uiTextContext = LocalContext.current
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ui_settings_screen_27))
        Text(
            stringResource(R.string.ui_settings_screen_28) +
                uiTextContext.getString(R.string.ui_extra_settings_screen_7),
            style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted,
        )
        if (!permission.granted) {
            Text(stringResource(R.string.ui_settings_screen_29), modifier = Modifier.padding(top = 6.dp))
            PixelButton(stringResource(R.string.ui_settings_screen_30), onPermission, Modifier.fillMaxWidth().padding(top = 6.dp))
        } else {
            Text(stringResource(R.string.ui_settings_screen_31), modifier = Modifier.padding(top = 6.dp))
        }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_8), uiTextContext.getString(R.string.ui_extra_settings_screen_9), m.detectionEnabled) { onChange(m.copy(detectionEnabled = it)) }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_10), uiTextContext.getString(R.string.ui_extra_settings_screen_11), m.learnTrips) { onChange(m.copy(learnTrips = it)) }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_12), uiTextContext.getString(R.string.ui_extra_settings_screen_13), m.confirmNewPlaces) { onChange(m.copy(confirmNewPlaces = it)) }
        SectionLabel(stringResource(R.string.ui_settings_screen_32), Modifier.padding(top = 8.dp))
        val modes = listOf<com.hoodie.app.core.mobility.MovementMode?>(null) + listOf(
            com.hoodie.app.core.mobility.MovementMode.CAR, com.hoodie.app.core.mobility.MovementMode.BUS,
            com.hoodie.app.core.mobility.MovementMode.TRAIN, com.hoodie.app.core.mobility.MovementMode.METRO,
        )
        ChipRow(modes.map { it?.let { mode -> "${mode.emoji} ${mode.label}" } ?: uiTextContext.getString(R.string.mobility_automatic) }, modes.indexOf(m.preferredMode), { onChange(m.copy(preferredMode = modes[it])) })
        PixelButton(stringResource(R.string.ui_settings_screen_33), onClear, Modifier.fillMaxWidth().padding(top = 8.dp), color = HoodieColors.Coral)
    }
}

/** Ajustes do Diário Digital: o que é lido, o que aparece e o que é guardado. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun DigitalSettingsPanel(
    d: DigitalSettings,
    permission: UsagePermissionState,
    onChange: (DigitalSettings) -> Unit,
    onAccess: () -> Unit,
    onClear: () -> Unit,
) {
    val uiTextContext = LocalContext.current
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ui_settings_screen_34))
        Text(
            when (permission) {
                UsagePermissionState.GRANTED -> uiTextContext.getString(R.string.ui_extra_settings_screen_14)
                UsagePermissionState.DENIED -> uiTextContext.getString(R.string.ui_extra_settings_screen_15)
                UsagePermissionState.UNAVAILABLE -> uiTextContext.getString(R.string.ui_extra_settings_screen_16)
            },
        )
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_17), uiTextContext.getString(R.string.ui_extra_settings_screen_18), d.analysisEnabled) { onChange(d.copy(analysisEnabled = it)) }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_19), uiTextContext.getString(R.string.ui_extra_settings_screen_20), d.showInDiary) { onChange(d.copy(showInDiary = it)) }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_21), uiTextContext.getString(R.string.ui_extra_settings_screen_22), d.saveHistory) { onChange(d.copy(saveHistory = it)) }
        DigitalSwitch(uiTextContext.getString(R.string.ui_extra_settings_screen_23), uiTextContext.getString(R.string.ui_extra_settings_screen_24), d.showTopAppsByContext) { onChange(d.copy(showTopAppsByContext = it)) }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PixelButton(stringResource(R.string.ui_settings_screen_35), onAccess, color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            PixelButton(stringResource(R.string.ui_settings_screen_36), onClear, color = HoodieColors.Coral)
        }
    }
}

@Composable
private fun DigitalSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
        Switch(checked, onChange)
    }
}
