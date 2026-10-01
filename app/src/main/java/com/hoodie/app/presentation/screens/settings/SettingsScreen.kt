package com.hoodie.app.presentation.screens.settings

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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val permissions: LocationPermissionManager,
    private val geofences: GeofenceRegistrar,
    private val wiper: DataWiper,
    private val usageAccess: UsageAccessManager,
    private val clearDigital: ClearDigitalHistoryUseCase,
) : ViewModel() {
    val usagePermission: StateFlow<UsagePermissionState> = usageAccess.state
    val state = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val permission: StateFlow<LocationPermissionState> = permissions.state
    val geofenceResult: StateFlow<GeofenceRegistrationResult?> = geofences.lastResult
    val info = MutableStateFlow<String?>(null)

    fun setName(n: String) = viewModelScope.launch { settings.setCatName(n) }
    fun setCommute(s: CommuteStyle) = viewModelScope.launch { settings.setCommuteStyle(s) }
    fun setNotifications(on: Boolean) = viewModelScope.launch { settings.setNotifications(on) }

    /** Voltou das configurações do sistema: revalida e reaplica os geofences. */
    fun onResume() = viewModelScope.launch {
        usageAccess.refresh()
        val before = permission.value
        val now = permissions.refresh()
        if (before != now || geofences.lastResult.value == null) geofences.registerAll()
    }

    fun reregister() = viewModelScope.launch {
        val r = geofences.registerAll()
        info.value = if (r.ok) "Geofences registrados." else "Não foi possível registrar."
    }

    fun appSettingsIntent(): Intent = permissions.appSettingsIntent()
    fun locationSettingsIntent(): Intent = permissions.locationSettingsIntent()

    /** Apaga tudo: banco, chave do banco, preferências, geofences, tarefas e notificações. Volta ao onboarding. */
    fun deleteEverything() = viewModelScope.launch { wiper.deleteEverything() }

    fun setDigital(d: DigitalSettings) = viewModelScope.launch { settings.setDigital(d) }
    fun usageAccessIntent(): Intent = usageAccess.settingsIntent()

    /** Apaga só os agregados do Diário Digital (as categorias escolhidas ficam). */
    fun clearDigitalHistory() = viewModelScope.launch {
        clearDigital()
        info.value = "Histórico digital apagado."
    }
}

/** Texto do card "Localização" a partir da permissão e do último registro de geofences. */
internal fun locationSummary(permission: LocationPermissionState, result: GeofenceRegistrationResult?): String = when (permission) {
    LocationPermissionState.NONE -> "❌ Sem permissão — seguindo a rotina provável"
    LocationPermissionState.LOCATION_DISABLED -> "⚠️ Geofences pausados\nLocalização do aparelho desligada"
    LocationPermissionState.APPROXIMATE_ONLY -> "⚠️ Geofences pausados\nAtive a localização precisa"
    LocationPermissionState.FOREGROUND -> "⚠️ Geofences pausados\nLocalização em segundo plano desativada"
    LocationPermissionState.BACKGROUND -> when {
        result == null -> "✅ Localização em segundo plano ativa"
        result.ok && result.requested == 0 -> "✅ Pronto — cadastre lugares para monitorar"
        result.ok && result.skipped > 0 -> "✅ ${result.registered} locais monitorados · ${result.skipped} fora do limite"
        result.ok -> "✅ ${result.registered} ${if (result.registered == 1) "local monitorado" else "locais monitorados"}"
        else -> "⚠️ Geofences pausados\n" + when (result.error) {
            GeofenceRegistrationError.GEOFENCE_NOT_AVAILABLE -> "Serviço de geofence indisponível no aparelho"
            GeofenceRegistrationError.GEOFENCE_TOO_MANY_GEOFENCES -> "Limite de locais do sistema atingido"
            GeofenceRegistrationError.GEOFENCE_TOO_MANY_PENDING_INTENTS -> "Limite de registros do sistema atingido"
            GeofenceRegistrationError.PERMISSION_DENIED -> "Permissão de localização negada"
            GeofenceRegistrationError.LOCATION_DISABLED -> "Localização do aparelho desligada"
            GeofenceRegistrationError.PLAY_SERVICES_ERROR -> "Google Play Services indisponível"
            else -> "Erro desconhecido — tente Re-registrar"
        }
    }
}

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val permission by vm.permission.collectAsStateWithLifecycle()
    val geofenceResult by vm.geofenceResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    var editingName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDigitalDelete by remember { mutableStateOf(false) }
    val usagePermission by vm.usagePermission.collectAsStateWithLifecycle()
    var versionTaps by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("AJUSTES", style = MaterialTheme.typography.headlineSmall)

        PixelPanel(Modifier.fillMaxWidth(), onClick = { editingName = true }) {
            SectionLabel("Nome do gato")
            Text(s.catName, style = MaterialTheme.typography.titleMedium)
        }
        PixelButton("Rotina e horários", { onOpen(Routes.ROUTINE) }, Modifier.fillMaxWidth())
        PixelButton("Perfil do ${s.catName}", { onOpen(Routes.PROFILE) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
        PixelButton("Memórias", { onOpen(Routes.MEMORIES) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)

        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel("Como o ${s.catName} se desloca")
            ChipRow(CommuteStyle.entries.map { it.label }, s.commuteStyle.ordinal, { vm.setCommute(CommuteStyle.entries[it]) })
        }

        PixelPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Notificações")
                    Text("Chegadas e confirmações (no máximo poucas por dia)", color = HoodieColors.Muted)
                }
                Switch(s.notificationsEnabled, vm::setNotifications)
            }
        }

        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel("Localização")
            Text(locationSummary(permission, geofenceResult))
            info?.let { Text(it, color = HoodieColors.Muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PixelButton("Permissões", {
                    context.startActivity(
                        if (permission == LocationPermissionState.LOCATION_DISABLED) vm.locationSettingsIntent() else vm.appSettingsIntent(),
                    )
                }, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                PixelButton("Re-registrar", vm::reregister, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
        }

        DigitalSettingsPanel(
            s.digital, usagePermission,
            onChange = vm::setDigital,
            onAccess = { runCatching { context.startActivity(vm.usageAccessIntent()) } },
            onClear = { confirmDigitalDelete = true },
        )

        PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
            SectionLabel("Privacidade")
            Text(
                "• Tudo fica neste aparelho: sem conta, sem servidor, sem nuvem.\n" +
                    "• Internet só ao buscar um endereço no mapa: o texto vai ao serviço de mapas do Android e o mapa vem do OpenStreetMap.\n" +
                    "• Guardamos lugares (cifrados), horários, contextos, histórico e o estado do gato.\n" +
                    "• Não guardamos trajeto GPS nem posição contínua.\n" +
                    "• Diário digital (opcional): só app + tempo de uso por dia. Nunca mensagens, texto, fotos ou conteúdo da tela.\n" +
                    "• Backup em nuvem desativado.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PixelButton("Apagar todos os dados", { confirmDelete = true }, Modifier.fillMaxWidth(), color = HoodieColors.Coral)

        Text(
            "Hoodie ${BuildConfig.VERSION_NAME}",
            color = HoodieColors.Muted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { versionTaps++ }.padding(8.dp),
        )
        // Ferramenta interna: sempre visível em debug, escondida (5 toques) em release.
        if (BuildConfig.DEBUG || versionTaps >= 5) {
            PixelButton("🧪 Developer Lab", { onOpen(Routes.DEV_LAB) }, Modifier.fillMaxWidth(), color = HoodieColors.Mint)
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(s.catName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Nome do gato") },
            text = { OutlinedTextField(name, { name = it.take(16) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.setName(name); editingName = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDigitalDelete) {
        AlertDialog(
            onDismissRequest = { confirmDigitalDelete = false },
            title = { Text("Apagar histórico digital?") },
            text = { Text("Tempo de tela, apps e uso por contexto salvos serão apagados deste aparelho. O resto do Diário continua.") },
            confirmButton = { TextButton(onClick = { vm.clearDigitalHistory(); confirmDigitalDelete = false }) { Text("Apagar", color = HoodieColors.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDigitalDelete = false }) { Text("Cancelar") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar tudo?") },
            text = { Text("Lugares, histórico, memórias e o estado do ${s.catName} serão apagados deste aparelho. Não dá para desfazer.") },
            confirmButton = { TextButton(onClick = { vm.deleteEverything(); confirmDelete = false }) { Text("Apagar", color = HoodieColors.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Ajustes do Diário Digital: o que é lido, o que aparece e o que é guardado. */
@Composable
private fun DigitalSettingsPanel(
    d: DigitalSettings,
    permission: UsagePermissionState,
    onChange: (DigitalSettings) -> Unit,
    onAccess: () -> Unit,
    onClear: () -> Unit,
) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("📱 Diário digital")
        Text(
            when (permission) {
                UsagePermissionState.GRANTED -> "✅ Acesso ao uso liberado"
                UsagePermissionState.DENIED -> "⚠️ Acesso ao uso desligado"
                UsagePermissionState.UNAVAILABLE -> "❌ Aparelho sem acesso ao uso"
            },
        )
        DigitalSwitch("Ativar análise do celular", "Lê só qual app ficou na tela e por quanto tempo", d.analysisEnabled) { onChange(d.copy(analysisEnabled = it)) }
        DigitalSwitch("Mostrar dados digitais no Diário", "Card \"Seu celular\" e apps na linha do tempo", d.showInDiary) { onChange(d.copy(showInDiary = it)) }
        DigitalSwitch("Salvar histórico digital", "Guarda o resumo de cada dia neste aparelho", d.saveHistory) { onChange(d.copy(saveHistory = it)) }
        DigitalSwitch("Mostrar top apps por contexto", "Casa, trabalho, transporte...", d.showTopAppsByContext) { onChange(d.copy(showTopAppsByContext = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PixelButton("Acesso ao uso", onAccess, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            PixelButton("Apagar histórico", onClear, Modifier.weight(1f), color = HoodieColors.Coral)
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
