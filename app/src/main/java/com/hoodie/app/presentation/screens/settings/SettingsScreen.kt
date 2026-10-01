package com.hoodie.app.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.BuildConfig
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.geofence.GeofenceManager
import com.hoodie.app.core.location.LocationProvider
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val location: LocationProvider,
    private val geofences: GeofenceManager,
    private val db: HoodieDatabase,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    val state = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val info = MutableStateFlow<String?>(null)

    fun locationStatus(): LocationStatus = location.status()
    fun setName(n: String) = viewModelScope.launch { settings.setCatName(n) }
    fun setCommute(s: CommuteStyle) = viewModelScope.launch { settings.setCommuteStyle(s) }
    fun setNotifications(on: Boolean) = viewModelScope.launch { settings.setNotifications(on) }
    fun reregister() = viewModelScope.launch {
        info.value = if (geofences.registerAll()) "Geofences registrados." else "Não foi possível registrar (verifique a permissão de localização)."
    }

    /** Apaga tudo: banco, preferências, geofences e tarefas. Volta ao onboarding. */
    fun deleteEverything() = viewModelScope.launch {
        geofences.clear()
        scheduler.cancelAll()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clear()
    }
}

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
            Text(
                when (vm.locationStatus()) {
                    LocationStatus.OK -> "✅ Ativa (geofences em segundo plano)"
                    LocationStatus.NO_BACKGROUND -> "⚠️ Só com o app aberto"
                    LocationStatus.NO_PERMISSION -> "❌ Sem permissão — seguindo a rotina provável"
                    LocationStatus.DISABLED -> "❌ Localização do aparelho desligada"
                },
            )
            info?.let { Text(it, color = HoodieColors.Muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PixelButton("Permissões", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                PixelButton("Re-registrar", vm::reregister, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
        }

        PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
            SectionLabel("Privacidade")
            Text(
                "• Tudo fica neste aparelho: sem conta, sem servidor, sem nuvem — o app nem tem permissão de internet.\n" +
                    "• Guardamos lugares (cifrados), horários, contextos, histórico e o estado do gato.\n" +
                    "• Não guardamos trajeto GPS nem posição contínua.\n" +
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
            PixelButton("🧪 Pixel Lab", { onOpen(Routes.PIXEL_LAB) }, Modifier.fillMaxWidth(), color = HoodieColors.Mint)
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
