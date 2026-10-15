package com.hoodie.app.presentation.screens.home

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.sizeIn
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.common.appErrorText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.core.time.SystemClockProvider
import com.hoodie.app.engine.routine.RoutineEngine
import com.hoodie.app.pixel.icons.IconLabel
import com.hoodie.app.pixel.icons.PixelIconView
import com.hoodie.app.pixel.icons.PixelIcons
import com.hoodie.app.pixel.icons.PixelSprite
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.ErrorState
import com.hoodie.app.presentation.components.HomeLoadingSkeleton
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.RenderFrame

/**
 * A tela que importa: CENÁRIO + GATO + AÇÃO. Todo o resto é secundário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val dayState by vm.dayState.collectAsStateWithLifecycle(initialValue = null)
    val homeNow by vm.homeNow.collectAsStateWithLifecycle()
    val savedPlaces by vm.savedPlaces.collectAsStateWithLifecycle()
    val zone = vm.zone
    val snackbar = remember { SnackbarHostState() }
    val reactions = remember { MutableSharedFlow<AnimationId>(extraBufferCapacity = 4) }
    val interactionRequests = remember { MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1) }
    val interactiveSpeech = rememberInteractiveSpeech()
    val soundsOn by vm.interactionSoundsEnabled.collectAsStateWithLifecycle()
    val interactionSounds = com.hoodie.app.presentation.components.rememberHoodieInteractionSounds(soundsOn)
    LaunchedEffect(state.visual, state.vehicleMoving, state.loading, state.error) {
        interactiveSpeech.clear()
        interactionSounds?.stop()
        vm.onInteractionCancelled()
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vm, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onHomeStarted()
                Lifecycle.Event.ON_STOP -> { interactiveSpeech.clear(); interactionSounds?.stop(); vm.onHomeStopped() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            interactiveSpeech.clear()
            vm.onHomeStopped()
        }
    }
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                when (event) {
                    is HomeUiEvent.InteractionResponse -> if (vm.isInteractionCurrent(event.request)) interactionRequests.emit(event.request)
                    is HomeUiEvent.React -> reactions.emit(event.animation)
                    is HomeUiEvent.ShowError -> launch { snackbar.showSnackbar(context.appErrorText(event.error)) }
                    is HomeUiEvent.ShowMessage -> launch { snackbar.showSnackbar(event.message) }
                    is HomeUiEvent.OfferUndo -> launch {
                        // ~15 s para desfazer; ao expirar a Snackbar some e a correção segue valendo.
                        val result = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                            snackbar.showSnackbar(event.message, actionLabel = context.getString(R.string.home_undo), duration = androidx.compose.material3.SnackbarDuration.Indefinite)
                        }
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoCorrection(event.operationId)
                    }
                }
            }
        }
    }

    HomeContent(
        state = state.copy(dialogue = interactiveSpeech.resolve(state.dialogue)),
        busy = busy,
        zone = zone,
        onOpen = onOpen,
        actions = HomeActions(
            retryLoad = vm::retryLoad,
            savePlaceHere = { vm.savePlaceHere(it) },
            setManual = { vm.setManual(it) },
            answerYesNo = { id, yes -> vm.answerYesNo(id, yes) },
            answerNewPlace = { id, type -> vm.answerNewPlace(id, type) },
            answerTransportMode = { id, mode -> vm.answerTransportMode(id, mode) },
            dismissQuestion = { vm.dismissQuestion(it) },
            answerSavePlace = { id, save -> vm.answerSavePlace(id, save) },
            saveDiscoveredPlace = { id, name, reuseId, position -> vm.saveDiscoveredPlace(id, name, reuseId, position) },
            updateNewPlacePosition = vm::updateNewPlacePosition,
            correctContext = { type, historical, expectedEventId -> vm.correctCurrentContext(type, historical, expectedEventId) },
            confirmContext = vm::confirmContext,
            quickCorrect = vm::quickCorrect,
        ),
        savedPlaces = savedPlaces,
        setupCard = { if (com.hoodie.app.core.config.HoodieConfig.PROGRESSIVE_ONBOARDING) com.hoodie.app.presentation.screens.onboarding.SetupProgressHost(onOpen) },
        reactions = reactions,
        snackbar = snackbar,
        dayState = dayState,
        homeNow = homeNow,
        onScenePresented = vm::onScenePresented,
        interactionsEnabled = vm.touchInteractionsEnabled,
        interactionRequests = interactionRequests,
        onHoodieGesture = vm::onHoodieGesture,
        interactionAllowed = vm::interactionAllowed,
        onInteractionStarted = { request ->
            // A fala e o som só valem quando a reação começou de fato na tela.
            if (vm.onInteractionStarted(request)) {
                interactiveSpeech.present(request)
                interactionSounds?.play(request)
            }
        },
        onInteractionFinished = vm::onInteractionFinished,
    )
}

internal data class HomeActions(
    val retryLoad: () -> Unit = {},
    val savePlaceHere: (PlaceType) -> Unit = {},
    val setManual: (PlaceType) -> Unit = {},
    val answerYesNo: (Long, Boolean) -> Unit = { _, _ -> },
    val answerNewPlace: (Long, PlaceType) -> Unit = { _, _ -> },
    val answerTransportMode: (Long, com.hoodie.app.core.mobility.MovementMode) -> Unit = { _, _ -> },
    val dismissQuestion: (Long) -> Unit = {},
    val answerSavePlace: (Long, Boolean) -> Unit = { _, _ -> },
    val saveDiscoveredPlace: (Long, String, Long?, Pair<Double, Double>?) -> Unit = { _, _, _, _ -> },
    val updateNewPlacePosition: (Long, Pair<Double, Double>?) -> Unit = { _, _ -> },
    val correctContext: suspend (PlaceType, Boolean, Long?) -> Boolean = { _, _, _ -> false },
    val confirmContext: (Long) -> Unit = {},
    val quickCorrect: suspend (com.hoodie.app.domain.correction.QuickContextCorrection) -> Boolean = { false },
)

/** Production UI shared by the connected screen and deterministic visual fixtures. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    busy: Boolean,
    zone: ZoneId,
    onOpen: (String) -> Unit,
    actions: HomeActions = HomeActions(),
    reactions: kotlinx.coroutines.flow.Flow<AnimationId>? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    dayState: com.hoodie.app.domain.daystate.DayStateSnapshot? = null,
    homeNow: com.hoodie.app.domain.home.HomeNowSnapshot? = null,
    onScenePresented: (com.hoodie.app.pixel.scene.SceneId) -> Unit = {},
    savedPlaces: List<com.hoodie.app.core.model.Place> = emptyList(),
    setupCard: @Composable () -> Unit = {},
    interactionsEnabled: Boolean = com.hoodie.app.core.config.HoodieConfig.HOODIE_TOUCH_INTERACTIONS_V1,
    interactionRequests: kotlinx.coroutines.flow.Flow<InteractionRequest>? = null,
    onHoodieGesture: (InteractionGesture, RenderFrame) -> Unit = { _, _ -> },
    interactionAllowed: (InteractionRequest, RenderFrame) -> Boolean = { _, _ -> true },
    onInteractionStarted: (InteractionRequest) -> Unit = {},
    onInteractionFinished: (InteractionRequest) -> Unit = {},
) {
    val uiTextContext = LocalContext.current
    var manualOpen by remember { mutableStateOf(false) }
    var correctionOpen by remember { mutableStateOf(false) }
    var correctionExpectedEventId by remember { mutableStateOf<Long?>(null) }
    var correctionSnapshot by remember { mutableStateOf<com.hoodie.app.domain.home.HomeNowSnapshot?>(null) }
    val openCorrection: () -> Unit = {
        correctionSnapshot = homeNow ?: state.context?.let { current ->
            com.hoodie.app.engine.home.HomeNowAssembler.assemble(
                dayState, current, state.contextPlaceName, null, null, state.now, state.locationStatus,
            )
        }
        correctionExpectedEventId = correctionSnapshot?.contextEventId ?: state.context?.id
        correctionOpen = true
    }
    val ux2 = com.hoodie.app.core.config.HoodieConfig.HOME_UX_V2
    val primary = if (ux2) HomePresentationMapper.primaryAction(state) else null
    if (state.loading) {
        HomeLoadingSkeleton()
        return
    }
    if (state.error != null) {
        ErrorState(uiTextContext.appErrorText(requireNotNull(state.error)), actions.retryLoad, fillScreen = true)
        return
    }
    Box(Modifier.fillMaxSize()) {

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()).testTag("home_scroll"),
    ) {
        // Cabeçalho: hora + período + atalhos.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Fonte ampliada: hora e período empilham, senão os 4 botões de 48 dp empurram o último para fora da tela.
            val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
            if (largeFont) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.now > 0) formatClock(state.now, zone) else "--:--", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    IconLabel(PixelIcons.of(state.period), state.period.label, color = periodColor(state.period), style = MaterialTheme.typography.labelLarge, iconSize = 20.dp)
                }
            } else {
                Text(if (state.now > 0) formatClock(state.now, zone) else "--:--", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                IconLabel(PixelIcons.of(state.period), state.period.label, color = periodColor(state.period), style = MaterialTheme.typography.labelLarge, iconSize = 20.dp)
                Spacer(Modifier.weight(1f))
            }
            if (com.hoodie.app.core.config.HoodieConfig.HOME_NOW_V2) {
                HomeMoreMenu(onAdjust = openCorrection)
            }
            HeaderIcon(PixelIcons.SPARKLE, HoodieColors.Gold, stringResource(R.string.home_memories)) { onOpen(Routes.MEMORIES) }
            HeaderIcon(PixelIcons.CAT, HoodieColors.Hood, stringResource(R.string.home_profile)) { onOpen(Routes.PROFILE) }
        }

        // Cena viva.
        dayState?.let { snapshot ->
            val label = when (snapshot.state) {
                com.hoodie.app.domain.daystate.DayState.SLEEPING -> stringResource(R.string.day_state_sleeping)
                com.hoodie.app.domain.daystate.DayState.WAKING -> stringResource(R.string.day_state_waking)
                com.hoodie.app.domain.daystate.DayState.ACTIVE -> stringResource(R.string.day_state_active)
                com.hoodie.app.domain.daystate.DayState.COMMUTING -> stringResource(R.string.day_state_commuting)
                com.hoodie.app.domain.daystate.DayState.WINDING_DOWN -> stringResource(R.string.day_state_winding_down)
            }
            Text(if (snapshot.provisional) stringResource(R.string.day_state_provisional, label) else label,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("home_day_state"))
        }
        // A altura é a da própria cena (escala inteira pela largura): sem caixa 3:4 sobrando abaixo dela,
        // o texto "Hoodie está…" fica logo embaixo do cenário.
        if (ux2) HomeHeroContent(
            state, homeNow, zone, busy, reactions, onScenePresented,
            onConfirm = actions.confirmContext, onCorrect = openCorrection,
            interactionsEnabled = interactionsEnabled, interactionRequests = interactionRequests,
            onHoodieGesture = onHoodieGesture, interactionAllowed = interactionAllowed,
            onInteractionStarted = onInteractionStarted, onInteractionFinished = onInteractionFinished,
        ) else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // O balão fica acima do cenário (em fluxo): sobreposto, cobria o topo da cena.
            state.dialogue?.let { SpeechBubble(it, Modifier.padding(top = 10.dp, bottom = 6.dp)) }
            HoodieSceneView(state.visual, Modifier.fillMaxWidth().testTag("home_live_scene"), reactions = reactions, onScenePresented = onScenePresented,
                interactionsEnabled = interactionsEnabled, interactionRequests = interactionRequests,
                onHoodieGesture = onHoodieGesture, interactionAllowed = interactionAllowed,
                onInteractionStarted = onInteractionStarted, onInteractionFinished = onInteractionFinished)
        }

        if (com.hoodie.app.core.config.HoodieConfig.HOME_NOW_V2) {
            // Bloco único "Hoodie · Agora": a atividade do Hoodie em destaque e o contexto do usuário embaixo.
            Column(Modifier.padding(horizontal = 16.dp)) {
                HomeNowCard(
                    homeNow, state.now, zone,
                    activity = state.snapshot?.state?.activity, activityStartedAt = state.snapshot?.state?.startedAt, catName = state.catName,
                )
                if (ux2) HomeNowQuickActions(
                    HomePresentationMapper.quickActions(homeNow), onConfirm = actions.confirmContext, onCorrect = openCorrection,
                    enabled = !busy, modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else state.snapshot?.let { snap ->
            val a = snap.state.activity
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("home_hoodie_activity"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.home_activity, state.catName, a.label), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                val since = state.now - snap.state.startedAt
                Text(
                    stringResource(R.string.home_activity_since, formatClock(snap.state.startedAt, zone), formatDuration(since)),
                    color = HoodieColors.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            state.question?.takeUnless { state.vehicleMoving && it.kind in setOf(QuestionKind.NEW_PLACE, QuestionKind.SAVE_PLACE) }
                ?.takeIf { !ux2 || primary is HomePrimaryAction.PendingQuestion }
                ?.let { q -> QuestionCard(q, actions, state.newPlaceSaveOptions, busy) }

            if (if (ux2) primary is HomePrimaryAction.MonitoringWarning else (state.probableMode || state.locationStatus != LocationStatus.OK)) {
                PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                    Text(
                        when (state.locationStatus) {
                            LocationStatus.NO_PERMISSION, LocationStatus.DISABLED -> uiTextContext.getString(R.string.ui_extra_home_screen_1)
                            LocationStatus.NO_BACKGROUND -> uiTextContext.getString(R.string.ui_extra_home_screen_2)
                            LocationStatus.OK -> uiTextContext.getString(R.string.ui_extra_home_screen_3)
                        },
                    )
                    Text(stringResource(R.string.home_probable_routine, state.catName), color = HoodieColors.Muted)
                }
            }

            state.suggestSavePlace?.takeIf { !ux2 || primary is HomePrimaryAction.SuggestPlace }?.let { type ->
                PixelPanel(Modifier.fillMaxWidth()) {
                    val title = when {
                        state.canSaveHere && type == PlaceType.WORK -> uiTextContext.getString(R.string.ui_extra_home_screen_4)
                        state.canSaveHere -> uiTextContext.getString(R.string.ui_extra_home_screen_5)
                        type == PlaceType.WORK -> uiTextContext.getString(R.string.ui_extra_home_screen_6)
                        else -> uiTextContext.getString(R.string.ui_extra_home_screen_7)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_save_place_reason, state.catName), color = HoodieColors.Muted)
                    Spacer(Modifier.padding(4.dp))
                    if (state.canSaveHere) {
                        PixelButton(if (busy) stringResource(R.string.home_locating) else stringResource(R.string.home_save_place_as, type.label), { actions.savePlaceHere(type) }, Modifier.fillMaxWidth(), enabled = !busy)
                        Spacer(Modifier.padding(4.dp))
                    }
                    PixelButton(stringResource(R.string.ui_home_screen_1), { onOpen(Routes.placePicker(type)) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                }
            }

            // Configuração gradual: só quando não há nenhuma outra pendência e não está em veículo.
            if (ux2 && primary == null && !state.vehicleMoving) setupCard()

            // Você.
            if (!com.hoodie.app.core.config.HoodieConfig.HOME_NOW_V2) PixelPanel(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.ui_home_screen_2))
                val ctx = state.context
                val type = ctx?.type ?: UserContextType.UNKNOWN
                IconLabel(PixelIcons.of(type), type.label + if (state.probableMode && ctx != null) stringResource(R.string.home_probable_suffix) else "", style = MaterialTheme.typography.titleMedium, iconSize = 20.dp)
                ctx?.let { Text(stringResource(R.string.home_context_since, formatClock(it.startedAt, zone)), color = HoodieColors.Muted) }
                state.next?.let {
                    Spacer(Modifier.padding(4.dp))
                    SectionLabel(stringResource(R.string.ui_home_screen_3))
                    Text(it.display)
                    state.routineOrigin?.let { origin -> Text(origin, style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted) }
                }
            }

            if (!com.hoodie.app.core.config.HoodieConfig.HOME_NOW_V2) {
                PixelButton(stringResource(R.string.ui_home_screen_4), { manualOpen = true }, Modifier.fillMaxWidth(), color = HoodieColors.Gold)
            }
        }
    }

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (manualOpen) {
        ModalBottomSheet(onDismissRequest = { manualOpen = false }, containerColor = HoodieColors.Panel) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ui_home_screen_5), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_routine_learning, state.catName), color = HoodieColors.Muted)
                ManualPlaceOptions(onSelect = { type -> actions.setManual(type); manualOpen = false })
                Spacer(Modifier.padding(12.dp))
            }
        }
    }
    if (correctionOpen) HomeNowCorrectionSheet(
        snapshot = correctionSnapshot,
        onDismiss = { correctionOpen = false },
        onSave = { type, historical, placeId ->
            val expected = correctionExpectedEventId
            if (com.hoodie.app.core.config.HoodieConfig.QUICK_CORRECTION_V2 && expected != null) actions.quickCorrect(
                com.hoodie.app.domain.correction.QuickContextCorrection(
                    expected, type, placeId,
                    if (historical) com.hoodie.app.domain.correction.CorrectionScope.SINCE_CURRENT_ARRIVAL else com.hoodie.app.domain.correction.CorrectionScope.FROM_NOW,
                ),
            ) else actions.correctContext(type, historical, expected)
        },
        saving = busy,
        savedPlaces = savedPlaces,
    )
}

@Composable
private fun HomeMoreMenu(onAdjust: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.home_more_menu)
    Box {
        Box(
            Modifier
                .padding(start = 8.dp)
                .background(HoodieColors.Panel)
                .border(2.dp, HoodieColors.Outline)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = { expanded = true })
                .semantics { contentDescription = moreLabel }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text("⋯", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_adjust_activity)) },
                onClick = { expanded = false; onAdjust() },
                modifier = Modifier.testTag("home_adjust_activity"),
            )
        }
    }
}

/** Opções do seletor "O que estou fazendo?", compartilhando o catálogo de Novo Lugar. */
@Composable
internal fun ManualPlaceOptions(onSelect: (PlaceType) -> Unit) {
    PlaceType.physicalPlaceOptions.forEach { type ->
        PixelButton(
            type.label, { onSelect(type) }, Modifier.fillMaxWidth(),
            color = HoodieColors.PanelLight, textColor = HoodieColors.Ink, leadingIcon = PixelIcons.of(type),
        )
    }
}

@Composable
private fun HeaderIcon(icon: PixelSprite, tint: androidx.compose.ui.graphics.Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .background(HoodieColors.Panel)
            .border(2.dp, HoodieColors.Outline)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { PixelIconView(icon, size = 24.dp, tint = tint) }
}

private fun periodColor(p: com.hoodie.app.core.time.DayPeriod) = when (p) {
    com.hoodie.app.core.time.DayPeriod.MORNING, com.hoodie.app.core.time.DayPeriod.DAY -> HoodieColors.Gold
    com.hoodie.app.core.time.DayPeriod.EVENING -> HoodieColors.Coral
    com.hoodie.app.core.time.DayPeriod.NIGHT -> HoodieColors.Hood
}

@Composable
private fun QuestionCard(q: ContextQuestion, actions: HomeActions,
    saveOptions: com.hoodie.app.engine.context.NewPlaceSaveOptions?, busy: Boolean) {
    var saving by remember(q.id) { mutableStateOf(q.kind == QuestionKind.SAVE_PLACE) }
    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
        Text(q.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(6.dp))
        when (q.kind) {
            QuestionKind.CONFIRM_CONTEXT, QuestionKind.CONFIRM_MOVEMENT, QuestionKind.CONFIRM_ARRIVAL, QuestionKind.CONFIRM_TRIP_PATTERN -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(stringResource(R.string.ui_home_screen_6), { actions.answerYesNo(q.id, true) }, Modifier.weight(1f))
                PixelButton(stringResource(R.string.ui_home_screen_7), { actions.answerYesNo(q.id, false) }, Modifier.weight(1f), color = HoodieColors.Panel, textColor = HoodieColors.Ink)
            }
            QuestionKind.NEW_PLACE -> {
                val options = PlaceType.physicalPlaceOptions
                ChipRow(options.map { it.label }, null, { actions.answerNewPlace(q.id, options[it]) }, icons = options.map { PixelIcons.of(it) })
                Spacer(Modifier.padding(4.dp))
                PixelButton(stringResource(R.string.ui_home_screen_8), { actions.dismissQuestion(q.id) }, modifier = Modifier.fillMaxWidth(), color = HoodieColors.PanelLight)
            }
            QuestionKind.SELECT_TRANSPORT_MODE -> {
                // Escolha só dentro do app (nunca um botão de notificação com o veículo andando).
                val options = com.hoodie.app.core.mobility.MovementMode.TRANSPORT_CHOICES
                ChipRow(options.map { "${it.emoji} ${it.label}" }, null, { actions.answerTransportMode(q.id, options[it]) })
                Spacer(Modifier.padding(4.dp))
                PixelButton(stringResource(R.string.ui_home_screen_9), { actions.dismissQuestion(q.id) }, modifier = Modifier.fillMaxWidth(), color = HoodieColors.PanelLight)
            }
            QuestionKind.SAVE_PLACE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton("Guardar lugar", { saving = true }, Modifier.weight(1f), enabled = !busy)
                PixelButton("Somente hoje", { actions.answerSavePlace(q.id, false) }, Modifier.weight(1f), color = HoodieColors.Panel, textColor = HoodieColors.Ink, enabled = !busy)
            }
        }
    }
    if (saving && q.kind == QuestionKind.SAVE_PLACE) {
        NewPlaceSaveSheet(q.id, q.chosenPlaceType ?: PlaceType.OTHER, saveOptions, busy,
            onSave = { name, reuseId, manualPosition -> actions.saveDiscoveredPlace(q.id, name, reuseId, manualPosition) },
            onToday = { actions.answerSavePlace(q.id, false) },
            onPositionChange = { actions.updateNewPlacePosition(q.id, it) },
            onDismiss = { saving = false })
    }
}
