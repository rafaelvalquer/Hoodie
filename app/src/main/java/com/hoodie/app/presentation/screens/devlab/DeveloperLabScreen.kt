package com.hoodie.app.presentation.screens.devlab

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.HoodieStateDao
import com.hoodie.app.core.database.HoodieStateEntity
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.SystemClockProvider
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.screens.diary.DiaryLabScreen
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevLabSnapshot(
    val now: Long = 0,
    val offsetMs: Long = 0,
    val current: ContextEventEntity? = null,
    val recent: List<ContextEventEntity> = emptyList(),
    val hoodie: HoodieStateEntity? = null,
    val places: List<Place> = emptyList(),
    val tables: List<Pair<String, Int>> = emptyList(),
)

/** Ferramenta interna (debug): estado das engines, geofences, simulador e banco. */
@HiltViewModel
class DeveloperLabViewModel @Inject constructor(
    private val contextDao: ContextEventDao,
    private val stateDao: HoodieStateDao,
    private val activityDao: HoodieActivityDao,
    private val timelineDao: TimelineDao,
    private val places: PlaceRepository,
    private val permissions: LocationPermissionManager,
    private val geofences: GeofenceRegistrar,
    private val contextEngine: ContextEngine,
    private val hoodie: HoodieEngine,
    private val clock: ClockProvider,
    logger: DebugEventLogger,
) : ViewModel() {
    private val _snapshot = MutableStateFlow(DevLabSnapshot())
    val snapshot: StateFlow<DevLabSnapshot> = _snapshot.asStateFlow()
    val permission: StateFlow<LocationPermissionState> = permissions.state
    val geofenceResult: StateFlow<GeofenceRegistrationResult?> = geofences.lastResult
    val log: StateFlow<List<DebugEventLogger.Entry>> = logger.entries

    fun refresh() = viewModelScope.launch {
        permissions.refresh()
        val all = contextDao.all()
        _snapshot.value = DevLabSnapshot(
            now = clock.nowMillis(),
            offsetMs = (clock as? SystemClockProvider)?.debugOffsetMs ?: 0,
            current = contextDao.current(),
            recent = all.takeLast(RECENT),
            hoodie = stateDao.get(),
            places = places.all(),
            tables = listOf(
                "context_events" to contextDao.count(),
                "hoodie_activities" to activityDao.count(),
                "timeline_events" to timelineDao.count(),
                "places" to places.all().size,
            ),
        )
    }

    fun reregister() = viewModelScope.launch { geofences.registerAll(); refresh() }

    /** Avança o relógio do app (só em debug) e reconcilia como o worker faria. */
    fun advance(ms: Long) = viewModelScope.launch {
        val system = clock as? SystemClockProvider ?: return@launch
        system.debugOffsetMs += ms
        contextEngine.applyRoutineFallbackIfNeeded()
        hoodie.resolve()
        refresh()
    }

    fun resetClock() = viewModelScope.launch {
        (clock as? SystemClockProvider)?.debugOffsetMs = 0
        hoodie.resolve()
        refresh()
    }

    private companion object {
        const val RECENT = 12
    }
}

private val tabs = listOf("PIXEL", "CONTEXT", "GEOFENCE", "SIMULATOR", "DATABASE", "DIARY", "LOG")

@Composable
fun DeveloperLabScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: DeveloperLabViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(1) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val permission by vm.permission.collectAsStateWithLifecycle()
    val geofence by vm.geofenceResult.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    LaunchedEffect(tab) { vm.refresh() }
    val zone = java.time.ZoneId.systemDefault()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DEVELOPER LAB", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
        ChipRow(tabs, tab, { tab = it })
        when (tabs[tab]) {
            "PIXEL" -> PixelLabTab(onOpen)
            "CONTEXT" -> ContextTab(snap, zone)
            "GEOFENCE" -> GeofenceTab(snap, permission, geofence, vm::reregister)
            "SIMULATOR" -> SimulatorTab(snap, zone, vm::advance, vm::resetClock)
            "DATABASE" -> DatabaseTab(snap)
            "DIARY" -> DiaryLabScreen(Modifier.fillMaxWidth())
            "LOG" -> LogTab(log, zone)
        }
    }
}

@Composable
private fun PixelLabTab(onOpen: (String) -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Cenas, animações e expressões")
        PixelButton("Abrir Pixel Lab", { onOpen(Routes.PIXEL_LAB) }, Modifier.fillMaxWidth().padding(top = 8.dp), color = HoodieColors.Mint)
    }
}

@Composable
private fun ContextTab(snap: DevLabSnapshot, zone: java.time.ZoneId) {
    val c = snap.current
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Atual")
        if (c == null) {
            Text("— sem contexto —")
        } else {
            Mono("Current:    ${c.type}")
            Mono("Source:     ${c.source}")
            Mono("Confidence: ${(c.confidence * 100).toInt()}%")
            Mono("startedAt:  ${formatClock(c.startedAt, zone)}  (há ${formatDuration(snap.now - c.startedAt)})")
            Mono("placeId:    ${c.placeId ?: "—"}")
        }
    }
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Últimos eventos")
        snap.recent.asReversed().forEach { e ->
            val end = e.endedAt?.let { formatClock(it, zone) } ?: "…"
            Mono("#${e.id} ${formatClock(e.startedAt, zone)}–$end ${e.type} ${e.source}")
        }
    }
}

@Composable
private fun GeofenceTab(snap: DevLabSnapshot, permission: LocationPermissionState, result: GeofenceRegistrationResult?, onRegister: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Permissão")
        Mono(permission.name)
        SectionLabel("Último registro", Modifier.padding(top = 8.dp))
        if (result == null) {
            Mono("—")
        } else {
            Mono("requested=${result.requested} registered=${result.registered} skipped=${result.skipped}")
            Mono("error=${result.error ?: "none"}")
        }
        PixelButton("Re-registrar", onRegister, Modifier.fillMaxWidth().padding(top = 8.dp), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
    }
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Lugares")
        snap.places.forEach { p ->
            val active = result?.activePlaceIds?.contains(p.id) == true
            Mono("${p.type.name.padEnd(10)} ${if (active) "ACTIVE  " else "INACTIVE"} ${p.name}")
        }
    }
}

@Composable
private fun SimulatorTab(snap: DevLabSnapshot, zone: java.time.ZoneId, onAdvance: (Long) -> Unit, onReset: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Relógio do app")
        Mono("now:    ${formatClock(snap.now, zone)}  (${java.time.Instant.ofEpochMilli(snap.now).atZone(zone).toLocalDate()})")
        Mono("offset: +${formatDuration(snap.offsetMs)}")
        snap.hoodie?.let { h ->
            Mono("Hoodie: ${h.activity} desde ${formatClock(h.startedAt, zone)} · ctx ${h.userContext}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            PixelButton("+15 min", { onAdvance(15 * MINUTE_MS) }, Modifier.weight(1f))
            PixelButton("+1 h", { onAdvance(HOUR_MS) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            PixelButton("+6 h", { onAdvance(6 * HOUR_MS) }, Modifier.weight(1f))
            PixelButton("+1 dia", { onAdvance(DAY_MS) }, Modifier.weight(1f))
        }
        PixelButton("Voltar ao relógio real", onReset, Modifier.fillMaxWidth().padding(top = 6.dp), color = HoodieColors.Coral)
    }
}

@Composable
private fun DatabaseTab(snap: DevLabSnapshot) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Tabelas")
        snap.tables.forEach { (name, count) -> Mono("${name.padEnd(18)} $count") }
    }
}

@Composable
private fun LogTab(entries: List<DebugEventLogger.Entry>, zone: java.time.ZoneId) {
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Eventos (mais recentes primeiro)")
        if (entries.isEmpty()) Text("— vazio (só registra em build debug) —", color = HoodieColors.Muted)
        entries.asReversed().take(LOG_LINES).forEach { e -> Mono("${formatClock(e.at, zone)} ${e.category} ${e.message}") }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

private const val LOG_LINES = 120
