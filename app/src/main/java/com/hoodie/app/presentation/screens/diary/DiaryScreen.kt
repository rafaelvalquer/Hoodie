package com.hoodie.app.presentation.screens.diary

import androidx.compose.ui.semantics.stateDescription
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import com.hoodie.app.presentation.common.CollectUiEvents
import com.hoodie.app.presentation.common.appErrorText
import androidx.compose.runtime.rememberUpdatedState
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.domain.diary.model.DiaryMapNode
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.screens.phoneinsights.ContextPhoneUsageSection
import com.hoodie.app.presentation.screens.phoneinsights.DiaryPhoneCard
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsScreen
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.presentation.theme.HoodieSpacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiaryScreen(vm: DiaryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedNodeId by remember(state.selectedDate) { mutableStateOf<String?>(null) }
    val selectedDate by rememberUpdatedState(state.selectedDate)
    CollectUiEvents(vm.events) { event ->
        when (event) {
            is DiaryUiEvent.ShowPlaceDetails -> if (event.date == selectedDate) selectedNodeId = event.nodeId
        }
    }
    val zone = vm.zone
    DiaryContent(
        state = state,
        zone = zone,
        nowMillis = vm.nowMillis,
        selectedNodeId = selectedNodeId,
        onSelectedNodeChange = { selectedNodeId = it },
        actions = DiaryActions(
            selectDate = vm::selectDate,
            retry = vm::retry,
            openPlace = vm::openPlace,
            pause = vm::pause,
            play = vm::play,
            reset = vm::reset,
            setSpeed = vm::setSpeed,
            seek = vm::seekTo,
            setMapMode = vm::setMapMode,
            openChapter = vm::openChapter,
            selectClockSegment = vm::selectClockSegment,
            refreshClock = vm::refreshClock,
        ),
    )
}

internal data class DiaryActions(
    val selectDate: (LocalDate) -> Unit = {},
    val retry: () -> Unit = {},
    val openPlace: (String) -> Unit = {},
    val pause: () -> Unit = {},
    val play: () -> Unit = {},
    val reset: () -> Unit = {},
    val setSpeed: (ReplaySpeed) -> Unit = {},
    val seek: (Long) -> Unit = {},
    val setMapMode: (com.hoodie.app.domain.diary.journey.DiaryMapMode) -> Unit = {},
    val openChapter: (com.hoodie.app.domain.diary.journey.DayChapter?) -> Unit = {},
    val selectClockSegment: (String?) -> Unit = {},
    val refreshClock: () -> Unit = {},
)

@Composable
internal fun DiaryContent(
    state: DiaryUiState,
    zone: ZoneId,
    nowMillis: Long,
    selectedNodeId: String? = null,
    onSelectedNodeChange: (String?) -> Unit = {},
    actions: DiaryActions = DiaryActions(),
    digitalContent: @Composable (LocalDate) -> Unit = { PhoneInsightsScreen(it) },
) {
    val today = state.today
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(DiaryTab.GENERAL) }
    var selectedContext by remember { mutableStateOf<UserContextType?>(null) }
    var selectedJourneyNodeId by remember(state.selectedDate) { mutableStateOf<String?>(null) }
    var quickStops by remember(state.selectedDate) { mutableStateOf<List<com.hoodie.app.domain.diary.model.JourneyNode>>(emptyList()) }
    // Jornada do dia: montada uma vez por dia carregado (layout e cache não mudam a cada quadro).
    val journey = remember(state.diary) { state.diary?.let { JourneyMapModel.from(it, nowMillis) } }
    // Jornada 3.0: plano + serpentina; a parada do replay e a selecionada nunca somem num "×k".
    val protectedKey = journey?.let { protectedStops(null, it.data, state.replay, selectedJourneyNodeId) }
    val overworld = remember(journey, protectedKey) {
        journey?.let { com.hoodie.app.engine.diary.journey.JourneyOverworldModel.build(it.data, zone, protectedKey.orEmpty()) }
    }
    val clock = remember(journey) {
        if (com.hoodie.app.core.config.HoodieConfig.DIARY_DAY_CLOCK_V2) null
        else journey?.let { com.hoodie.app.engine.diary.journey.DayClockLegacyAssembler.build(it.data, zone) }
    }
    // Relógio do Dia 2.0: vem do ViewModel; fixtures sem ViewModel montam aqui.
    val dayClock = state.dayClock ?: remember(state.diary) {
        state.diary?.takeIf { com.hoodie.app.core.config.HoodieConfig.DIARY_DAY_CLOCK_V2 }
            ?.let { com.hoodie.app.engine.diary.DayClockAssembler.build(it, state.selectedDate, zone, nowMillis) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("diary_scroll").padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.ui_diary_screen_1), style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        DateSelector(state.selectedDate, today, context, zone, onSelect = actions.selectDate)
        DiaryTabs(tab, onSelect = { tab = it })
        if (tab == DiaryTab.DIGITAL) {
            digitalContent(state.selectedDate)
        } else if (state.isLoading) {
            PixelPanel(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text(stringResource(R.string.ui_diary_screen_2), color = HoodieColors.Muted) } }
        } else if (state.error != null) {
            PixelPanel(Modifier.fillMaxWidth()) {
                Text(context.appErrorText(requireNotNull(state.error)), color = HoodieColors.Coral)
                PixelButton(stringResource(R.string.ui_diary_screen_3), actions.retry)
            }
        } else {
            val diary = state.diary
            if (diary == null || diary.summary.totalMs == 0L && diary.timeline.isEmpty()) {
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_diary_screen_4), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                    Text(stringResource(R.string.ui_diary_screen_5), color = HoodieColors.Muted, modifier = Modifier.padding(top = 6.dp))
                    Text(stringResource(R.string.ui_diary_screen_6), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                SummarySection(diary.summary, onContext = if (diary.phoneInsights != null) { ctx -> selectedContext = ctx } else null)
                if (diary.mobilityTotals.isNotEmpty()) {
                    // Deslocamentos do dia, sem trajeto: só quanto tempo em cada meio.
                    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                        SectionLabel(stringResource(R.string.ui_diary_screen_7))
                        Text(com.hoodie.app.engine.diary.DiaryMobilityMerger.summary(diary.mobilityTotals), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                diary.phoneInsights?.let { DiaryPhoneCard(it, onOpen = { tab = DiaryTab.DIGITAL }) }
                val toggleReplay = { if (state.replay.state == ReplayState.PLAYING) actions.pause() else actions.play() }
                if (journey != null) {
                    JourneySleepPrelude(diary.activityWindow, zone)
                    if (com.hoodie.app.core.config.HoodieConfig.DIARY_JOURNEY_MAP_V3 && overworld != null) {
                        // [JORNADA] [RELÓGIO]: mesmo replay, mesmo detalhe de parada.
                        DiaryMapModeSelector(state.mapMode, onSelect = actions.setMapMode)
                        val onStop: (com.hoodie.app.domain.diary.journey.JourneyStop) -> Unit = { stop ->
                            val cluster = overworld.clusterNodes(stop.id)
                            if (cluster.isNotEmpty()) quickStops = cluster else overworld.nodeOf(stop.id)?.let { selectedJourneyNodeId = it.id }
                        }
                        when (state.mapMode) {
                            com.hoodie.app.domain.diary.journey.DiaryMapMode.JOURNEY -> if (overworld.isChapters) {
                                JourneyChaptersView(
                                    overworld, state.replay, zone, state.manualChapter, state.selectedDate == today, nowMillis,
                                    selectedJourneyNodeId, onOpenChapter = actions.openChapter, onStop = onStop,
                                )
                            } else overworld.single?.let { single ->
                                PixelPanel(Modifier.fillMaxWidth().testTag("journey_map")) {
                                    SectionLabel(stringResource(R.string.journey_title))
                                    JourneyOverworldMapView(overworld, single, state.replay, zone, selectedJourneyNodeId, onStop, modifier = Modifier.padding(top = 8.dp))
                                    Text(stringResource(R.string.journey_v3_footer), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            com.hoodie.app.domain.diary.journey.DiaryMapMode.CLOCK -> if (dayClock != null) {
                                com.hoodie.app.presentation.screens.diary.clock.DayClockPanel(
                                    com.hoodie.app.presentation.screens.diary.clock.DayClockUiState.of(dayClock, state.clockSelectedId, state.replay),
                                    onSelect = actions.selectClockSegment,
                                    onMinuteTick = actions.refreshClock,
                                )
                            } else clock?.let { data ->
                                DayClockLegacyView(
                                    data, journey.data, state.replay, zone, overworld.seed, selectedJourneyNodeId,
                                    onStop = { id -> selectedJourneyNodeId = id },
                                    onTick = { tick -> quickStops = tick.stopIds.mapNotNull { journey.data.node(it) } },
                                )
                            }
                        }
                    } else {
                        // Jornada 2.0 (zigue-zague) enquanto DIARY_JOURNEY_MAP_V3 = false.
                        JourneyMapView(journey, state.replay, zone, selectedJourneyNodeId, onNode = { selectedJourneyNodeId = it.id })
                    }
                    DiaryReplayHud(state.replay.visual, zone)
                    JourneyReplayControls(state.replay, journey.data, zone, onToggle = toggleReplay, onSeek = actions.seek, onReset = actions.reset, onSpeed = actions.setSpeed)
                }
                TimelineSection(diary.timeline, state.replay.currentTimestamp, zone, state.replay.highlightedTimelineItemIds)
            }
        }
    }
    state.diary?.let { diary ->
        val layout = remember(diary.visits) { DiaryMapLayoutEngine.layout(diary.visits) }
        layout.node(selectedNodeId)?.let { node ->
            val details = remember(diary, nowMillis) { com.hoodie.app.engine.diary.ReplayHudAssembler.visitDetails(diary, nowMillis) }
            PlaceDetailBottomSheet(node, details, zone) { onSelectedNodeChange(null) }
        }
    }
    selectedContext?.let { ctx -> ContextPhoneSheet(ctx, state.diary?.phoneInsights) { selectedContext = null } }
    if (quickStops.isNotEmpty()) {
        QuickStopsSheet(quickStops, zone, onNode = { quickStops = emptyList(); selectedJourneyNodeId = it.id }, onDismiss = { quickStops = emptyList() })
    }
    // Detalhe de uma parada da jornada; "ver detalhes do lugar" abre o detalhe completo (todas as visitas).
    val journeyNode = journey?.data?.node(selectedJourneyNodeId)
    val diaryForJourney = state.diary
    if (journeyNode != null && diaryForJourney != null) {
        val details = remember(diaryForJourney, nowMillis) { com.hoodie.app.engine.diary.ReplayHudAssembler.visitDetails(diaryForJourney, nowMillis) }
        JourneyNodeDetailsSheet(
            node = journeyNode,
            arrivedBy = journey.data.segments.firstOrNull { it.index == journeyNode.visitIndex - 1 },
            details = details.firstOrNull { it.index == journeyNode.visitIndex },
            zone = zone,
            onSeeAll = {
                selectedJourneyNodeId = null
                DiaryMapLayoutEngine.layout(diaryForJourney.visits).nodes
                    .firstOrNull { journeyNode.visitIndex in it.visitIndices }
                    ?.let { onSelectedNodeChange(it.id) }
            },
            onDismiss = { selectedJourneyNodeId = null },
        )
    }
}

@Composable
internal fun DateSelector(date: LocalDate, today: LocalDate, context: android.content.Context, zone: ZoneId, onSelect: (LocalDate) -> Unit) {
    val uiTextContext = LocalContext.current
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateChip(uiTextContext.getString(R.string.ui_extra_diary_screen_1), date == today) { onSelect(today) }
        DateChip(uiTextContext.getString(R.string.ui_extra_diary_screen_2), date == today.minusDays(1)) { onSelect(today.minusDays(1)) }
        DateChip(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-BR"))).uppercase(), date != today && date != today.minusDays(1)) {
            val initial = date
            DatePickerDialog(
                context,
                { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth,
            ).apply {
                datePicker.maxDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            }.show()
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).background(if (selected) HoodieColors.Blue else HoodieColors.PanelLight).border(2.dp, HoodieColors.Outline).semantics { this.selected = selected; contentDescription = label }.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HoodieColors.Outline else HoodieColors.Ink)
    }
}

private data class SummaryItem(val label: String, val emoji: String, val duration: Long, val context: UserContextType? = null)

@Composable
internal fun SummarySection(summary: DailySummary, onContext: ((UserContextType) -> Unit)? = null) {
    val uiTextContext = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.ui_diary_screen_8))
        val items = listOf(
            SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_3), "🏠", summary.homeMs, UserContextType.HOME), SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_4), "🏢", summary.workMs, UserContextType.WORK),
            SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_5), "🚶", summary.commutingMs, UserContextType.COMMUTING), SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_6), uiTextContext.getString(R.string.ui_extra_diary_screen_7), summary.lunchMs, UserContextType.LUNCH),
        SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_8), uiTextContext.getString(R.string.ui_extra_diary_screen_9), summary.gymMs, UserContextType.GYM), SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_10), "🎉", summary.leisureMs, UserContextType.LEISURE), SummaryItem(uiTextContext.getString(R.string.ui_extra_diary_screen_11), "📍", summary.otherMs),
        ).filter { it.duration > 0 }
        if (items.isEmpty()) PixelPanel(Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_diary_screen_9), color = HoodieColors.Muted) }
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val click = if (onContext != null && item.context != null) { { onContext(item.context) } } else null
                    PixelPanel(Modifier.weight(1f), color = HoodieColors.PanelLight, onClick = click) {
                        Text(item.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(HoodieSpacing.LabelToValue))
                        Text(item.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HoodieColors.MutedStrong)
                        Spacer(Modifier.height(HoodieSpacing.LabelToValue))
                        Text(formatDuration(item.duration), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ReplayControls(replay: ReplayUiState, onToggle: () -> Unit, onReset: () -> Unit, onSpeed: (ReplaySpeed) -> Unit) {
    val uiTextContext = LocalContext.current
    val replayDescription = uiTextContext.getString(when (replay.state) {
        ReplayState.IDLE -> R.string.replay_idle
        ReplayState.PLAYING -> R.string.replay_playing
        ReplayState.PAUSED -> R.string.replay_paused
        ReplayState.FINISHED -> R.string.replay_finished
    })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (replay.state == ReplayState.PLAYING) uiTextContext.getString(R.string.ui_extra_diary_screen_12) else if (replay.state == ReplayState.FINISHED) uiTextContext.getString(R.string.ui_extra_diary_screen_13) else uiTextContext.getString(R.string.ui_extra_diary_screen_14), onToggle, modifier = Modifier.weight(1f).semantics { stateDescription = replayDescription }, color = HoodieColors.Mint)
        TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = uiTextContext.getString(R.string.ui_extra_diary_screen_15) }) { Text(stringResource(R.string.ui_diary_screen_10), color = HoodieColors.Hood) }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ui_diary_screen_11), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        ReplaySpeed.entries.forEach { speed ->
            androidx.compose.material3.FilterChip(modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton; contentDescription = speed.label }, selected = replay.speed == speed, onClick = { onSpeed(speed) }, label = { Text(speed.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
internal fun TimelineSection(items: List<com.hoodie.app.domain.diary.model.DiaryTimelineItem>, replayAt: Long?, zone: ZoneId, highlightedIds: Set<String> = emptySet()) {
    val uiTextContext = LocalContext.current
    val activeIndex = if (replayAt == null) -1 else items.indexOfFirst { it.id in highlightedIds }
    val timelineScroll = rememberLazyListState()
    LaunchedEffect(activeIndex, items) {
        if (activeIndex >= 0) timelineScroll.animateScrollToItem(activeIndex)
        else timelineScroll.scrollToItem(0)
    }
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.ui_diary_screen_12))
        if (items.isEmpty()) Text(stringResource(R.string.ui_diary_screen_13), color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), state = timelineScroll) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val highlighted = index == activeIndex
            val actorColor = when (item.actor) {
                com.hoodie.app.domain.diary.model.DiaryActor.USER -> HoodieColors.Blue
                com.hoodie.app.domain.diary.model.DiaryActor.HOODIE -> HoodieColors.Mint
                else -> HoodieColors.Muted
            }
            val actorLabel = when (item.actor) {
                com.hoodie.app.domain.diary.model.DiaryActor.USER -> uiTextContext.getString(R.string.ui_extra_diary_screen_16)
                com.hoodie.app.domain.diary.model.DiaryActor.HOODIE -> "Hoodie"
                else -> uiTextContext.getString(R.string.ui_extra_diary_screen_17)
            }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                selected = highlighted
                contentDescription = "$actorLabel, ${formatClock(item.timestamp, zone)}, ${item.title}" +
                    (item.subtitle?.let { ", $it" } ?: "")
            }.padding(vertical = 6.dp).background(if (highlighted) HoodieColors.PanelLight else Color.Transparent).padding(4.dp), verticalAlignment = Alignment.Top) {
                Text(formatClock(item.timestamp, zone), style = MaterialTheme.typography.labelSmall, color = if (highlighted) HoodieColors.Gold else HoodieColors.Muted, maxLines = 1, softWrap = false)
                Text(if (item.actor.name == "HOODIE") "🐱" else if (item.actor.name == "SYSTEM") uiTextContext.getString(R.string.ui_extra_diary_screen_18) else (item.emoji ?: "📍"), color = actorColor, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = if (highlighted) HoodieColors.Hood else HoodieColors.Ink)
                    item.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted) }
                }
            }
            }
        }
    }
}

